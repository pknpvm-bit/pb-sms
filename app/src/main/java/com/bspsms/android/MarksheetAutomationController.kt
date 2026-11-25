package com.bspsms.android

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class BulkDownloaderMode { AUTO, MANUAL }

enum class AutomationJobStatus { IDLE, RUNNING, PAUSED, COMPLETED, STOPPED, ERROR }

data class HrpcStudentEntry(
    val rowIndex: Int,
    val rollNo: String,
    val name: String,
    val hasView: Boolean,
    val hasPrint: Boolean,
    val viewHref: String = "",
    val printHref: String = ""
) {
    val uniqueKey: String get() = "${rollNo.trim().lowercase()}|${name.trim().lowercase()}"
}

data class BulkDownloadHistoryItem(
    val rollNo: String,
    val studentName: String,
    val pageNumber: Int,
    val fileName: String,
    val pagesSaved: List<Int>,
    val timestamp: String
)

data class BulkFailedItem(
    val rollNo: String,
    val studentName: String,
    val pageNumber: Int,
    val rowIndex: Int,
    val reason: String
)

data class BulkMarksheetState(
    val mode: BulkDownloaderMode = BulkDownloaderMode.AUTO,
    val jobStatus: AutomationJobStatus = AutomationJobStatus.IDLE,
    val selectedPages: Set<Int> = emptySet(), // empty = all 5 pages
    val safFolderLabel: String = "",
    val hasSafFolder: Boolean = false,
    val scannedStudents: List<HrpcStudentEntry> = emptyList(),
    val totalStudents: Int = 0,
    val completedDownloads: Int = 0,
    val pendingDownloads: Int = 0,
    val failedDownloadsCount: Int = 0,
    val currentStudentName: String = "",
    val currentRollNo: String = "",
    val currentPageNumber: Int = 1,
    val totalPages: Int = 1,
    val statusMessage: String = "Open Student Information table in Portal after Search, then tap Scan Table.",
    val isError: Boolean = false,
    val downloadHistory: List<BulkDownloadHistoryItem> = emptyList(),
    val failedList: List<BulkFailedItem> = emptyList(),
    val completedKeys: Set<String> = emptySet(),
    val singleBusyKey: String? = null
)

data class ResultFailedItem(
    val rollNo: String,
    val studentName: String,
    val pageNumber: Int,
    val rowIndex: Int,
    val reason: String
)

data class ResultReadyState(
    val jobStatus: AutomationJobStatus = AutomationJobStatus.IDLE,
    val scannedStudents: List<HrpcStudentEntry> = emptyList(),
    val subjectSchema: List<DynamicSubjectDef> = emptyList(),
    val termHeaders: ResultTermHeaders = ResultTermHeaders(),
    val extractedRows: List<StudentSummativeResultRow> = emptyList(),
    val extractedKeys: Set<String> = emptySet(),
    val totalStudents: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val currentStudentName: String = "",
    val currentRollNo: String = "",
    val currentPageNumber: Int = 1,
    val totalPages: Int = 1,
    val safFolderLabel: String = "",
    val hasSafFolder: Boolean = false,
    val lastExportedFileName: String = "",
    val statusMessage: String = "Open Student Information table in Portal after Search, then tap Start Extraction.",
    val isError: Boolean = false,
    val failedList: List<ResultFailedItem> = emptyList()
)

class MarksheetAutomationController(private val appContext: Context) {
    companion object {
        private const val SLOW_NET_LOAD_TIMEOUT_MS = 45_000L
        private const val SLOW_NET_IMAGE_SETTLE_MAX_MS = 5_000L
        private const val SLOW_NET_INTERACTIVE_GRACE_MS = 12_000L
        private const val SLOW_NET_POLL_INTERVAL_MS = 300L
        private const val SLOW_NET_PAGINATION_TIMEOUT_MS = 30_000L
        private const val SLOW_NET_PAGE_SEEK_TIMEOUT_MS = 25_000L
        private const val SLOW_NET_RETRY_BACKOFF_MS = 1_800L
        private const val INTER_STUDENT_PACING_DELAY_MS = 1_600L
        private const val MAX_STUDENT_ATTEMPT_ROUNDS = 3 // Initial attempt + up to 2 retries
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var portalWebViewProvider: () -> WebView? = { null }
    private var backgroundWebViewProvider: () -> WebView? = { null }
    private var backgroundWebViewCreator: (() -> WebView?)? = null
    private var backgroundWebViewDestroyer: (WebView?) -> Unit = {}
    private var activePopupWebViewProvider: () -> WebView? = { null }
    private var closePopupWebViewAction: () -> Unit = {}

    private val _bulkState = MutableStateFlow(BulkMarksheetState())
    val bulkState: StateFlow<BulkMarksheetState> = _bulkState.asStateFlow()

    private val _resultState = MutableStateFlow(ResultReadyState())
    val resultState: StateFlow<ResultReadyState> = _resultState.asStateFlow()

    private var bulkJob: Job? = null
    private var resultJob: Job? = null

    @Volatile
    private var pauseBulkRequested = false
    @Volatile
    private var stopBulkRequested = false
    @Volatile
    private var pauseResultRequested = false
    @Volatile
    private var stopResultRequested = false

    @Volatile
    var cookieResolver: (String) -> String? = { url ->
        runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
    }

    @Volatile
    var cookieFlusher: () -> Unit = {
        runCatching { android.webkit.CookieManager.getInstance().flush() }
    }

    // Latest saved HRPC filter snapshot so Back Navigation can restore Academic Year, Medium, Class, Section, Page
    private val lastHrpcSnapshotRef = AtomicReference<String?>(null)

    @Volatile
    var pendingHrpcRestoreFromBack: Boolean = false

    // Captures direct PDF download triggered when Portal Print endpoint serves application/pdf via DownloadListener
    private val capturedPdfDownloadUrl = AtomicReference<String?>(null)
    private val capturedPdfUserAgent = AtomicReference<String?>(null)

    private val failedDbGetUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var lastCleanHrpcUrl: String? = null

    @Volatile
    private var lastHrpcMenuHref: String? = null

    @Volatile
    var lastWebViewCacheMissAtMs: Long = 0L

    @Volatile
    var historyGoBackProducesCacheMiss: Boolean = false

    @Volatile
    var lastMainFrameErrorDesc: String? = null

    @Volatile
    var lastMainFrameErrorAtMs: Long = 0L

    @Volatile
    var lastBgCacheMissAtMs: Long = 0L

    @Volatile
    var lastBgMainFrameErrorDesc: String? = null

    @Volatile
    var lastBgMainFrameErrorAtMs: Long = 0L

    init {
        refreshSafFolderState()
    }

    fun attachPortalWebViewProvider(provider: () -> WebView?) {
        portalWebViewProvider = provider
    }

    fun attachBackgroundWebViewProvider(provider: () -> WebView?) {
        backgroundWebViewProvider = provider
    }

    fun attachBackgroundWebViewLifecycle(
        creator: () -> WebView?,
        destroyer: (WebView?) -> Unit
    ) {
        backgroundWebViewCreator = creator
        backgroundWebViewDestroyer = destroyer
    }

    fun attachPopupWebViewHandlers(
        popupProvider: () -> WebView?,
        closePopupAction: () -> Unit
    ) {
        activePopupWebViewProvider = popupProvider
        closePopupWebViewAction = closePopupAction
    }

    fun getBackgroundWebView(): WebView? = backgroundWebViewProvider()

    fun refreshSafFolderState() {
        val label = SafStorageHelper.getSavedFolderLabel(appContext)
        val hasUri = SafStorageHelper.getSavedFolderUri(appContext) != null
        _bulkState.update { it.copy(safFolderLabel = label, hasSafFolder = hasUri) }
        _resultState.update { it.copy(safFolderLabel = label, hasSafFolder = hasUri) }
    }

    fun onSafFolderSelected(treeUri: Uri) {
        val label = SafStorageHelper.persistFolderUri(appContext, treeUri)
        _bulkState.update {
            it.copy(
                safFolderLabel = label,
                hasSafFolder = true,
                statusMessage = "Download folder selected: $label",
                isError = false
            )
        }
        _resultState.update {
            it.copy(
                safFolderLabel = label,
                hasSafFolder = true
            )
        }
    }

    fun updateLastHrpcSnapshot(snapshotJson: String) {
        if (snapshotJson.isNotBlank() && snapshotJson != "{}") {
            lastHrpcSnapshotRef.set(snapshotJson)
            runCatching {
                val obj = JSONObject(snapshotJson)
                val u = obj.optString("url", "").trim()
                val menuHref = obj.optString("hrpcMenuHref", "").trim()
                if (menuHref.startsWith("http", ignoreCase = true) && !failedDbGetUrls.contains(menuHref)) {
                    lastHrpcMenuHref = menuHref
                }
                if (u.startsWith("http", ignoreCase = true) &&
                    !failedDbGetUrls.contains(u) &&
                    !u.contains("print", ignoreCase = true)
                ) {
                    lastCleanHrpcUrl = u
                }
            }
        }
    }

    fun getLastHrpcSnapshot(): String? = lastHrpcSnapshotRef.get()

    @Volatile
    var isMainTabLockedForStudentFetch: Boolean = false

    fun isAnyAutomationActive(): Boolean =
        _bulkState.value.jobStatus == AutomationJobStatus.RUNNING ||
            _bulkState.value.singleBusyKey != null ||
            _resultState.value.jobStatus == AutomationJobStatus.RUNNING

    fun isMainTabNavigationLocked(): Boolean = isMainTabLockedForStudentFetch

    fun onCapturedBackgroundTarget(targetJson: String) {
        val portal = portalWebViewProvider() ?: return
        val bg = backgroundWebViewProvider() ?: return
        val targetObj = runCatching { JSONObject(targetJson) }.getOrNull() ?: return
        val portalUrl = portal.url.orEmpty()
        scope.launch(Dispatchers.Main) {
            if (backgroundWebViewProvider() === bg) {
                dispatchTargetToBackground(portal, bg, portalUrl, targetObj)
            }
        }
    }

    fun markUrlAsDbError(url: String?) {
        val cleaned = url?.trim().orEmpty()
        if (cleaned.startsWith("http", ignoreCase = true)) {
            failedDbGetUrls.add(cleaned)
        }
        lastWebViewCacheMissAtMs = System.currentTimeMillis()
        historyGoBackProducesCacheMiss = true
    }

    fun clearDbErrorUrls() {
        failedDbGetUrls.clear()
    }

    fun notifyWebViewCacheMiss() {
        val now = System.currentTimeMillis()
        lastWebViewCacheMissAtMs = now
        historyGoBackProducesCacheMiss = true
    }

    fun notifyWebViewMainFrameError(desc: String?) {
        lastMainFrameErrorDesc = desc?.takeIf { it.isNotBlank() } ?: "Portal page failed to load"
        lastMainFrameErrorAtMs = System.currentTimeMillis()
    }

    fun clearWebViewTransientError() {
        lastMainFrameErrorDesc = null
    }

    fun notifyBackgroundCacheMiss() {
        lastBgCacheMissAtMs = System.currentTimeMillis()
    }

    fun notifyBackgroundMainFrameError(desc: String?) {
        lastBgMainFrameErrorDesc = desc?.takeIf { it.isNotBlank() } ?: "Marksheet page failed to load"
        lastBgMainFrameErrorAtMs = System.currentTimeMillis()
    }

    fun clearBackgroundTransientError() {
        lastBgMainFrameErrorDesc = null
        lastBgMainFrameErrorAtMs = 0L
        lastBgCacheMissAtMs = 0L
    }

    fun isAutomatedPrintSuppressionActive(): Boolean {
        val bStatus = _bulkState.value.jobStatus
        return bStatus == AutomationJobStatus.RUNNING || _bulkState.value.singleBusyKey != null
    }

    fun onPortalDownloadTriggered(url: String, mimeType: String?, userAgent: String?) {
        val cleanedUrl = url.trim()
        val looksPdf = mimeType?.contains("pdf", ignoreCase = true) == true ||
            cleanedUrl.contains(".pdf", ignoreCase = true) ||
            cleanedUrl.contains("print", ignoreCase = true) ||
            cleanedUrl.startsWith("blob:", ignoreCase = true) ||
            cleanedUrl.startsWith("data:application/pdf", ignoreCase = true)
        if (looksPdf && isAutomatedPrintSuppressionActive()) {
            capturedPdfDownloadUrl.set(cleanedUrl)
            capturedPdfUserAgent.set(userAgent)
        }
    }

    fun setBulkMode(mode: BulkDownloaderMode) {
        _bulkState.update { it.copy(mode = mode) }
    }

    fun toggleSelectedPage(page: Int) {
        if (page !in 1..5) return
        _bulkState.update { st ->
            val next = if (st.selectedPages.contains(page)) {
                st.selectedPages - page
            } else {
                st.selectedPages + page
            }
            st.copy(selectedPages = next)
        }
    }

    fun clearSelectedPages() {
        _bulkState.update { it.copy(selectedPages = emptySet()) }
    }

    fun selectAllPages() {
        _bulkState.update { it.copy(selectedPages = setOf(1, 2, 3, 4, 5)) }
    }

    fun pauseBulk() {
        pauseBulkRequested = true
        _bulkState.update {
            it.copy(
                jobStatus = AutomationJobStatus.PAUSED,
                statusMessage = "Paused cleanly after current step. Tap Resume to continue."
            )
        }
    }

    fun resumeBulk() {
        pauseBulkRequested = false
        if (_bulkState.value.jobStatus == AutomationJobStatus.PAUSED) {
            _bulkState.update {
                it.copy(
                    jobStatus = AutomationJobStatus.RUNNING,
                    statusMessage = "Resuming bulk marksheet download..."
                )
            }
        }
    }

    fun stopBulk() {
        stopBulkRequested = true
        pauseBulkRequested = false
        _bulkState.update {
            it.copy(
                jobStatus = AutomationJobStatus.STOPPED,
                statusMessage = "Bulk marksheet download stopped by user."
            )
        }
    }

    fun pauseResult() {
        pauseResultRequested = true
        _resultState.update {
            it.copy(
                jobStatus = AutomationJobStatus.PAUSED,
                statusMessage = "Paused extraction. Tap Resume to continue."
            )
        }
    }

    fun resumeResult() {
        pauseResultRequested = false
        if (_resultState.value.jobStatus == AutomationJobStatus.PAUSED) {
            _resultState.update {
                it.copy(
                    jobStatus = AutomationJobStatus.RUNNING,
                    statusMessage = "Resuming summative marks extraction..."
                )
            }
        }
    }

    fun stopResult() {
        stopResultRequested = true
        pauseResultRequested = false
        _resultState.update {
            it.copy(
                jobStatus = AutomationJobStatus.STOPPED,
                statusMessage = "Extraction stopped by user."
            )
        }
    }

    fun scanCurrentTableForBulk() {
        scope.launch {
            val webView = portalWebViewProvider()
            if (webView == null) {
                _bulkState.update { it.copy(statusMessage = "Portal WebView is not ready.", isError = true) }
                return@launch
            }
            val scan = scanTableInternal(webView)
            if (!scan.ok) {
                _bulkState.update {
                    it.copy(
                        scannedStudents = emptyList(),
                        statusMessage = scan.error,
                        isError = true
                    )
                }
                return@launch
            }
            saveHrpcSnapshotNow(webView)
            val completed = _bulkState.value.completedKeys
            val failedCount = _bulkState.value.failedList.size
            val totalEst = maxOf(scan.totalEntries, scan.students.size)
            _bulkState.update {
                it.copy(
                    scannedStudents = scan.students,
                    totalStudents = totalEst,
                    currentPageNumber = scan.currentPage,
                    totalPages = scan.totalPages,
                    pendingDownloads = (totalEst - completed.size - failedCount).coerceAtLeast(0),
                    statusMessage = "Found ${scan.students.size} students on Page ${scan.currentPage}/${scan.totalPages} (Total Entries: $totalEst).",
                    isError = false
                )
            }
        }
    }

    fun scanCurrentTableForResult() {
        scope.launch {
            val webView = portalWebViewProvider()
            if (webView == null) {
                _resultState.update { it.copy(statusMessage = "Portal WebView is not ready.", isError = true) }
                return@launch
            }
            val scan = scanTableInternal(webView)
            if (!scan.ok) {
                _resultState.update {
                    it.copy(
                        scannedStudents = emptyList(),
                        statusMessage = scan.error,
                        isError = true
                    )
                }
                return@launch
            }
            saveHrpcSnapshotNow(webView)
            val totalEst = maxOf(scan.totalEntries, scan.students.size)
            val done = _resultState.value.extractedKeys.size
            val failedCount = _resultState.value.failedList.size
            _resultState.update {
                it.copy(
                    scannedStudents = scan.students,
                    totalStudents = totalEst,
                    currentPageNumber = scan.currentPage,
                    totalPages = scan.totalPages,
                    pendingCount = (totalEst - done - failedCount).coerceAtLeast(0),
                    statusMessage = "Found ${scan.students.size} students on Page ${scan.currentPage}/${scan.totalPages} (Total Entries: $totalEst).",
                    isError = false
                )
            }
        }
    }

    fun startBulkAutoDownload(clearPreviousHistory: Boolean = true) {
        val treeUri = SafStorageHelper.getSavedFolderUri(appContext)
        if (treeUri == null) {
            refreshSafFolderState()
            _bulkState.update {
                it.copy(
                    statusMessage = "Please select a writable destination folder first (Tap 'Choose Download Folder').",
                    isError = true
                )
            }
            return
        }
        val webView = portalWebViewProvider()
        if (webView == null) {
            _bulkState.update { it.copy(statusMessage = "Portal WebView is not ready.", isError = true) }
            return
        }

        bulkJob?.cancel()
        pauseBulkRequested = false
        stopBulkRequested = false

        _bulkState.update { st ->
            if (clearPreviousHistory) {
                st.copy(
                    singleBusyKey = null,
                    completedDownloads = 0,
                    failedDownloadsCount = 0,
                    pendingDownloads = st.totalStudents,
                    downloadHistory = emptyList(),
                    failedList = emptyList(),
                    completedKeys = emptySet(),
                    currentStudentName = "",
                    currentRollNo = ""
                )
            } else {
                st.copy(singleBusyKey = null)
            }
        }

        bulkJob = scope.launch {
            runBulkAutoLoop(webView, treeUri)
        }
    }

    fun retryFailedBulkDownloads() {
        val treeUri = SafStorageHelper.getSavedFolderUri(appContext)
        if (treeUri == null) {
            _bulkState.update { it.copy(statusMessage = "Select a destination folder first.", isError = true) }
            return
        }
        val webView = portalWebViewProvider() ?: return
        val failed = _bulkState.value.failedList.toList()
        if (failed.isEmpty()) return

        bulkJob?.cancel()
        pauseBulkRequested = false
        stopBulkRequested = false

        bulkJob = scope.launch {
            _bulkState.update {
                it.copy(
                    singleBusyKey = null,
                    jobStatus = AutomationJobStatus.RUNNING,
                    isError = false,
                    statusMessage = "Retrying ${failed.size} failed download(s)..."
                )
            }
            val selectedPagesList = effectiveSelectedPages()
            for (item in failed) {
                if (!waitWhilePausedBulk() || stopBulkRequested) break

                // Navigate to the failed student's page if needed
                val moved = ensureOnTablePage(webView, item.pageNumber) { stopBulkRequested }
                if (!moved) {
                    if (!stopBulkRequested) {
                        updateFailedReason(item, "Could not return to Page ${item.pageNumber}")
                    }
                    continue
                }

                val student = HrpcStudentEntry(
                    rowIndex = item.rowIndex,
                    rollNo = item.rollNo,
                    name = item.studentName,
                    hasView = true,
                    hasPrint = true
                )
                val res = processSingleStudentPdf(webView, treeUri, student, item.pageNumber, selectedPagesList) { stopBulkRequested }
                if (res.isSuccess) {
                    val hist = res.getOrThrow()
                    _bulkState.update { st ->
                        val nextFailed = st.failedList.filterNot {
                            it.rollNo == item.rollNo && it.studentName == item.studentName && it.pageNumber == item.pageNumber
                        }
                        val nextCompletedKeys = st.completedKeys + student.uniqueKey
                        st.copy(
                            completedDownloads = nextCompletedKeys.size,
                            failedDownloadsCount = nextFailed.size,
                            pendingDownloads = (st.totalStudents - nextCompletedKeys.size - nextFailed.size).coerceAtLeast(0),
                            failedList = nextFailed,
                            completedKeys = nextCompletedKeys,
                            downloadHistory = listOf(hist) + st.downloadHistory,
                            statusMessage = "Retry succeeded: ${hist.fileName}"
                        )
                    }
                } else {
                    updateFailedReason(item, res.exceptionOrNull()?.message ?: "Retry failed")
                }
                abortAwareDelay(INTER_STUDENT_PACING_DELAY_MS) { stopBulkRequested }
            }

            _bulkState.update { st ->
                val finalStatus = when {
                    stopBulkRequested -> AutomationJobStatus.STOPPED
                    else -> AutomationJobStatus.COMPLETED
                }
                st.copy(
                    jobStatus = finalStatus,
                    pendingDownloads = (st.totalStudents - st.completedKeys.size - st.failedList.size).coerceAtLeast(0),
                    statusMessage = if (stopBulkRequested) {
                        "Retry stopped by user. Remaining failed: ${st.failedList.size}."
                    } else if (st.failedList.isEmpty()) {
                        "All failed downloads retried successfully!"
                    } else {
                        "Retry finished with ${st.failedList.size} remaining failure(s)."
                    }
                )
            }
        }
    }

    fun downloadSingleStudentManual(student: HrpcStudentEntry) {
        val treeUri = SafStorageHelper.getSavedFolderUri(appContext)
        if (treeUri == null) {
            refreshSafFolderState()
            _bulkState.update { it.copy(statusMessage = "Please choose a writable download folder first.", isError = true) }
            return
        }
        val webView = portalWebViewProvider() ?: return
        if (_bulkState.value.jobStatus == AutomationJobStatus.RUNNING || _bulkState.value.singleBusyKey != null) return

        scope.launch {
            val currentPage = _bulkState.value.currentPageNumber
            val selectedPagesList = effectiveSelectedPages()
            _bulkState.update {
                it.copy(
                    singleBusyKey = student.uniqueKey,
                    currentStudentName = student.name,
                    currentRollNo = student.rollNo,
                    statusMessage = "Downloading marksheet PDF for Roll ${student.rollNo} (${student.name})...",
                    isError = false
                )
            }
            val res = processSingleStudentPdf(webView, treeUri, student, currentPage, selectedPagesList) { false }
            if (res.isSuccess) {
                val hist = res.getOrThrow()
                _bulkState.update { st ->
                    val nextCompletedKeys = st.completedKeys + student.uniqueKey
                    val nextFailed = st.failedList.filterNot {
                        it.rollNo.equals(student.rollNo, ignoreCase = true) && it.studentName.equals(student.name, ignoreCase = true)
                    }
                    st.copy(
                        singleBusyKey = null,
                        completedDownloads = nextCompletedKeys.size,
                        failedList = nextFailed,
                        failedDownloadsCount = nextFailed.size,
                        pendingDownloads = (st.totalStudents - nextCompletedKeys.size - nextFailed.size).coerceAtLeast(0),
                        completedKeys = nextCompletedKeys,
                        downloadHistory = listOf(hist) + st.downloadHistory,
                        statusMessage = "Saved ${hist.fileName} cleanly to SAF folder.",
                        isError = false
                    )
                }
            } else {
                val reason = res.exceptionOrNull()?.message ?: "Failed to save PDF"
                _bulkState.update { st ->
                    val fail = BulkFailedItem(student.rollNo, student.name, currentPage, student.rowIndex, reason)
                    val nextFailed = (st.failedList + fail).distinctBy { "${it.rollNo}|${it.studentName}|${it.pageNumber}" }
                    st.copy(
                        singleBusyKey = null,
                        failedDownloadsCount = nextFailed.size,
                        pendingDownloads = (st.totalStudents - st.completedKeys.size - nextFailed.size).coerceAtLeast(0),
                        failedList = nextFailed,
                        statusMessage = "Failed for ${student.name}: $reason",
                        isError = true
                    )
                }
            }
        }
    }

    fun captureOpenMarksheetManually(rollNoInput: String, nameInput: String) {
        val treeUri = SafStorageHelper.getSavedFolderUri(appContext)
        if (treeUri == null) {
            _bulkState.update { it.copy(statusMessage = "Please choose a download folder first.", isError = true) }
            return
        }
        val webView = portalWebViewProvider() ?: return
        scope.launch {
            val roll = rollNoInput.trim().ifBlank { "000" }
            val name = nameInput.trim().ifBlank { "MARKSHEET" }
            val selectedPages = effectiveSelectedPages()
            val fileName = SafStorageHelper.sanitizeMarksheetFileName(roll, name, "pdf")
            _bulkState.update {
                it.copy(
                    singleBusyKey = "manual_open",
                    statusMessage = "Capturing currently open marksheet as $fileName...",
                    isError = false
                )
            }
            val activeView = activePopupWebViewProvider() ?: webView
            val saveRes = captureActiveMarksheetWebViewToSaf(
                activeView,
                treeUri,
                fileName,
                selectedPages
            )
            if (saveRes.isSuccess) {
                val hist = BulkDownloadHistoryItem(
                    rollNo = roll,
                    studentName = name,
                    pageNumber = _bulkState.value.currentPageNumber,
                    fileName = fileName,
                    pagesSaved = selectedPages,
                    timestamp = nowTimeStr()
                )
                _bulkState.update { st ->
                    st.copy(
                        singleBusyKey = null,
                        completedDownloads = st.completedDownloads + 1,
                        downloadHistory = listOf(hist) + st.downloadHistory,
                        statusMessage = "Saved $fileName successfully!",
                        isError = false
                    )
                }
            } else {
                _bulkState.update {
                    it.copy(
                        singleBusyKey = null,
                        statusMessage = saveRes.exceptionOrNull()?.message ?: "Failed capturing open marksheet",
                        isError = true
                    )
                }
            }
        }
    }

    fun startResultExtraction(clearPrevious: Boolean = true) {
        val webView = portalWebViewProvider()
        if (webView == null) {
            _resultState.update { it.copy(statusMessage = "Portal WebView is not ready.", isError = true) }
            return
        }
        resultJob?.cancel()
        pauseResultRequested = false
        stopResultRequested = false

        if (clearPrevious) {
            _resultState.update {
                it.copy(
                    subjectSchema = emptyList(),
                    termHeaders = ResultTermHeaders(),
                    extractedRows = emptyList(),
                    extractedKeys = emptySet(),
                    completedCount = 0,
                    failedCount = 0,
                    failedList = emptyList(),
                    lastExportedFileName = ""
                )
            }
        }

        resultJob = scope.launch {
            runResultExtractionLoop(webView)
        }
    }

    fun retryFailedResultExtractions() {
        val webView = portalWebViewProvider() ?: return
        val failed = _resultState.value.failedList.toList()
        if (failed.isEmpty()) return

        resultJob?.cancel()
        pauseResultRequested = false
        stopResultRequested = false

        resultJob = scope.launch {
            _resultState.update {
                it.copy(
                    jobStatus = AutomationJobStatus.RUNNING,
                    isError = false,
                    statusMessage = "Retrying ${failed.size} failed marksheet extraction(s)..."
                )
            }
            for (item in failed) {
                if (!waitWhilePausedResult() || stopResultRequested) break

                val moved = ensureOnTablePage(webView, item.pageNumber) { stopResultRequested }
                if (!moved) {
                    if (!stopResultRequested) {
                        updateResultFailedReason(item, "Could not return to Page ${item.pageNumber}")
                    }
                    continue
                }

                val entry = HrpcStudentEntry(item.rowIndex, item.rollNo, item.studentName, hasView = true, hasPrint = false)
                val res = extractSingleStudentSummative(webView, entry, item.pageNumber) { stopResultRequested }
                if (res.isSuccess) {
                    val row = res.getOrThrow()
                    _resultState.update { st ->
                        val mergedRows = mergeOrReplaceResultRow(st.extractedRows, row)
                        val updatedRows = ResultXlsxExporter.recalculatePositions(mergedRows)
                        val nextSchema = ResultXlsxExporter.resolveSubjectSchema(updatedRows)
                        val nextHeaders = ResultXlsxExporter.resolveTermHeaders(updatedRows, nextSchema)
                        val nextKeys = st.extractedKeys + entry.uniqueKey
                        val nextFailed = st.failedList.filterNot {
                            it.rollNo == item.rollNo && it.studentName == item.studentName && it.pageNumber == item.pageNumber
                        }
                        st.copy(
                            subjectSchema = nextSchema,
                            termHeaders = nextHeaders,
                            extractedRows = updatedRows,
                            extractedKeys = nextKeys,
                            completedCount = updatedRows.size,
                            pendingCount = (st.totalStudents - nextKeys.size - nextFailed.size).coerceAtLeast(0),
                            failedList = nextFailed,
                            failedCount = nextFailed.size,
                            statusMessage = "Extracted ${row.studentName} cleanly."
                        )
                    }
                } else {
                    updateResultFailedReason(item, res.exceptionOrNull()?.message ?: "Retry failed")
                }
                abortAwareDelay(INTER_STUDENT_PACING_DELAY_MS) { stopResultRequested }
            }
            _resultState.update { st ->
                val finalRows = ResultXlsxExporter.recalculatePositions(st.extractedRows)
                val finalSchema = ResultXlsxExporter.resolveSubjectSchema(finalRows)
                val finalHeaders = ResultXlsxExporter.resolveTermHeaders(finalRows, finalSchema)
                st.copy(
                    jobStatus = if (stopResultRequested) AutomationJobStatus.STOPPED else AutomationJobStatus.COMPLETED,
                    subjectSchema = finalSchema,
                    termHeaders = finalHeaders,
                    extractedRows = finalRows,
                    pendingCount = (st.totalStudents - st.extractedKeys.size - st.failedList.size).coerceAtLeast(0),
                    statusMessage = if (stopResultRequested) {
                        "Retry stopped by user. ${finalRows.size} student row(s) ready."
                    } else if (st.failedList.isEmpty()) {
                        "Retry complete! ${finalRows.size} student row(s) ready for Excel export."
                    } else {
                        "Completed with ${st.failedList.size} remaining failure(s)."
                    }
                )
            }
        }
    }

    fun exportResultWorkbookToSafFolder(fileNameInput: String = "") {
        val treeUri = SafStorageHelper.getSavedFolderUri(appContext)
        if (treeUri == null) {
            _resultState.update {
                it.copy(
                    statusMessage = "Select a SAF folder or use 'Save As .xlsx' to download.",
                    isError = true
                )
            }
            return
        }
        val rows = _resultState.value.extractedRows
        if (rows.isEmpty()) {
            _resultState.update { it.copy(statusMessage = "No student marks extracted yet.", isError = true) }
            return
        }
        scope.launch {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val finalName = fileNameInput.trim()
                .ifBlank { "Result_Ready_$timestamp.xlsx" }
                .let { if (it.endsWith(".xlsx", ignoreCase = true)) it else "$it.xlsx" }

            val writeRes = withContext(Dispatchers.IO) {
                SafStorageHelper.writeFileToFolder(
                    context = appContext,
                    treeUri = treeUri,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    displayName = finalName,
                    overwriteExisting = true
                ) { out ->
                    ResultXlsxExporter.writeWorkbook(rows, "Result Ready", out)
                }
            }
            if (writeRes.isSuccess) {
                _resultState.update {
                    it.copy(
                        lastExportedFileName = finalName,
                        statusMessage = "Excel saved: $finalName (${rows.size} students)",
                        isError = false
                    )
                }
            } else {
                _resultState.update {
                    it.copy(
                        statusMessage = writeRes.exceptionOrNull()?.message ?: "Failed saving Excel file",
                        isError = true
                    )
                }
            }
        }
    }

    fun exportResultWorkbookToExplicitUri(targetUri: Uri, fileNameLabel: String) {
        val rows = _resultState.value.extractedRows
        if (rows.isEmpty()) return
        scope.launch {
            val writeRes = withContext(Dispatchers.IO) {
                SafStorageHelper.writeToExplicitUri(appContext, targetUri) { out ->
                    ResultXlsxExporter.writeWorkbook(rows, "Result Ready", out)
                }
            }
            if (writeRes.isSuccess) {
                _resultState.update {
                    it.copy(
                        lastExportedFileName = fileNameLabel,
                        statusMessage = "Excel exported cleanly (${rows.size} students).",
                        isError = false
                    )
                }
            } else {
                _resultState.update {
                    it.copy(
                        statusMessage = writeRes.exceptionOrNull()?.message ?: "Export failed",
                        isError = true
                    )
                }
            }
        }
    }

    private suspend fun runBulkAutoLoop(webView: WebView, treeUri: Uri) {
        _bulkState.update {
            it.copy(
                jobStatus = AutomationJobStatus.RUNNING,
                isError = false,
                statusMessage = "Scanning Student Information table..."
            )
        }

        val selectedPagesList = effectiveSelectedPages()
        var currentPageSafetyCount = 0
        historyGoBackProducesCacheMiss = false
        clearDbErrorUrls()
        clearWebViewTransientError()

        while (currentPageSafetyCount < 200) {
            currentPageSafetyCount++
            if (!waitWhilePausedBulk() || stopBulkRequested) break

            val scan = scanTableInternal(webView)
            if (stopBulkRequested) break
            if (!scan.ok) {
                _bulkState.update {
                    it.copy(
                        jobStatus = if (scan.sessionExpired) AutomationJobStatus.PAUSED else AutomationJobStatus.ERROR,
                        statusMessage = scan.error,
                        isError = true
                    )
                }
                return
            }

            saveHrpcSnapshotNow(webView)
            val totalEst = maxOf(_bulkState.value.totalStudents, scan.totalEntries, scan.students.size)

            _bulkState.update { st ->
                st.copy(
                    scannedStudents = scan.students,
                    totalStudents = totalEst,
                    currentPageNumber = scan.currentPage,
                    totalPages = scan.totalPages,
                    pendingDownloads = (totalEst - st.completedKeys.size - st.failedList.size).coerceAtLeast(0)
                )
            }

            for (student in scan.students) {
                if (!waitWhilePausedBulk() || stopBulkRequested) break

                // Prevent duplicate downloads cleanly
                if (_bulkState.value.completedKeys.contains(student.uniqueKey)) {
                    continue
                }

                _bulkState.update { st ->
                    st.copy(
                        currentStudentName = student.name,
                        currentRollNo = student.rollNo,
                        currentPageNumber = scan.currentPage,
                        statusMessage = "Page ${scan.currentPage}/${scan.totalPages}: Downloading ${student.rollNo} - ${student.name}..."
                    )
                }

                val res = processSingleStudentPdf(webView, treeUri, student, scan.currentPage, selectedPagesList) { stopBulkRequested }
                if (stopBulkRequested) break

                if (res.isSuccess) {
                    val hist = res.getOrThrow()
                    _bulkState.update { st ->
                        val nextKeys = st.completedKeys + student.uniqueKey
                        val nextFailed = st.failedList.filterNot {
                            it.rollNo == student.rollNo && it.studentName == student.name && it.pageNumber == scan.currentPage
                        }
                        st.copy(
                            completedDownloads = nextKeys.size,
                            failedList = nextFailed,
                            failedDownloadsCount = nextFailed.size,
                            pendingDownloads = (st.totalStudents - nextKeys.size - nextFailed.size).coerceAtLeast(0),
                            completedKeys = nextKeys,
                            downloadHistory = listOf(hist) + st.downloadHistory,
                            statusMessage = "Saved ${hist.fileName}"
                        )
                    }
                } else {
                    val reason = res.exceptionOrNull()?.message ?: "Download failed"
                    if (reason.contains("session expired", ignoreCase = true)) {
                        pauseBulkRequested = true
                        _bulkState.update {
                            it.copy(
                                jobStatus = AutomationJobStatus.PAUSED,
                                statusMessage = "Portal session expired. Sign in on Portal and tap Resume.",
                                isError = true
                            )
                        }
                        if (!waitWhilePausedBulk() || stopBulkRequested) break
                    } else {
                        val fail = BulkFailedItem(student.rollNo, student.name, scan.currentPage, student.rowIndex, reason)
                        _bulkState.update { st ->
                            val nextFailed = (st.failedList + fail).distinctBy { "${it.rollNo}|${it.studentName}|${it.pageNumber}" }
                            st.copy(
                                failedList = nextFailed,
                                failedDownloadsCount = nextFailed.size,
                                pendingDownloads = (st.totalStudents - st.completedKeys.size - nextFailed.size).coerceAtLeast(0),
                                statusMessage = "Failed for ${student.rollNo} (${student.name}): $reason"
                            )
                        }
                        ensureOnTablePage(webView, scan.currentPage) { stopBulkRequested }
                    }
                }

                // Controlled pacing delay (~1.6s) after student's temporary background WebView is destroyed
                abortAwareDelay(INTER_STUDENT_PACING_DELAY_MS) { stopBulkRequested }
            }

            if (stopBulkRequested) break
            if (!waitWhilePausedBulk() || stopBulkRequested) break

            if (!scan.hasNextPage && scan.currentPage >= scan.totalPages) {
                break
            }

            _bulkState.update {
                it.copy(statusMessage = "Moving to Page ${scan.currentPage + 1}...")
            }
            ensureOnTablePage(webView, scan.currentPage) { stopBulkRequested }
            if (stopBulkRequested) break
            val advanced = advanceToNextTablePage(webView, scan.currentPage) { stopBulkRequested }
            if (!advanced) {
                break
            }
        }

        _bulkState.update { st ->
            val finishedStatus = if (stopBulkRequested) AutomationJobStatus.STOPPED else AutomationJobStatus.COMPLETED
            val msg = if (stopBulkRequested) {
                "Stopped. Saved ${st.completedDownloads} PDF(s), Failed ${st.failedDownloadsCount}."
            } else {
                "Completed! Downloaded ${st.completedDownloads} marksheet PDF(s). Failed: ${st.failedDownloadsCount}."
            }
            st.copy(
                jobStatus = finishedStatus,
                currentStudentName = "",
                currentRollNo = "",
                pendingDownloads = (st.totalStudents - st.completedKeys.size - st.failedDownloadsCount).coerceAtLeast(0),
                statusMessage = msg,
                isError = false
            )
        }
    }

    private suspend fun runResultExtractionLoop(webView: WebView) {
        _resultState.update {
            it.copy(
                jobStatus = AutomationJobStatus.RUNNING,
                isError = false,
                statusMessage = "Scanning Student Information table..."
            )
        }

        var pageSafetyCount = 0
        historyGoBackProducesCacheMiss = false
        clearDbErrorUrls()
        clearWebViewTransientError()

        while (pageSafetyCount < 200) {
            pageSafetyCount++
            if (!waitWhilePausedResult() || stopResultRequested) break

            val scan = scanTableInternal(webView)
            if (stopResultRequested) break
            if (!scan.ok) {
                _resultState.update {
                    it.copy(
                        jobStatus = if (scan.sessionExpired) AutomationJobStatus.PAUSED else AutomationJobStatus.ERROR,
                        statusMessage = scan.error,
                        isError = true
                    )
                }
                return
            }

            saveHrpcSnapshotNow(webView)
            val totalEst = maxOf(_resultState.value.totalStudents, scan.totalEntries, scan.students.size)

            _resultState.update { st ->
                st.copy(
                    scannedStudents = scan.students,
                    totalStudents = totalEst,
                    currentPageNumber = scan.currentPage,
                    totalPages = scan.totalPages,
                    pendingCount = (totalEst - st.extractedKeys.size - st.failedList.size).coerceAtLeast(0)
                )
            }

            for (student in scan.students) {
                if (!waitWhilePausedResult() || stopResultRequested) break
                if (_resultState.value.extractedKeys.contains(student.uniqueKey)) continue

                _resultState.update {
                    it.copy(
                        currentStudentName = student.name,
                        currentRollNo = student.rollNo,
                        currentPageNumber = scan.currentPage,
                        statusMessage = "Page ${scan.currentPage}/${scan.totalPages}: Extracting marks for ${student.rollNo} - ${student.name}..."
                    )
                }

                val res = extractSingleStudentSummative(webView, student, scan.currentPage) { stopResultRequested }
                if (stopResultRequested) break

                if (res.isSuccess) {
                    val row = res.getOrThrow()
                    _resultState.update { st ->
                        val mergedRows = mergeOrReplaceResultRow(st.extractedRows, row)
                        val updatedRows = ResultXlsxExporter.recalculatePositions(mergedRows)
                        val nextSchema = ResultXlsxExporter.resolveSubjectSchema(updatedRows)
                        val nextHeaders = ResultXlsxExporter.resolveTermHeaders(updatedRows, nextSchema)
                        val nextKeys = st.extractedKeys + student.uniqueKey
                        val nextFailed = st.failedList.filterNot {
                            it.rollNo == student.rollNo && it.studentName == student.name && it.pageNumber == scan.currentPage
                        }
                        st.copy(
                            subjectSchema = nextSchema,
                            termHeaders = nextHeaders,
                            extractedRows = updatedRows,
                            extractedKeys = nextKeys,
                            completedCount = updatedRows.size,
                            failedList = nextFailed,
                            failedCount = nextFailed.size,
                            pendingCount = (st.totalStudents - nextKeys.size - nextFailed.size).coerceAtLeast(0),
                            statusMessage = "Extracted ${row.rollNo} - ${row.studentName} (Grand Total: ${row.grandTotal.ifBlank { "-" }})"
                        )
                    }
                } else {
                    val reason = res.exceptionOrNull()?.message ?: "Extraction failed"
                    if (reason.contains("session expired", ignoreCase = true)) {
                        pauseResultRequested = true
                        _resultState.update {
                            it.copy(
                                jobStatus = AutomationJobStatus.PAUSED,
                                statusMessage = "Portal session expired. Re-login in Portal and tap Resume.",
                                isError = true
                            )
                        }
                        if (!waitWhilePausedResult() || stopResultRequested) break
                    } else {
                        val fail = ResultFailedItem(student.rollNo, student.name, scan.currentPage, student.rowIndex, reason)
                        _resultState.update { st ->
                            val nextFailed = (st.failedList + fail).distinctBy { "${it.rollNo}|${it.studentName}|${it.pageNumber}" }
                            st.copy(
                                failedList = nextFailed,
                                failedCount = nextFailed.size,
                                pendingCount = (st.totalStudents - st.extractedKeys.size - nextFailed.size).coerceAtLeast(0)
                            )
                        }
                        ensureOnTablePage(webView, scan.currentPage) { stopResultRequested }
                    }
                }
                // Controlled pacing delay (~1.6s) after student's temporary background WebView is destroyed
                abortAwareDelay(INTER_STUDENT_PACING_DELAY_MS) { stopResultRequested }
            }

            if (stopResultRequested) break
            if (!waitWhilePausedResult() || stopResultRequested) break

            if (!scan.hasNextPage && scan.currentPage >= scan.totalPages) break
            ensureOnTablePage(webView, scan.currentPage) { stopResultRequested }
            if (stopResultRequested) break
            val advanced = advanceToNextTablePage(webView, scan.currentPage) { stopResultRequested }
            if (!advanced) break
        }

        _resultState.update { st ->
            val finalRows = ResultXlsxExporter.recalculatePositions(st.extractedRows)
            val finalSchema = ResultXlsxExporter.resolveSubjectSchema(finalRows)
            val finalHeaders = ResultXlsxExporter.resolveTermHeaders(finalRows, finalSchema)
            st.copy(
                jobStatus = if (stopResultRequested) AutomationJobStatus.STOPPED else AutomationJobStatus.COMPLETED,
                subjectSchema = finalSchema,
                termHeaders = finalHeaders,
                extractedRows = finalRows,
                currentStudentName = "",
                currentRollNo = "",
                pendingCount = (st.totalStudents - st.extractedKeys.size - st.failedList.size).coerceAtLeast(0),
                statusMessage = if (stopResultRequested) {
                    "Stopped. Extracted ${finalRows.size} student row(s)."
                } else {
                    "Extraction complete! ${finalRows.size} student(s) ready. Tap 'Download Excel (.xlsx)' to save."
                }
            )
        }
    }

    private suspend fun abortAwareDelay(totalMs: Long, isAborted: suspend () -> Boolean) {
        if (totalMs <= 0L) return
        val deadline = System.currentTimeMillis() + totalMs
        while (!isAborted()) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            delay(minOf(200L, remaining))
        }
    }

    private suspend fun createFreshBackgroundWebView(): WebView? {
        capturedPdfDownloadUrl.set(null)
        capturedPdfUserAgent.set(null)
        clearBackgroundTransientError()
        cookieFlusher()
        val created = withContext(Dispatchers.Main) {
            runCatching { backgroundWebViewCreator?.invoke() ?: backgroundWebViewProvider() }.getOrNull()
        }
        delay(80L)
        return created
    }

    private suspend fun destroyStudentBackgroundWebView(targetView: WebView?) {
        if (targetView == null) return
        capturedPdfDownloadUrl.set(null)
        capturedPdfUserAgent.set(null)
        clearBackgroundTransientError()
        withContext(NonCancellable + Dispatchers.Main) {
            runCatching {
                val destroyer = backgroundWebViewDestroyer
                destroyer(targetView)
            }
        }
        cookieFlusher()
    }

    private suspend fun closeModalOnPortalTableIfOpen(portalWebView: WebView) {
        val raw = evalJs(portalWebView, PortalHrpcDomScripts.tryCloseInPageMarksheetOrModalJs())
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        if (obj?.optBoolean("handled", false) == true) {
            delay(180L)
        }
    }

    private fun buildFormEncodedBody(paramsArr: JSONArray?): String {
        if (paramsArr == null || paramsArr.length() == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until paramsArr.length()) {
            val item = paramsArr.optJSONObject(i) ?: continue
            val k = item.optString("name", "")
            if (k.isBlank()) continue
            val v = item.optString("value", "")
            if (sb.isNotEmpty()) sb.append('&')
            sb.append(URLEncoder.encode(k, "UTF-8"))
            sb.append('=')
            sb.append(URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }

    private suspend fun dispatchTargetToBackground(
        portalWebView: WebView,
        bgWebView: WebView,
        portalUrl: String,
        targetObj: JSONObject
    ): Boolean {
        if (targetObj.optBoolean("closeModalFirst", false)) {
            closeModalOnPortalTableIfOpen(portalWebView)
        }
        val mode = targetObj.optString("mode", "url").trim().lowercase(Locale.US)
        val headers = if (portalUrl.startsWith("http", ignoreCase = true)) {
            mapOf("Referer" to portalUrl)
        } else {
            emptyMap()
        }
        cookieFlusher()
        return when (mode) {
            "url" -> {
                val url = targetObj.optString("url", "").trim()
                if (!url.startsWith("http", ignoreCase = true)) return false
                withContext(Dispatchers.Main) {
                    if (headers.isEmpty()) bgWebView.loadUrl(url) else bgWebView.loadUrl(url, headers)
                }
                true
            }
            "form" -> {
                val actionUrl = targetObj.optString("url", "").trim()
                if (!actionUrl.startsWith("http", ignoreCase = true)) return false
                val method = targetObj.optString("method", "GET").trim().uppercase(Locale.US)
                val encoded = buildFormEncodedBody(targetObj.optJSONArray("params"))
                if (method == "POST") {
                    withContext(Dispatchers.Main) {
                        bgWebView.postUrl(actionUrl, encoded.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    val sep = if (actionUrl.contains('?')) '&' else '?'
                    val finalUrl = if (encoded.isNotBlank()) "$actionUrl$sep$encoded" else actionUrl
                    withContext(Dispatchers.Main) {
                        if (headers.isEmpty()) bgWebView.loadUrl(finalUrl) else bgWebView.loadUrl(finalUrl, headers)
                    }
                }
                true
            }
            "html" -> {
                val html = targetObj.optString("html", "")
                if (html.isBlank()) return false
                val baseUrl = targetObj.optString("baseUrl", "").trim()
                    .ifBlank { portalUrl }
                    .ifBlank { MainActivity.PORTAL_URL }
                withContext(Dispatchers.Main) {
                    bgWebView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                }
                true
            }
            "async_pending", "in_page_modal" -> true
            else -> false
        }
    }

    private suspend fun processSingleStudentPdf(
        webView: WebView,
        treeUri: Uri,
        student: HrpcStudentEntry,
        tablePageNum: Int,
        selectedPages: List<Int>,
        isAborted: () -> Boolean
    ): Result<BulkDownloadHistoryItem> {
        var lastError: Throwable? = null

        for (attemptRound in 1..MAX_STUDENT_ATTEMPT_ROUNDS) {
            if (!waitWhilePausedBulk() || isAborted()) {
                return Result.failure(IllegalStateException("Stopped by user"))
            }

            if (attemptRound > 1) {
                val retryNo = attemptRound - 1
                val maxRetries = MAX_STUDENT_ATTEMPT_ROUNDS - 1
                _bulkState.update {
                    it.copy(
                        statusMessage = "Slow network / error — fresh retry ($retryNo/$maxRetries) for Roll ${student.rollNo} (${student.name})..."
                    )
                }
                abortAwareDelay(SLOW_NET_RETRY_BACKOFF_MS) { !waitWhilePausedBulk() || isAborted() }
                if (!waitWhilePausedBulk() || isAborted()) {
                    return Result.failure(IllegalStateException("Stopped by user"))
                }
            }

            // Keep visible portalWebView permanently untouched on the Student Information table page
            val tableReady = ensureOnTablePage(webView, tablePageNum, isAborted)
            if (!tableReady) {
                lastError = IllegalStateException("Could not reach table Page $tablePageNum")
                continue
            }
            val portalUrl = withContext(Dispatchers.Main) { webView.url.orEmpty() }

            var activeTempBgView: WebView? = createFreshBackgroundWebView()
            try {
                if (activeTempBgView == null) {
                    lastError = IllegalStateException("Could not initialize isolated background WebView")
                    continue
                }

                isMainTabLockedForStudentFetch = true
                evalJs(webView, PortalHrpcDomScripts.setAutomationMainTabLockJs(true))

                val triggerJson = evalJs(
                    webView,
                    PortalHrpcDomScripts.triggerStudentActionJs(student.rowIndex, student.rollNo, student.name, "print")
                )
                val triggerObj = runCatching { JSONObject(triggerJson) }.getOrNull()
                if (triggerObj?.optBoolean("ok", false) != true) {
                    lastError = IllegalStateException(triggerObj?.optString("error") ?: "Print button failed")
                    continue
                }

                val candidates = mutableListOf(triggerObj)
                triggerObj.optJSONObject("fallback")?.let { fb ->
                    if (fb.optString("url", "").startsWith("http", ignoreCase = true)) {
                        candidates.add(fb)
                    }
                }

                for ((idx, targetObj) in candidates.withIndex()) {
                    if (!waitWhilePausedBulk() || isAborted()) {
                        return Result.failure(IllegalStateException("Stopped by user"))
                    }
                    val mode = targetObj.optString("mode", "").lowercase(Locale.US)

                    // For fallback candidate within the same round, destroy old temporary WebView and spin up a clean new one
                    if (idx > 0) {
                        destroyStudentBackgroundWebView(activeTempBgView)
                        activeTempBgView = createFreshBackgroundWebView()
                        if (activeTempBgView == null) {
                            lastError = IllegalStateException("Could not initialize fallback background WebView")
                            continue
                        }
                    }

                    val targetBg = activeTempBgView
                        ?: run {
                            lastError = IllegalStateException("Isolated background WebView unavailable")
                            continue
                        }
                    if (idx > 0 || mode != "async_pending") {
                        val dispatched = dispatchTargetToBackground(webView, targetBg, portalUrl, targetObj)
                        if (!dispatched) {
                            lastError = IllegalStateException("Could not dispatch marksheet request")
                            continue
                        }
                    }

                    val waitOutcome = waitForMarksheetOrPdfDownload(
                        targetBg,
                        isBackgroundMode = true,
                        timeoutMs = SLOW_NET_LOAD_TIMEOUT_MS,
                        isAborted = isAborted
                    )
                    if (isAborted()) {
                        return Result.failure(IllegalStateException("Stopped by user"))
                    }
                    val directPdfUrl = capturedPdfDownloadUrl.getAndSet(null)

                    if (!waitOutcome.ok && directPdfUrl.isNullOrBlank()) {
                        lastError = IllegalStateException(waitOutcome.error)
                        if (waitOutcome.error.contains("session expired", ignoreCase = true)) {
                            return Result.failure(lastError)
                        }
                        continue
                    }

                    val fileName = SafStorageHelper.sanitizeMarksheetFileName(student.rollNo, student.name, "pdf")
                    try {
                        var savedViaDirectPdf = false
                        if (!directPdfUrl.isNullOrBlank()) {
                            val rawPdfBytes: ByteArray? = when {
                                directPdfUrl.startsWith("data:", ignoreCase = true) -> {
                                    decodeDataUriPdfBytes(directPdfUrl)
                                }
                                directPdfUrl.startsWith("blob:", ignoreCase = true) -> {
                                    readBlobPdfBytesFromWebView(targetBg, directPdfUrl)
                                }
                                else -> {
                                    val cookieHeader = runCatching { cookieResolver(directPdfUrl) }.getOrNull()
                                    withContext(Dispatchers.IO) {
                                        downloadBytesWithCookies(directPdfUrl, cookieHeader, capturedPdfUserAgent.get())
                                    }
                                }
                            }

                            if (rawPdfBytes != null && rawPdfBytes.isNotEmpty()) {
                                val writeRes = withContext(Dispatchers.IO) {
                                    SafStorageHelper.writeFileToFolder(
                                        context = appContext,
                                        treeUri = treeUri,
                                        mimeType = "application/pdf",
                                        displayName = fileName,
                                        overwriteExisting = true
                                    ) { out ->
                                        val filterRes = MarksheetPdfGenerator.filterExistingPdfBytes(
                                            rawPdfBytes = rawPdfBytes,
                                            selectedPagesInput = selectedPages,
                                            cacheDir = appContext.cacheDir,
                                            outStream = out
                                        )
                                        if (filterRes.isFailure) {
                                            throw filterRes.exceptionOrNull() ?: IllegalStateException("PDF write failed")
                                        }
                                    }
                                }
                                if (writeRes.isFailure) {
                                    throw writeRes.exceptionOrNull() ?: IllegalStateException("SAF write failed")
                                }
                                savedViaDirectPdf = true
                            }
                        }

                        if (!savedViaDirectPdf) {
                            delay(420L)
                            val saveRes = captureActiveMarksheetWebViewToSaf(targetBg, treeUri, fileName, selectedPages)
                            if (saveRes.isFailure) {
                                throw saveRes.exceptionOrNull() ?: IllegalStateException("PDF generation failed")
                            }
                        }

                        return Result.success(
                            BulkDownloadHistoryItem(
                                rollNo = student.rollNo,
                                studentName = student.name,
                                pageNumber = tablePageNum,
                                fileName = fileName,
                                pagesSaved = selectedPages,
                                timestamp = nowTimeStr()
                            )
                        )
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }
            } finally {
                isMainTabLockedForStudentFetch = false
                withContext(NonCancellable + Dispatchers.Main) {
                    runCatching {
                        webView.evaluateJavascript(PortalHrpcDomScripts.setAutomationMainTabLockJs(false), null)
                    }
                }
                if (activeTempBgView != null) {
                    destroyStudentBackgroundWebView(activeTempBgView)
                    activeTempBgView = null
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Failed downloading marksheet PDF"))
    }

    private fun sanitizeExtractedMarkCell(raw: String?, maxAllowed: Double): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isBlank()) return ""
        val lower = cleaned.lowercase(Locale.US)
        if (lower in setOf("-", "--", "---", "na", "n/a", "nil", "*")) return ""
        if (lower == "ab" || lower == "absent" || lower == "abs") return "AB"
        // Reject any subject labels, headers, or grade strings defensively
        if (Regex("[a-zA-Z]{2,}|[\\u0980-\\u09FF]").containsMatchIn(cleaned)) {
            return ""
        }
        val num = cleaned.toDoubleOrNull() ?: return ""
        if (!num.isFinite() || num < 0.0 || num > maxAllowed) return ""
        return formatCleanNumber(num)
    }

    private fun sanitizeNumericText(raw: String?, maxAllowed: Double): String {
        val cleaned = raw?.replace("%", "")?.trim().orEmpty()
        val num = cleaned.toDoubleOrNull() ?: return ""
        if (!num.isFinite() || num < 0.0 || num > maxAllowed) return ""
        return if (cleaned.contains(".")) String.format(Locale.US, "%.2f", num) else formatCleanNumber(num)
    }

    private suspend fun extractSingleStudentSummative(
        webView: WebView,
        student: HrpcStudentEntry,
        tablePageNum: Int,
        isAborted: () -> Boolean
    ): Result<StudentSummativeResultRow> {
        var extractedObj: JSONObject? = null
        var lastError: String = "View failed"

        for (attemptRound in 1..MAX_STUDENT_ATTEMPT_ROUNDS) {
            if (!waitWhilePausedResult() || isAborted()) {
                return Result.failure(IllegalStateException("Stopped by user"))
            }

            if (attemptRound > 1) {
                val retryNo = attemptRound - 1
                val maxRetries = MAX_STUDENT_ATTEMPT_ROUNDS - 1
                _resultState.update {
                    it.copy(
                        statusMessage = "Slow network / error — fresh retry ($retryNo/$maxRetries) for Roll ${student.rollNo} (${student.name})..."
                    )
                }
                abortAwareDelay(SLOW_NET_RETRY_BACKOFF_MS) { !waitWhilePausedResult() || isAborted() }
                if (!waitWhilePausedResult() || isAborted()) {
                    return Result.failure(IllegalStateException("Stopped by user"))
                }
            }

            val tableReady = ensureOnTablePage(webView, tablePageNum, isAborted)
            if (!tableReady) {
                lastError = "Could not reach table Page $tablePageNum"
                continue
            }
            val portalUrl = withContext(Dispatchers.Main) { webView.url.orEmpty() }

            var activeTempBgView: WebView? = createFreshBackgroundWebView()
            try {
                if (activeTempBgView == null) {
                    lastError = "Could not initialize isolated background WebView"
                    continue
                }

                isMainTabLockedForStudentFetch = true
                evalJs(webView, PortalHrpcDomScripts.setAutomationMainTabLockJs(true))

                val triggerJson = evalJs(
                    webView,
                    PortalHrpcDomScripts.triggerStudentActionJs(student.rowIndex, student.rollNo, student.name, "view")
                )
                val triggerObj = runCatching { JSONObject(triggerJson) }.getOrNull()
                if (triggerObj?.optBoolean("ok", false) != true) {
                    lastError = triggerObj?.optString("error") ?: "View button failed"
                    continue
                }

                val candidates = mutableListOf(triggerObj)
                triggerObj.optJSONObject("fallback")?.let { fb ->
                    if (fb.optString("url", "").startsWith("http", ignoreCase = true)) {
                        candidates.add(fb)
                    }
                }

                for ((idx, targetObj) in candidates.withIndex()) {
                    if (!waitWhilePausedResult() || isAborted()) {
                        return Result.failure(IllegalStateException("Stopped by user"))
                    }
                    val mode = targetObj.optString("mode", "").lowercase(Locale.US)

                    if (idx > 0) {
                        destroyStudentBackgroundWebView(activeTempBgView)
                        activeTempBgView = createFreshBackgroundWebView()
                        if (activeTempBgView == null) {
                            lastError = "Could not initialize fallback background WebView"
                            continue
                        }
                    }

                    val targetBg = activeTempBgView
                        ?: run {
                            lastError = "Isolated background WebView unavailable"
                            continue
                        }
                    if (idx > 0 || mode != "async_pending") {
                        val dispatched = dispatchTargetToBackground(webView, targetBg, portalUrl, targetObj)
                        if (!dispatched) {
                            lastError = "Could not dispatch marksheet preview request"
                            continue
                        }
                    }

                    val waitOutcome = waitForMarksheetOrPdfDownload(
                        targetBg,
                        isBackgroundMode = true,
                        timeoutMs = SLOW_NET_LOAD_TIMEOUT_MS,
                        isAborted = isAborted
                    )
                    if (isAborted()) {
                        return Result.failure(IllegalStateException("Stopped by user"))
                    }
                    if (!waitOutcome.ok) {
                        lastError = waitOutcome.error
                        if (waitOutcome.error.contains("session expired", ignoreCase = true)) {
                            return Result.failure(IllegalStateException(lastError))
                        }
                        continue
                    }

                    delay(320L)
                    if (isAborted()) {
                        return Result.failure(IllegalStateException("Stopped by user"))
                    }

                    val extractedJson = evalJs(targetBg, PortalHrpcDomScripts.extractSummativeMarksJs(student.rollNo, student.name))

                    val parsed = runCatching { JSONObject(extractedJson) }.getOrNull()
                    if (parsed == null) {
                        lastError = "Failed parsing marksheet DOM"
                        continue
                    }
                    if (!parsed.optBoolean("ok", false)) {
                        lastError = parsed.optString("error", "Summative Evaluation table not found")
                        if (parsed.optBoolean("sessionExpired", false)) {
                            return Result.failure(IllegalStateException("Portal session expired"))
                        }
                        continue
                    }
                    extractedObj = parsed
                    break
                }
            } finally {
                isMainTabLockedForStudentFetch = false
                withContext(NonCancellable + Dispatchers.Main) {
                    runCatching {
                        webView.evaluateJavascript(PortalHrpcDomScripts.setAutomationMainTabLockJs(false), null)
                    }
                }
                if (activeTempBgView != null) {
                    destroyStudentBackgroundWebView(activeTempBgView)
                    activeTempBgView = null
                }
            }
            if (extractedObj != null) break
        }

        val obj = extractedObj
            ?: return Result.failure(IllegalStateException(lastError))

        val orderedArr = obj.optJSONArray("orderedSubjects") ?: JSONArray()
        val subjectsObj = obj.optJSONObject("subjects") ?: JSONObject()
        val termHeadersObj = obj.optJSONObject("termHeaders") ?: JSONObject()
        val printedObj = obj.optJSONObject("printed") ?: JSONObject()

        val orderedSubjects = mutableListOf<DynamicSubjectDef>()
        for (i in 0 until orderedArr.length()) {
            val sObj = orderedArr.optJSONObject(i) ?: continue
            val key = sObj.optString("key", "").trim()
            val code = sObj.optString("code", "").trim()
            val displayName = sObj.optString("displayName", code).trim()
            if (key.isBlank() || code.isBlank()) continue
            val maxT1 = sObj.optDouble("maxT1", 20.0).takeIf { it > 0.0 } ?: 20.0
            val maxT2 = sObj.optDouble("maxT2", 30.0).takeIf { it > 0.0 } ?: 30.0
            val maxT3 = sObj.optDouble("maxT3", 50.0).takeIf { it > 0.0 } ?: 50.0
            val maxTotal = sObj.optDouble("maxTotal", maxT1 + maxT2 + maxT3).takeIf { it > 0.0 } ?: (maxT1 + maxT2 + maxT3)
            orderedSubjects.add(
                DynamicSubjectDef(
                    key = key,
                    code = code,
                    displayName = displayName,
                    maxT1 = maxT1,
                    maxT2 = maxT2,
                    maxT3 = maxT3,
                    maxTotal = maxTotal
                )
            )
        }

        if (orderedSubjects.isEmpty()) {
            orderedSubjects.addAll(
                listOf(
                    DynamicSubjectDef("ben", "BEN", "Bengali", 20.0, 30.0, 50.0, 100.0),
                    DynamicSubjectDef("eng", "ENG", "English", 20.0, 30.0, 50.0, 100.0),
                    DynamicSubjectDef("evs", "EVS", "Our Environment", 20.0, 30.0, 50.0, 100.0),
                    DynamicSubjectDef("math", "MATH", "Mathematics", 20.0, 30.0, 50.0, 100.0),
                    DynamicSubjectDef("health", "HEALTH", "Health & Physical Ed.", 20.0, 30.0, 50.0, 100.0),
                    DynamicSubjectDef("art", "ART", "Art & Work Ed.", 20.0, 30.0, 50.0, 100.0)
                )
            )
        }

        fun sumNumericStrings(values: List<String>): Double? {
            var sum = 0.0
            var hasNumeric = false
            for (v in values) {
                val n = v.toDoubleOrNull()
                if (n != null && n.isFinite() && n >= 0.0) {
                    sum += n
                    hasNumeric = true
                }
            }
            return if (hasNumeric) sum else null
        }

        val dynamicMarks = LinkedHashMap<String, DynamicSubjectTermMarks>()
        val t1Vals = mutableListOf<String>()
        val t2Vals = mutableListOf<String>()
        val t3Vals = mutableListOf<String>()
        val subjTotalsList = mutableListOf<String>()
        var fullMarksTotal = 0.0

        for (sub in orderedSubjects) {
            val sNode = subjectsObj.optJSONObject(sub.key)
            val m1 = sanitizeExtractedMarkCell(sNode?.optString("t1", ""), sub.maxT1.coerceAtLeast(100.0))
            val m2 = sanitizeExtractedMarkCell(sNode?.optString("t2", ""), sub.maxT2.coerceAtLeast(100.0))
            val m3 = sanitizeExtractedMarkCell(sNode?.optString("t3", ""), sub.maxT3.coerceAtLeast(100.0))
            val sumSub = sumNumericStrings(listOf(m1, m2, m3))
            val subTot = if (sumSub != null) {
                formatCleanNumber(sumSub)
            } else {
                sanitizeExtractedMarkCell(sNode?.optString("total", ""), sub.maxTotal.coerceAtLeast(200.0))
            }
            dynamicMarks[sub.key] = DynamicSubjectTermMarks(t1 = m1, t2 = m2, t3 = m3, total = subTot)
            t1Vals.add(m1)
            t2Vals.add(m2)
            t3Vals.add(m3)
            subjTotalsList.add(subTot)
            fullMarksTotal += sub.maxTotal
        }

        val tot1 = sumNumericStrings(t1Vals)?.let { formatCleanNumber(it) }
            ?: sanitizeExtractedMarkCell(printedObj.optString("t1", ""), 1200.0)
        val tot2 = sumNumericStrings(t2Vals)?.let { formatCleanNumber(it) }
            ?: sanitizeExtractedMarkCell(printedObj.optString("t2", ""), 1200.0)
        val tot3 = sumNumericStrings(t3Vals)?.let { formatCleanNumber(it) }
            ?: sanitizeExtractedMarkCell(printedObj.optString("t3", ""), 1500.0)

        val computedGrand = sumNumericStrings(subjTotalsList)
        val printedGrand = sanitizeExtractedMarkCell(printedObj.optString("grandTotal", ""), 2500.0).toDoubleOrNull()
        val finalGrandNum = computedGrand ?: printedGrand
        val grandTotalStr = finalGrandNum?.let { formatCleanNumber(it) } ?: ""

        val pctStr = if (finalGrandNum != null && fullMarksTotal > 0.0) {
            val pctNum = (finalGrandNum / fullMarksTotal) * 100.0
            String.format(Locale.US, "%.2f", pctNum.coerceIn(0.0, 100.0))
        } else {
            sanitizeNumericText(printedObj.optString("percentage", ""), 100.0)
        }

        val posStr = sanitizeExtractedMarkCell(printedObj.optString("position", ""), 999.0)
        val displayRoll = obj.optString("rollNo", student.rollNo).trim().ifBlank { student.rollNo }
        val displayName = obj.optString("studentName", student.name).trim().ifBlank { student.name }

        val headers = ResultTermHeaders(
            term1Header = termHeadersObj.optString("term1Header", "").ifBlank { "1ST SUMMATIVE" },
            term2Header = termHeadersObj.optString("term2Header", "").ifBlank { "2ND SUMMATIVE" },
            term3Header = termHeadersObj.optString("term3Header", "").ifBlank { "3RD SUMMATIVE" }
        )

        return Result.success(
            StudentSummativeResultRow(
                rollNo = displayRoll,
                studentName = displayName,
                orderedSubjects = orderedSubjects,
                subjectMarks = dynamicMarks,
                term1Total = tot1,
                term2Total = tot2,
                term3Total = tot3,
                grandTotal = grandTotalStr,
                fullMarksTotal = fullMarksTotal,
                percentage = pctStr,
                position = posStr,
                termHeaders = headers
            )
        )
    }

    private suspend fun captureActiveMarksheetWebViewToSaf(
        webView: WebView,
        treeUri: Uri,
        fileName: String,
        selectedPages: List<Int>
    ): Result<Uri> {
        val pdfBytesRes = MarksheetPdfGenerator.captureSelectedPagesToPdfBytes(webView, selectedPages, 5)
        if (pdfBytesRes.isFailure) {
            return Result.failure(pdfBytesRes.exceptionOrNull() ?: IllegalStateException("Render failed"))
        }
        val bytes = pdfBytesRes.getOrThrow()
        return withContext(Dispatchers.IO) {
            SafStorageHelper.writeFileToFolder(
                context = appContext,
                treeUri = treeUri,
                mimeType = "application/pdf",
                displayName = fileName,
                overwriteExisting = true
            ) { out ->
                out.write(bytes)
            }
        }
    }

    private suspend fun waitForMarksheetOrPdfDownload(
        targetView: WebView,
        isBackgroundMode: Boolean,
        timeoutMs: Long,
        isAborted: () -> Boolean
    ): SimpleOutcome {
        val startMs = System.currentTimeMillis()
        if (isBackgroundMode) {
            clearBackgroundTransientError()
        } else {
            clearWebViewTransientError()
        }
        val deadline = startMs + timeoutMs
        var firstMarksheetReadySeenAt = 0L
        var firstInteractiveReadySeenAt = 0L

        while (System.currentTimeMillis() < deadline) {
            if (isAborted()) {
                return SimpleOutcome(false, "Stopped by user")
            }
            if (capturedPdfDownloadUrl.get() != null) {
                return SimpleOutcome(true, "")
            }
            if (isBackgroundMode) {
                if (lastBgCacheMissAtMs >= startMs) {
                    return SimpleOutcome(false, "Portal form cache miss (ERR_CACHE_MISS)", isCacheMiss = true)
                }
                if (lastBgMainFrameErrorAtMs >= startMs && !lastBgMainFrameErrorDesc.isNullOrBlank()) {
                    val errStr = lastBgMainFrameErrorDesc ?: "Marksheet page failed to load"
                    return SimpleOutcome(
                        false,
                        errStr,
                        isCacheMiss = errStr.contains("CACHE_MISS", ignoreCase = true)
                    )
                }
            } else {
                if (lastWebViewCacheMissAtMs >= startMs) {
                    return SimpleOutcome(false, "Portal form cache miss (ERR_CACHE_MISS)", isCacheMiss = true)
                }
                if (lastMainFrameErrorAtMs >= startMs && !lastMainFrameErrorDesc.isNullOrBlank()) {
                    val errStr = lastMainFrameErrorDesc ?: "Portal page error"
                    return SimpleOutcome(
                        false,
                        errStr,
                        isCacheMiss = errStr.contains("CACHE_MISS", ignoreCase = true)
                    )
                }
            }

            val raw = evalJs(targetView, PortalHrpcDomScripts.checkMarksheetRenderJs())
            val obj = runCatching { JSONObject(raw) }.getOrNull()
            if (obj != null) {
                if (obj.optBoolean("hasAuthForm", false)) {
                    return SimpleOutcome(false, "Portal session expired. Please log in again.")
                }
                val pageUrl = obj.optString("url", "")
                if (obj.optBoolean("hasPortalError", false)) {
                    markUrlAsDbError(pageUrl)
                    return SimpleOutcome(
                        false,
                        "Portal database error displayed for student marksheet",
                        isCacheMiss = true
                    )
                }
                if (pageUrl.contains("chromewebdata", ignoreCase = true)) {
                    val bodyCheck = evalJs(
                        targetView,
                        "(function(){var t=(document.body&&document.body.innerText)||'';return /ERR_CACHE_MISS|Confirm Form Resubmission|Webpage not available/i.test(t)?'CACHE_MISS':'OK';})();"
                    )
                    if (bodyCheck.contains("CACHE_MISS")) {
                        if (isBackgroundMode) notifyBackgroundCacheMiss() else notifyWebViewCacheMiss()
                        return SimpleOutcome(false, "Portal form cache miss (ERR_CACHE_MISS)", isCacheMiss = true)
                    }
                }
                if (pageUrl == "about:blank") {
                    delay(SLOW_NET_POLL_INTERVAL_MS)
                    continue
                }
                val readyState = obj.optString("readyState", "")
                val looksLikeMarksheet = obj.optBoolean("looksLikeMarksheet", false)
                val hasSummative = obj.optBoolean("hasSummative", false)
                val spinner = obj.optBoolean("spinnerVisible", false)
                val pendingImgs = obj.optInt("pendingImgs", 0)
                val textLen = obj.optInt("textLen", 0)
                if (looksLikeMarksheet && !spinner) {
                    if (readyState == "complete") {
                        if (firstMarksheetReadySeenAt == 0L) {
                            firstMarksheetReadySeenAt = System.currentTimeMillis()
                        }
                        // On slow connections allow up to 5s for crest/icon images before capturing
                        if (pendingImgs == 0 || (System.currentTimeMillis() - firstMarksheetReadySeenAt) >= SLOW_NET_IMAGE_SETTLE_MAX_MS) {
                            return SimpleOutcome(true, "")
                        }
                    } else if (readyState == "interactive" && (hasSummative || textLen > 400)) {
                        // Fallback when DOM content already appeared cleanly but an external subresource stalls readyState in interactive
                        if (firstInteractiveReadySeenAt == 0L) {
                            firstInteractiveReadySeenAt = System.currentTimeMillis()
                        } else if ((System.currentTimeMillis() - firstInteractiveReadySeenAt) >= SLOW_NET_INTERACTIVE_GRACE_MS && pendingImgs == 0) {
                            return SimpleOutcome(true, "")
                        }
                    }
                } else {
                    firstMarksheetReadySeenAt = 0L
                    firstInteractiveReadySeenAt = 0L
                }
            }
            delay(SLOW_NET_POLL_INTERVAL_MS)
        }
        return SimpleOutcome(false, "Timed out waiting for marksheet to render")
    }

    private suspend fun navigateToExactTablePageIfNeeded(
        webView: WebView,
        targetPage: Int,
        isAborted: () -> Boolean
    ): Boolean {
        val currentScan = scanTableInternal(webView)
        if (!currentScan.ok) return false
        if (currentScan.currentPage == targetPage || targetPage <= 1) return true

        var steps = 0
        var latestPage = currentScan.currentPage
        while (latestPage != targetPage && steps < 35) {
            if (isAborted()) return false
            steps++
            val clickJson = evalJs(webView, PortalHrpcDomScripts.clickPaginationPageJs(targetPage, "exact"))
            val clickObj = runCatching { JSONObject(clickJson) }.getOrNull()
            if (clickObj?.optBoolean("alreadyOnPage", false) == true) return true
            if (clickObj?.optBoolean("clicked", false) != true) return false

            delay(400L)
            var changed = false
            val deadline = System.currentTimeMillis() + SLOW_NET_PAGE_SEEK_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (isAborted()) return false
                val afterScan = scanTableInternal(webView)
                if (afterScan.ok && !afterScan.isProcessing && afterScan.currentPage == targetPage && afterScan.students.isNotEmpty()) {
                    return true
                }
                if (afterScan.ok && !afterScan.isProcessing && afterScan.currentPage != latestPage && afterScan.students.isNotEmpty()) {
                    latestPage = afterScan.currentPage
                    changed = true
                    break
                }
                delay(SLOW_NET_POLL_INTERVAL_MS)
            }
            if (!changed) break
        }
        val verify = scanTableInternal(webView)
        return verify.ok && verify.currentPage == targetPage
    }

    private suspend fun ensureOnTablePage(
        webView: WebView,
        targetPage: Int,
        isAborted: () -> Boolean
    ): Boolean {
        val waitDeadline = System.currentTimeMillis() + 12_000L
        while (System.currentTimeMillis() < waitDeadline) {
            if (isAborted()) return false
            val scanNow = scanTableInternal(webView)
            if (scanNow.ok && !scanNow.isProcessing && scanNow.students.isNotEmpty()) {
                return navigateToExactTablePageIfNeeded(webView, targetPage, isAborted)
            }
            if (scanNow.sessionExpired) return false
            delay(SLOW_NET_POLL_INTERVAL_MS)
        }
        return false
    }

    private suspend fun advanceToNextTablePage(
        webView: WebView,
        prevPage: Int,
        isAborted: () -> Boolean
    ): Boolean {
        if (isAborted()) return false
        val clickJson = evalJs(webView, PortalHrpcDomScripts.clickPaginationPageJs(prevPage + 1, "next"))
        val obj = runCatching { JSONObject(clickJson) }.getOrNull() ?: return false
        if (!obj.optBoolean("ok", false)) return false
        val prevFirstRow = obj.optString("prevFirstRow", "").trim()

        var retriedClick = false
        val startMs = System.currentTimeMillis()
        val deadline = startMs + SLOW_NET_PAGINATION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isAborted()) return false
            delay(380L)
            val after = scanTableInternal(webView)
            if (after.ok && !after.isProcessing && after.students.isNotEmpty()) {
                if (after.currentPage > prevPage) return true
                val firstAfter = after.students.firstOrNull()?.let { "${it.rollNo} ${it.name}".trim() }.orEmpty()
                if (prevFirstRow.isNotBlank() && firstAfter.isNotBlank() &&
                    !prevFirstRow.contains(firstAfter, ignoreCase = true)
                ) {
                    return true
                }
            }
            // If slow click was ignored while table wasn't processing, retry Next click once after 12s
            if (!retriedClick && (System.currentTimeMillis() - startMs) >= 12_000L && after.ok && !after.isProcessing) {
                retriedClick = true
                evalJs(webView, PortalHrpcDomScripts.clickPaginationPageJs(prevPage + 1, "next"))
            }
        }
        return false
    }

    private suspend fun scanTableInternal(webView: WebView): TableScanOutcome {
        val raw = evalJs(webView, PortalHrpcDomScripts.scanHrpcTableJs())
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: return TableScanOutcome(ok = false, error = "Unable to inspect portal page.")
        if (!obj.optBoolean("ok", false)) {
            val isDbErr = obj.optBoolean("dbError", false)
            if (isDbErr) {
                markUrlAsDbError(obj.optString("url", ""))
            }
            return TableScanOutcome(
                ok = false,
                sessionExpired = obj.optBoolean("sessionExpired", false),
                dbError = isDbErr,
                error = obj.optString("reason", "Student table not detected.")
            )
        }
        val arr = obj.optJSONArray("students") ?: JSONArray()
        val list = mutableListOf<HrpcStudentEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(
                HrpcStudentEntry(
                    rowIndex = item.optInt("rowIndex", i),
                    rollNo = item.optString("rollNo", ""),
                    name = item.optString("name", ""),
                    hasView = item.optBoolean("hasView", false),
                    hasPrint = item.optBoolean("hasPrint", false),
                    viewHref = item.optString("viewHref", ""),
                    printHref = item.optString("printHref", "")
                )
            )
        }
        return TableScanOutcome(
            ok = true,
            isProcessing = obj.optBoolean("isProcessing", false),
            currentPage = obj.optInt("currentPage", 1).coerceAtLeast(1),
            totalPages = obj.optInt("totalPages", 1).coerceAtLeast(1),
            totalEntries = obj.optInt("totalEntries", list.size).coerceAtLeast(list.size),
            hasNextPage = obj.optBoolean("hasNextPage", false),
            students = list
        )
    }

    private suspend fun saveHrpcSnapshotNow(webView: WebView) {
        val snap = evalJs(webView, PortalHrpcDomScripts.snapshotHrpcStateJs())
        updateLastHrpcSnapshot(snap)
    }

    private fun effectiveSelectedPages(): List<Int> {
        val sel = _bulkState.value.selectedPages
        return if (sel.isEmpty()) listOf(1, 2, 3, 4, 5) else sel.sorted()
    }

    private fun updateFailedReason(item: BulkFailedItem, newReason: String) {
        _bulkState.update { st ->
            val updated = st.failedList.map {
                if (it.rollNo == item.rollNo && it.studentName == item.studentName && it.pageNumber == item.pageNumber) {
                    it.copy(reason = newReason)
                } else {
                    it
                }
            }
            st.copy(failedList = updated, failedDownloadsCount = updated.size, statusMessage = newReason)
        }
    }

    private fun updateResultFailedReason(item: ResultFailedItem, newReason: String) {
        _resultState.update { st ->
            val updated = st.failedList.map {
                if (it.rollNo == item.rollNo && it.studentName == item.studentName && it.pageNumber == item.pageNumber) {
                    it.copy(reason = newReason)
                } else {
                    it
                }
            }
            st.copy(failedList = updated, failedCount = updated.size, statusMessage = newReason)
        }
    }

    private fun mergeOrReplaceResultRow(
        existing: List<StudentSummativeResultRow>,
        incoming: StudentSummativeResultRow
    ): List<StudentSummativeResultRow> {
        val key = "${incoming.rollNo.trim().lowercase(Locale.US)}|${incoming.studentName.trim().lowercase(Locale.US)}"
        val idx = existing.indexOfFirst {
            "${it.rollNo.trim().lowercase(Locale.US)}|${it.studentName.trim().lowercase(Locale.US)}" == key
        }
        return if (idx >= 0) {
            existing.toMutableList().apply { this[idx] = incoming }
        } else {
            existing + incoming
        }
    }

    private suspend fun waitWhilePausedBulk(): Boolean {
        while (pauseBulkRequested && !stopBulkRequested) {
            delay(300L)
        }
        return !stopBulkRequested
    }

    private suspend fun waitWhilePausedResult(): Boolean {
        while (pauseResultRequested && !stopResultRequested) {
            delay(300L)
        }
        return !stopResultRequested
    }

    private fun formatCleanNumber(v: Double): String {
        return if (v == v.roundToInt().toDouble()) {
            v.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        }
    }

    private fun nowTimeStr(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun decodeDataUriPdfBytes(dataUri: String): ByteArray? {
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx <= 0 || commaIdx + 1 >= dataUri.length) return null
        val b64 = dataUri.substring(commaIdx + 1).trim()
        return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun readBlobPdfBytesFromWebView(webView: WebView, blobUrl: String): ByteArray? {
        val quoted = JSONObject.quote(blobUrl)
        val startJson = evalJs(
            webView,
            """
            (function() {
                try {
                    window.__bspBlobDone = false;
                    window.__bspBlobB64 = '';
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', $quoted, true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        if (xhr.status === 200 || xhr.status === 0) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                var res = String(reader.result || '');
                                var idx = res.indexOf(',');
                                window.__bspBlobB64 = idx >= 0 ? res.slice(idx + 1) : '';
                                window.__bspBlobDone = true;
                            };
                            reader.readAsDataURL(xhr.response);
                        } else {
                            window.__bspBlobDone = true;
                        }
                    };
                    xhr.onerror = function() { window.__bspBlobDone = true; };
                    xhr.send();
                    return 'STARTED';
                } catch (e) {
                    return 'ERR';
                }
            })();
            """.trimIndent()
        )
        if (!startJson.contains("STARTED")) return null
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            delay(200L)
            val statusJson = evalJs(
                webView,
                "(function(){return JSON.stringify({done:!!window.__bspBlobDone,data:window.__bspBlobB64||''});})();"
            )
            val stObj = runCatching { JSONObject(statusJson) }.getOrNull() ?: continue
            if (stObj.optBoolean("done", false)) {
                val b64 = stObj.optString("data", "")
                if (b64.isNotBlank()) {
                    return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
                return null
            }
        }
        return null
    }

    private fun downloadBytesWithCookies(urlStr: String, cookies: String?, userAgent: String?): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
            if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Download HTTP $code")
            }
            val baos = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                input.copyTo(baos)
            }
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) error("Empty PDF response")
            bytes
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun evalJs(webView: WebView, script: String): String =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { raw ->
                val decoded = if (raw == null || raw == "null") {
                    "{}"
                } else {
                    try {
                        if (raw.startsWith("\"") && raw.endsWith("\"")) {
                            JSONArray("[$raw]").getString(0)
                        } else {
                            raw
                        }
                    } catch (_: Exception) {
                        raw
                    }
                }
                if (cont.isActive) cont.resume(decoded)
            }
        }

    private data class SimpleOutcome(
        val ok: Boolean,
        val error: String,
        val isCacheMiss: Boolean = false
    )
    private data class TableScanOutcome(
        val ok: Boolean,
        val sessionExpired: Boolean = false,
        val dbError: Boolean = false,
        val isProcessing: Boolean = false,
        val currentPage: Int = 1,
        val totalPages: Int = 1,
        val totalEntries: Int = 0,
        val hasNextPage: Boolean = false,
        val students: List<HrpcStudentEntry> = emptyList(),
        val error: String = ""
    )
}
