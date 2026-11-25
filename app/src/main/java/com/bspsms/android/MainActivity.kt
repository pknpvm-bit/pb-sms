package com.bspsms.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppLocale { EN, BN }

enum class ToolDestination(
    val id: String,
    val backendKey: String,
    val titleEn: String,
    val titleBn: String,
    val descEn: String,
    val descBn: String,
    val entryPath: String,
    val contentScript: String? = null,
    val icon: ImageVector
) {
    FORMATIVE(
        id = "formative",
        backendKey = "formative",
        titleEn = "Formative Filler",
        titleBn = "ফরমেটিভ ফিলার",
        descEn = "Automatic marks filling",
        descBn = "স্বয়ংক্রিয় নম্বর পূরণ",
        entryPath = "formative/popup.html",
        icon = Icons.Outlined.Edit
    ),
    TALK(
        id = "talk",
        backendKey = "talk",
        titleEn = "Talk to Filler",
        titleBn = "টক টু ফিলার",
        descEn = "Voice-controlled marks entry",
        descBn = "ভয়েস-নিয়ন্ত্রিত নম্বর এন্ট্রি",
        entryPath = "talk/popup.html",
        icon = Icons.Outlined.Mic
    ),
    LPCD(
        id = "lpcd",
        backendKey = "lpcd",
        titleEn = "LPCD Filler",
        titleBn = "এলপিসিডি ফিলার",
        descEn = "Qualitative data auto & manual",
        descBn = "গুণগত তথ্য স্বয়ংক্রিয় ও ম্যানুয়াল",
        entryPath = "lpcd/popup.html",
        contentScript = "lpcd/content.js",
        icon = Icons.Outlined.Layers
    ),
    BCO(
        id = "bco",
        backendKey = "bco",
        titleEn = "BCO Filler",
        titleBn = "বিসিও ফিলার",
        descEn = "A/B form group filler",
        descBn = "এ/বি ফর্ম গ্রুপ ফিলার",
        entryPath = "bco/ab_form_filler_v2/popup.html",
        contentScript = "bco/ab_form_filler_v2/content.js",
        icon = Icons.Outlined.GridView
    ),
    EXCEL(
        id = "excel",
        backendKey = "excel",
        titleEn = "Excel Filler",
        titleBn = "এক্সেল ফিলার",
        descEn = "Excel / CSV to webpage",
        descBn = "এক্সেল / সিএসভি থেকে ওয়েবপেজ",
        entryPath = "excel/popup.html",
        icon = Icons.Outlined.TableChart
    ),
    BULK_MARKSHEET(
        id = "bulk_marksheet",
        backendKey = "marksheet",
        titleEn = "BULK MARKSHEET DOWNLOADER",
        titleBn = "বাল্ক মার্কশিট ডাউনলোডার",
        descEn = "Automatic & Manual HRPC Marksheet Download",
        descBn = "অটো ও ম্যানুয়াল HRPC মার্কশিট ডাউনলোড",
        entryPath = "bulk_marksheet/popup.html",
        icon = Icons.Outlined.PictureAsPdf
    ),
    RESULT_READY(
        id = "result_ready",
        backendKey = "result_ready",
        titleEn = "RESULT READY TOOL",
        titleBn = "রেজাল্ট রেডি টুল",
        descEn = "Extract Summative Evaluation to Excel (.xlsx)",
        descBn = "সামেটিভ নম্বর এক্সেল (.xlsx)-এ ডাউনলোড",
        entryPath = "result_ready/popup.html",
        icon = Icons.Outlined.Assessment
    ),
    HPRC_SCAN(
        id = "hprc_scan",
        backendKey = "hprc_scan",
        titleEn = "HPRC SCAN TOOL",
        titleBn = "HPRC স্ক্যান টুল",
        descEn = "Camera OCR scan to Excel & Portal fill",
        descBn = "ক্যামেরা OCR স্ক্যান → এক্সেল ও পোর্টাল ফিল",
        entryPath = "hprc_scan/native",
        icon = Icons.Outlined.CameraAlt
    )
}

enum class PortalErrorKind {
    CONNECTION_TIMEOUT,
    SERVER_ERROR,
    INVALID_CAPTCHA,
    RATE_LIMITED,
    RENDERER_GONE
}

data class PortalErrorState(
    val title: String,
    val detail: String,
    val isBlockingErrorPage: Boolean,
    val kind: PortalErrorKind = PortalErrorKind.SERVER_ERROR,
    val failedUrl: String? = null,
    val canRefreshCaptcha: Boolean = false,
    val waitSeconds: Int = 0
)

sealed interface NavScreen {
    data object Portal : NavScreen
    data object Hub : NavScreen
    data object AdminPanel : NavScreen
    data class Tool(val tool: ToolDestination) : NavScreen
}

class MainActivity : ComponentActivity() {
    companion object {
        const val PORTAL_URL = "https://school.banglarshiksha.gov.in/sms/"
        private const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
        private const val ASSET_BASE = "$ASSET_ORIGIN/assets/extension/"
        private val NO_CACHE_HEADERS = mapOf(
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Expires" to "0"
        )
    }

    private var portalWebView: WebView? = null
    private var bgMarksheetHost: FrameLayout? = null
    private var bgMarksheetWebView: WebView? = null
    private var popupPortalWebView: WebView? = null
    private var toolWebView: WebView? = null
    private var activeBridge: ChromeCompatBridge? = null
    private var activePortalProfileName: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAudioRequest: PermissionRequest? = null
    private var pendingSpeechCallback: ((Boolean) -> Unit)? = null

    private fun destroyTemporaryBackgroundWebView(targetView: WebView? = null) {
        val viewToDestroy = targetView ?: bgMarksheetWebView ?: return
        if (bgMarksheetWebView === viewToDestroy) {
            bgMarksheetWebView = null
        }
        runCatching { viewToDestroy.stopLoading() }
        runCatching { viewToDestroy.onPause() }
        runCatching { viewToDestroy.loadUrl("about:blank") }
        runCatching { viewToDestroy.clearHistory() }
        runCatching { viewToDestroy.clearFormData() }
        runCatching { viewToDestroy.removeJavascriptInterface("BspAndroidBridge") }
        runCatching { viewToDestroy.webChromeClient = null }
        runCatching { viewToDestroy.webViewClient = WebViewClient() }
        runCatching { (viewToDestroy.parent as? ViewGroup)?.removeView(viewToDestroy) }
        runCatching { bgMarksheetHost?.removeAllViews() }
        runCatching { viewToDestroy.removeAllViews() }
        runCatching { viewToDestroy.destroy() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching { WebView.enableSlowWholeDocumentDraw() }
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }

        setContent {
            MaterialTheme {
                BspSmsRoot()
            }
        }
    }

    override fun onPause() {
        flushPortalCookies(activePortalProfileName)
        super.onPause()
    }

    override fun onStop() {
        flushPortalCookies(activePortalProfileName)
        super.onStop()
    }

    override fun onDestroy() {
        flushPortalCookies(activePortalProfileName)
        destroyTemporaryBackgroundWebView()
        bgMarksheetHost = null
        popupPortalWebView?.stopLoading()
        runCatching { popupPortalWebView?.destroy() }
        popupPortalWebView = null
        activeBridge?.destroy()
        super.onDestroy()
    }

    @Composable
    private fun BspSmsRoot() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repo = remember { AuthRepository(context) }
        val deviceId = remember { resolveStableDeviceId(context) }
        val automationController = remember {
            MarksheetAutomationController(context.applicationContext).apply {
                attachPortalWebViewProvider { portalWebView }
            }
        }

        var locale by remember { mutableStateOf(AppLocale.EN) }
        var session by remember { mutableStateOf(repo.loadSavedSession()) }
        var appConfig by remember { mutableStateOf(repo.loadSavedAppConfig()) }
        var updatePromptDismissed by remember { mutableStateOf(false) }
        var blockedReason by remember { mutableStateOf<String?>(null) }
        var terminationBannerMessage by remember { mutableStateOf<String?>(null) }
        var isCheckingSession by remember { mutableStateOf(false) }
        var isVerifyInFlight by remember { mutableStateOf(false) }
        var lastVerifiedAtMs by remember { mutableLongStateOf(0L) }
        var isLifecycleResumed by remember { mutableStateOf(true) }
        var loginEpoch by remember { mutableIntStateOf(0) }
        var adminResetEpoch by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            val cfgRes = repo.fetchAppConfig()
            cfgRes.appConfig?.let {
                appConfig = it
                repo.saveAppConfig(it)
            }
        }

        fun terminateActiveSessionToLogin(current: AuthSession, reason: String) {
            if (current.isAdmin) return
            automationController.stopBulk()
            automationController.stopResult()
            activeBridge?.stopNativeSpeech()
            val oldWebView = portalWebView
            val oldPopup = popupPortalWebView
            val oldProfile = activePortalProfileName ?: current.portalProfileName
            activePortalProfileName = null
            portalWebView = null
            popupPortalWebView = null
            destroyTemporaryBackgroundWebView()
            oldPopup?.stopLoading()
            runCatching {
                (oldPopup?.parent as? ViewGroup)?.removeView(oldPopup)
                oldPopup?.destroy()
            }
            oldWebView?.stopLoading()
            clearPortalDataForSession(
                context = context,
                profileName = oldProfile,
                webView = oldWebView,
                clearDefaultStoreAlso = true
            ) {
                runCatching { oldWebView?.destroy() }
            }
            toolWebView?.stopLoading()
            runCatching { toolWebView?.destroy() }
            toolWebView = null
            repo.clearSession()
            blockedReason = null
            adminResetEpoch = 0
            isVerifyInFlight = false
            isCheckingSession = false
            terminationBannerMessage = reason
            session = null
            scope.launch {
                repo.logout(current)
            }
        }

        fun verifyActiveSession(current: AuthSession, force: Boolean = false) {
            if (current.isAdmin) {
                blockedReason = null
                return
            }
            if (current.expiryDate.isNotBlank() && isExpiryDatePassed(current.expiryDate)) {
                val expiredReason = if (locale == AppLocale.BN) {
                    "আপনার ১ মাসের (৩০ দিনের) সাবস্ক্রিপশনের মেয়াদ (${current.expiryDate}) শেষ হয়েছে। রিনিউ করতে অ্যাডমিনের সাথে যোগাযোগ করুন।"
                } else {
                    "Your 1-month (30-day) subscription expired on ${current.expiryDate}. Please contact Admin to renew/activate your account."
                }
                terminateActiveSessionToLogin(current, expiredReason)
                return
            }
            if (isVerifyInFlight) return
            val nowMs = SystemClock.elapsedRealtime()
            if (!force && lastVerifiedAtMs > 0L && (nowMs - lastVerifiedAtMs) < 12_000L) {
                return
            }

            isVerifyInFlight = true
            isCheckingSession = true
            scope.launch {
                val res = repo.checkSession(current.userId, deviceId, current.sessionToken)
                isVerifyInFlight = false
                isCheckingSession = false
                lastVerifiedAtMs = SystemClock.elapsedRealtime()

                val latestSession = session
                if (latestSession == null || latestSession.isAdmin || latestSession.userId != current.userId) {
                    return@launch
                }

                val returnedUser = res.user ?: res.users.singleOrNull()
                val isUnsupportedAction = res.code == "UNKNOWN_ACTION" ||
                    res.message.contains("unsupported action", ignoreCase = true) ||
                    res.message.contains("unknown action", ignoreCase = true) ||
                    res.message.contains("invalid action", ignoreCase = true)
                val isExplicitAccountDenial = res.code in setOf("INACTIVE", "EXPIRED", "DEVICE_LOCKED", "SESSION_REVOKED", "NOT_FOUND", "INVALID_SESSION") ||
                    res.message.contains("inactive", ignoreCase = true) ||
                    res.message.contains("deactivated", ignoreCase = true) ||
                    res.message.contains("disabled", ignoreCase = true) ||
                    res.message.contains("blocked", ignoreCase = true) ||
                    res.message.contains("expired", ignoreCase = true) ||
                    res.message.contains("another device", ignoreCase = true) ||
                    res.message.contains("device lock", ignoreCase = true) ||
                    res.message.contains("mismatch", ignoreCase = true) ||
                    res.message.contains("revoked", ignoreCase = true) ||
                    res.message.contains("not found", ignoreCase = true) ||
                    res.message.contains("no longer exists", ignoreCase = true)
                val serverDenied = !res.ok && !isUnsupportedAction && isExplicitAccountDenial
                val userInvalid = returnedUser != null && (
                    (returnedUser.hasStatusField && !returnedUser.isActive) ||
                        (returnedUser.hasExpiryField && (returnedUser.expiryDate.isBlank() || returnedUser.isExpired)) ||
                        (returnedUser.activeDeviceId.isNotBlank() && returnedUser.activeDeviceId != deviceId)
                    )

                if (serverDenied || userInvalid) {
                    val reason = when {
                        res.message.isNotBlank() &&
                            !res.message.equals("Session valid.", ignoreCase = true) &&
                            !res.message.equals("Done", ignoreCase = true) -> res.message
                        returnedUser != null && returnedUser.hasStatusField && !returnedUser.isActive ->
                            "Your account (${returnedUser.userId.ifBlank { current.userId }}) is inactive. Contact Admin for 30-day activation."
                        returnedUser != null && returnedUser.hasExpiryField && (returnedUser.expiryDate.isBlank() || returnedUser.isExpired) ->
                            "Your 1-month (30-day) subscription expired on ${returnedUser.expiryDate.ifBlank { "N/A" }}. Contact Admin to renew."
                        returnedUser != null && returnedUser.activeDeviceId.isNotBlank() && returnedUser.activeDeviceId != deviceId ->
                            "Device lock mismatch or reset by Admin. Please sign in again."
                        else -> "Your session is inactive or expired. Please contact Admin and sign in again."
                    }
                    terminateActiveSessionToLogin(current, reason)
                } else if (res.ok) {
                    res.appConfig?.let {
                        appConfig = it
                        repo.saveAppConfig(it)
                    }
                    val latestPerms = res.toolPermissions ?: returnedUser?.toolPermissions
                    if (returnedUser != null || latestPerms != null) {
                        if (returnedUser != null) {
                            val ackVersion = repo.getAckPortalResetVersion(current.portalOwnerKey)
                            if (ackVersion >= 0 && returnedUser.portalResetVersion > ackVersion) {
                                repo.setAckPortalResetVersion(current.portalOwnerKey, returnedUser.portalResetVersion)
                                adminResetEpoch++
                            }
                        }
                        val nextPerms = latestPerms ?: current.toolPermissions
                        val userChanged = returnedUser != null && (
                            returnedUser.expiryDate != current.expiryDate ||
                                (returnedUser.phone.isNotBlank() && returnedUser.phone != current.phone) ||
                                (returnedUser.fullName.isNotBlank() && returnedUser.fullName != current.fullName) ||
                                (returnedUser.username.isNotBlank() && returnedUser.username != current.username)
                            )
                        if (userChanged || nextPerms != current.toolPermissions) {
                            val updatedSession = current.copy(
                                expiryDate = returnedUser?.expiryDate?.ifBlank { current.expiryDate } ?: current.expiryDate,
                                phone = returnedUser?.phone?.ifBlank { current.phone } ?: current.phone,
                                fullName = returnedUser?.fullName?.ifBlank { current.fullName } ?: current.fullName,
                                username = returnedUser?.username?.ifBlank { current.username } ?: current.username,
                                role = returnedUser?.role?.ifBlank { current.role } ?: current.role,
                                toolPermissions = nextPerms
                            )
                            repo.saveSession(updatedSession)
                            session = updatedSession
                        }
                    }
                    blockedReason = null
                }
            }
        }

        LaunchedEffect(session?.userId, session?.isAdmin, isLifecycleResumed) {
            val current = session
            if (current != null && !current.isAdmin && isLifecycleResumed) {
                verifyActiveSession(current, force = true)
                while (isLifecycleResumed) {
                    delay(40_000L)
                    val latest = session ?: break
                    if (latest.isAdmin || latest.userId != current.userId || !isLifecycleResumed) break
                    verifyActiveSession(latest, force = true)
                }
            } else if (current == null || current.isAdmin) {
                blockedReason = null
            }
        }

        DisposableEffect(session?.userId, session?.isAdmin) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isLifecycleResumed = true
                        val current = session
                        if (current != null && !current.isAdmin) {
                            verifyActiveSession(current, force = true)
                        }
                    }
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP -> {
                        isLifecycleResumed = false
                    }
                    else -> Unit
                }
            }
            this@MainActivity.lifecycle.addObserver(observer)
            onDispose {
                this@MainActivity.lifecycle.removeObserver(observer)
            }
        }

        fun performAppSignOut(current: AuthSession) {
            automationController.stopBulk()
            automationController.stopResult()
            activeBridge?.stopNativeSpeech()
            val oldWebView = portalWebView
            val oldPopup = popupPortalWebView
            val oldProfile = activePortalProfileName ?: current.portalProfileName
            activePortalProfileName = null
            portalWebView = null
            popupPortalWebView = null
            destroyTemporaryBackgroundWebView()
            oldPopup?.stopLoading()
            runCatching {
                (oldPopup?.parent as? ViewGroup)?.removeView(oldPopup)
                oldPopup?.destroy()
            }
            oldWebView?.stopLoading()
            clearPortalDataForSession(
                context = context,
                profileName = oldProfile,
                webView = oldWebView,
                clearDefaultStoreAlso = true
            ) {
                runCatching { oldWebView?.destroy() }
            }
            toolWebView?.stopLoading()
            runCatching { toolWebView?.destroy() }
            toolWebView = null
            repo.clearSession()
            blockedReason = null
            terminationBannerMessage = null
            adminResetEpoch = 0
            session = null
            scope.launch {
                repo.logout(current)
            }
        }

        val currentSession = session
        when {
            currentSession == null -> {
                AuthGateScreen(
                    repo = repo,
                    deviceId = deviceId,
                    locale = locale,
                    initialTab = AuthTab.GENERAL_LOGIN,
                    initialErrorMessage = terminationBannerMessage,
                    onClearInitialError = { terminationBannerMessage = null },
                    onChangeLocale = { locale = it },
                    onAuthenticated = { newSession ->
                        blockedReason = null
                        terminationBannerMessage = null
                        adminResetEpoch = 0
                        lastVerifiedAtMs = SystemClock.elapsedRealtime()
                        activePortalProfileName = newSession.portalProfileName
                        popupPortalWebView?.stopLoading()
                        runCatching {
                            (popupPortalWebView?.parent as? ViewGroup)?.removeView(popupPortalWebView)
                            popupPortalWebView?.destroy()
                        }
                        popupPortalWebView = null
                        destroyTemporaryBackgroundWebView()
                        portalWebView?.stopLoading()
                        runCatching { portalWebView?.destroy() }
                        portalWebView = null
                        loginEpoch++
                        appConfig = repo.loadSavedAppConfig()
                        session = newSession
                    }
                )
            }
            !currentSession.isAdmin && blockedReason != null -> {
                SubscriptionBlockedScreen(
                    session = currentSession,
                    reasonMessage = blockedReason!!,
                    locale = locale,
                    isChecking = isCheckingSession,
                    onRecheck = { verifyActiveSession(currentSession, force = true) },
                    onSignOut = { performAppSignOut(currentSession) }
                )
            }
            else -> {
                BspSmsApp(
                    session = currentSession,
                    appConfig = appConfig,
                    loginEpoch = loginEpoch,
                    adminResetEpoch = adminResetEpoch,
                    repo = repo,
                    deviceId = deviceId,
                    locale = locale,
                    automationController = automationController,
                    onChangeLocale = { locale = it },
                    onUpdateToolPermissions = { nextPerms ->
                        session?.let { latest ->
                            if (!latest.isAdmin && latest.toolPermissions != nextPerms) {
                                val updated = latest.copy(toolPermissions = nextPerms)
                                repo.saveSession(updated)
                                session = updated
                            }
                        }
                    },
                    onVerifySession = { force ->
                        session?.let { latest ->
                            verifyActiveSession(latest, force = force)
                        }
                    },
                    onSignOut = { performAppSignOut(currentSession) }
                )
            }
        }

        val requiresMinUpdate = appConfig.minimumVersionCode > BuildConfig.VERSION_CODE
        val hasNewerName = appConfig.latestVersionName.isNotBlank() &&
            !appConfig.latestVersionName.equals(BuildConfig.VERSION_NAME, ignoreCase = true)
        val isMandatoryUpdate = requiresMinUpdate || appConfig.forceUpdate
        val showUpdateDialog = appConfig.apkDownloadUrl.isNotBlank() &&
            (requiresMinUpdate || (hasNewerName && (appConfig.forceUpdate || !updatePromptDismissed)))

        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isMandatoryUpdate) {
                        updatePromptDismissed = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706)
                    )
                },
                title = {
                    Text(
                        text = if (locale == AppLocale.BN) {
                            "নতুন অ্যাপ আপডেট উপলব্ধ (${appConfig.latestVersionName})"
                        } else {
                            "App Update Available (${appConfig.latestVersionName})"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    val msg = appConfig.updateMessage.ifBlank {
                        if (isMandatoryUpdate) {
                            "Please update BSP-SMS to version ${appConfig.latestVersionName} to continue."
                        } else {
                            "A newer version (${appConfig.latestVersionName}) of BSP-SMS is available."
                        }
                    }
                    Text(msg, fontSize = 13.sp, color = Color(0xFF334155))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(appConfig.apkDownloadUrl))
                                )
                            }.onFailure {
                                Toast.makeText(context, "Invalid APK download link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70))
                    ) {
                        Text(if (locale == AppLocale.BN) "আপডেট ডাউনলোড করুন" else "Download Update")
                    }
                },
                dismissButton = if (!isMandatoryUpdate) {
                    {
                        TextButton(onClick = { updatePromptDismissed = true }) {
                            Text(if (locale == AppLocale.BN) "পরে" else "Later")
                        }
                    }
                } else {
                    null
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BspSmsApp(
        session: AuthSession,
        appConfig: AppConfigData,
        loginEpoch: Int,
        adminResetEpoch: Int,
        repo: AuthRepository,
        deviceId: String,
        locale: AppLocale,
        automationController: MarksheetAutomationController,
        onChangeLocale: (AppLocale) -> Unit,
        onUpdateToolPermissions: (ToolPermissions) -> Unit,
        onVerifySession: (Boolean) -> Unit,
        onSignOut: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        var currentScreen by remember(session.isAdmin, loginEpoch) {
            mutableStateOf<NavScreen>(if (session.isAdmin) NavScreen.AdminPanel else NavScreen.Portal)
        }
        var openedToolFromHub by remember { mutableStateOf(false) }
        var isOnMarksheetSubpage by remember { mutableStateOf(false) }
        var portalProgress by remember { mutableIntStateOf(100) }
        var portalPageTitle by remember { mutableStateOf("Banglarshiksha SMS") }
        var portalErrorState by remember { mutableStateOf<PortalErrorState?>(null) }
        var portalInstanceEpoch by remember { mutableIntStateOf(0) }
        var showPortalMenu by remember { mutableStateOf(false) }
        var popupWebViewState by remember { mutableStateOf<WebView?>(null) }
        var popupPageTitle by remember { mutableStateOf("Marksheet Preview / Print") }
        var popupProgress by remember { mutableIntStateOf(0) }
        var popupErrorBanner by remember { mutableStateOf<String?>(null) }
        var lastReportedPortalActive by remember(session.portalOwnerKey, loginEpoch) {
            mutableStateOf<Boolean?>(null)
        }
        val bulkState by automationController.bulkState.collectAsState()
        val resultState by automationController.resultState.collectAsState()

        fun closePopupWebView() {
            val currentPopup = popupPortalWebView
            popupPortalWebView = null
            popupWebViewState = null
            popupProgress = 0
            popupErrorBanner = null
            popupPageTitle = "Marksheet Preview / Print"
            if (currentPopup != null) {
                runCatching {
                    currentPopup.stopLoading()
                    (currentPopup.parent as? ViewGroup)?.removeView(currentPopup)
                    currentPopup.destroy()
                }
            }
        }

        val profileName = session.portalProfileName
        val ownerKey = session.portalOwnerKey

        // Force complete portal WebView reset whenever:
        // 1) Any user (General or Admin) freshly logs in (loginEpoch > 0), OR
        // 2) User manually taps "Logout / Clear Portal Session" (portalInstanceEpoch > 0), OR
        // 3) Admin forced logout / reset this user's portal session (adminResetEpoch > 0), OR
        // 4) First time this user opens the portal on this device (ackPortalResetVersion < 0), OR
        // 5) Account owner changed on this device.
        // Reopening the app as the same already-logged-in user (loginEpoch == 0) keeps their own portal session intact!
        val needsInitialOrForcedWipe = remember(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
            val isFirstTimeForOwner = repo.getAckPortalResetVersion(ownerKey) < 0
            val switchedOwner = repo.getLastPortalOwner() != ownerKey
            loginEpoch > 0 || portalInstanceEpoch > 0 || adminResetEpoch > 0 || isFirstTimeForOwner || switchedOwner
        }

        var requireFreshSignIn by remember(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
            mutableStateOf(needsInitialOrForcedWipe)
        }
        var isPortalSessionReady by remember(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
            mutableStateOf(!needsInitialOrForcedWipe)
        }

        DisposableEffect(profileName) {
            activePortalProfileName = profileName
            automationController.cookieResolver = { url ->
                runCatching { resolveProfileCookieManager(profileName).getCookie(url) }.getOrNull()
            }
            onDispose {
                destroyTemporaryBackgroundWebView()
                closePopupWebView()
            }
        }

        LaunchedEffect(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
            repo.setLastPortalOwner(ownerKey)
            if (!session.isAdmin) {
                onVerifySession(true)
            }
            if (!isPortalSessionReady) {
                closePopupWebView()
                portalErrorState = null
                destroyTemporaryBackgroundWebView()
                val oldWebView = portalWebView
                portalWebView = null
                oldWebView?.stopLoading()
                clearPortalDataForSession(
                    context = context,
                    profileName = profileName,
                    webView = oldWebView,
                    clearDefaultStoreAlso = true
                ) {
                    runCatching { oldWebView?.destroy() }
                    if (repo.getAckPortalResetVersion(ownerKey) < 0) {
                        repo.setAckPortalResetVersion(ownerKey, 0)
                    }
                    if (!session.isAdmin && lastReportedPortalActive != false) {
                        lastReportedPortalActive = false
                        scope.launch {
                            repo.reportPortalState(session.userId, deviceId, false, "Portal session reset")
                        }
                    }
                    requireFreshSignIn = true
                    isPortalSessionReady = true
                }
            }
        }

        fun resetToFreshPortalLogin() {
            closePopupWebView()
            if (!session.isAdmin) {
                onVerifySession(true)
            }
            currentScreen = NavScreen.Portal
            portalErrorState = null
            showPortalMenu = false
            requireFreshSignIn = true
            isPortalSessionReady = false
            portalInstanceEpoch++
        }

        val effectivePortalUrl = appConfig.portalUrl.trim()
            .takeIf { it.startsWith("http", ignoreCase = true) }
            ?: PORTAL_URL
        var toolDeniedNotice by remember { mutableStateOf<String?>(null) }
        val latestSession by rememberUpdatedState(session)
        val latestLocale by rememberUpdatedState(locale)

        fun tryOpenTool(tool: ToolDestination, fromHub: Boolean) {
            val s = latestSession
            val loc = latestLocale
            val allowedLocal = s.isAdmin || s.toolPermissions.isAllowed(tool.backendKey)
            if (!allowedLocal) {
                toolDeniedNotice = if (loc == AppLocale.BN) {
                    "${tool.titleBn} বর্তমানে আপনার অ্যাকাউন্টের জন্য অ্যাডমিন দ্বারা বন্ধ রাখা হয়েছে।"
                } else {
                    "${tool.titleEn} is currently disabled by Admin for your account. Please contact Admin."
                }
                return
            }
            openedToolFromHub = fromHub
            currentScreen = NavScreen.Tool(tool)
        }

        LaunchedEffect(currentScreen, session.userId) {
            if (!session.isAdmin) {
                onVerifySession(false)
                val currentTool = (currentScreen as? NavScreen.Tool)?.tool
                val activityLabel = when (val s = currentScreen) {
                    NavScreen.Portal -> "Opened SMS Portal"
                    NavScreen.Hub -> "Opened Tools Hub"
                    NavScreen.AdminPanel -> "Admin Panel"
                    is NavScreen.Tool -> "Opened ${s.tool.titleEn}"
                }
                repo.updateActivity(
                    userId = session.userId,
                    deviceId = deviceId,
                    activity = activityLabel,
                    toolKey = currentTool?.backendKey.orEmpty()
                )
                if (currentTool != null) {
                    val accessRes = repo.verifyToolAccess(
                        userId = session.userId,
                        deviceId = deviceId,
                        sessionToken = session.sessionToken,
                        toolKey = currentTool.backendKey
                    )
                    accessRes.toolPermissions?.let { onUpdateToolPermissions(it) }
                    val isUnsupported = accessRes.code == "UNKNOWN_ACTION" ||
                        accessRes.message.contains("unsupported action", ignoreCase = true) ||
                        accessRes.message.contains("unknown action", ignoreCase = true)
                    val explicitlyDenied = (!accessRes.ok && !isUnsupported &&
                        (accessRes.code == "TOOL_DISABLED" || accessRes.message.contains("disabled", ignoreCase = true))) ||
                        (accessRes.toolPermissions != null && !accessRes.toolPermissions.isAllowed(currentTool.backendKey))
                    if (explicitlyDenied) {
                        if (currentTool == ToolDestination.BULK_MARKSHEET) automationController.stopBulk()
                        if (currentTool == ToolDestination.RESULT_READY) automationController.stopResult()
                        toolDeniedNotice = accessRes.message.ifBlank {
                            "${currentTool.titleEn} is disabled by Admin for your account."
                        }
                        currentScreen = NavScreen.Portal
                    }
                }
            }
        }

        var audioPermissionLauncherRef: androidx.activity.result.ActivityResultLauncher<String>? = null

        val bridge = remember {
            ChromeCompatBridge(
                context = context,
                portalWebViewProvider = { portalWebView },
                toolWebViewProvider = { toolWebView },
                onRequestRecordAudio = { cb ->
                    pendingSpeechCallback = cb
                    audioPermissionLauncherRef?.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCheckToolAllowed = { toolKey ->
                    val s = latestSession
                    s.isAdmin || s.toolPermissions.isAllowed(toolKey)
                },
                onHubAction = { action ->
                    when {
                        action == "BSP_MINIMIZE" || action == "BSP_CLOSE" -> currentScreen = NavScreen.Portal
                        action == "BSP_OPEN_BULK_MARKSHEET" -> tryOpenTool(ToolDestination.BULK_MARKSHEET, fromHub = true)
                        action == "BSP_OPEN_RESULT_READY" -> tryOpenTool(ToolDestination.RESULT_READY, fromHub = true)
                        action == "BSP_OPEN_HPRC_SCAN" -> tryOpenTool(ToolDestination.HPRC_SCAN, fromHub = true)
                        action.startsWith("BSP_TOOL_DENIED:") -> {
                            val label = action.removePrefix("BSP_TOOL_DENIED:").ifBlank { "This tool" }
                            toolDeniedNotice = "$label is currently disabled by Admin for your account."
                        }
                    }
                },
                onBeforeToolAction = {
                    if (!latestSession.isAdmin) {
                        onVerifySession(false)
                    }
                },
                onPortalStatusReported = { status, detailArg ->
                    when (status) {
                        "RATE_LIMITED" -> {
                            val waitSec = detailArg.toIntOrNull()?.coerceIn(5, 120) ?: 30
                            portalErrorState = PortalErrorState(
                                title = "Too Many Failed Attempts",
                                detail = "Banglarshiksha requires a ${waitSec}-second cooldown before trying again. Do not reload or submit during the countdown.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.RATE_LIMITED,
                                failedUrl = effectivePortalUrl,
                                canRefreshCaptcha = false,
                                waitSeconds = waitSec
                            )
                        }
                        "SERVER_ERROR" -> {
                            portalErrorState = PortalErrorState(
                                title = "Portal Server Error",
                                detail = "Portal reported 'Server error. Please try again after sometime.' Session cookies are preserved — tap Refresh Captcha or Reload Login.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.SERVER_ERROR,
                                failedUrl = portalWebView?.url,
                                canRefreshCaptcha = true
                            )
                        }
                        "INVALID_CAPTCHA" -> {
                            portalErrorState = PortalErrorState(
                                title = "Invalid Captcha",
                                detail = "Captcha is case-sensitive and tied to the latest page load. Tap Refresh Captcha to get a matching captcha in this session, then submit again.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.INVALID_CAPTCHA,
                                failedUrl = portalWebView?.url,
                                canRefreshCaptcha = true
                            )
                        }
                    }
                },
                onBackgroundTargetCaptured = { targetJson ->
                    automationController.onCapturedBackgroundTarget(targetJson)
                }
            ).also { activeBridge = it }
        }

        SideEffect {
            automationController.attachPortalWebViewProvider { portalWebView }
            automationController.attachBackgroundWebViewProvider { bgMarksheetWebView }
            automationController.attachBackgroundWebViewLifecycle(
                creator = {
                    destroyTemporaryBackgroundWebView()
                    flushPortalCookies(profileName)
                    val host = bgMarksheetHost
                    val webCtx = host?.context ?: portalWebView?.context ?: context
                    createBackgroundMarksheetWebView(
                        ctx = webCtx,
                        profileName = profileName,
                        bridge = bridge,
                        automationController = automationController
                    ).also { created ->
                        bgMarksheetWebView = created
                        runCatching { created.onResume() }
                        host?.addView(
                            created,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                destroyer = { target ->
                    destroyTemporaryBackgroundWebView(target)
                }
            )
            automationController.attachPopupWebViewHandlers(
                popupProvider = { popupPortalWebView },
                closePopupAction = { closePopupWebView() }
            )
            automationController.cookieFlusher = { flushPortalCookies(session.portalProfileName) }
        }

        val assetLoader = remember {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

        val fileChooserLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return@rememberLauncherForActivityResult

            if (result.resultCode == RESULT_OK) {
                val uris = mutableListOf<Uri>()
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                } else {
                    data?.data?.let { uris.add(it) }
                }
                callback.onReceiveValue(uris.toTypedArray())
            } else {
                callback.onReceiveValue(null)
            }
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val req = pendingAudioRequest
            pendingAudioRequest = null
            if (req != null) {
                if (granted) {
                    req.grant(req.resources)
                } else {
                    req.deny()
                }
            }
            val speechCb = pendingSpeechCallback
            pendingSpeechCallback = null
            speechCb?.invoke(granted)
        }
        audioPermissionLauncherRef = audioPermissionLauncher

        val safFolderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { treeUri ->
            if (treeUri != null) {
                automationController.onSafFolderSelected(treeUri)
            }
        }

        var pendingExcelFileName by remember { mutableStateOf("Result_Ready.xlsx") }
        val createExcelLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { targetUri ->
            if (targetUri != null) {
                automationController.exportResultWorkbookToExplicitUri(targetUri, pendingExcelFileName)
            }
        }

        var pendingCameraCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            pendingCameraCallback?.invoke(granted)
            pendingCameraCallback = null
        }

        var pendingHprcExcelFileName by remember { mutableStateOf("HPRC_Scanned.xlsx") }
        var pendingHprcStudents by remember { mutableStateOf<List<HprcStudentScanRecord>>(emptyList()) }
        val createHprcExcelLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { targetUri ->
            if (targetUri != null) {
                val snapshot = pendingHprcStudents
                val res = SafStorageHelper.writeToExplicitUri(context, targetUri) { out ->
                    HprcWorkbookExporter.writeWorkbook(snapshot, out)
                }
                res.onSuccess {
                    Toast.makeText(context, "Saved $pendingHprcExcelFileName", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        fun handlePortalBackNavigation() {
            val wv = portalWebView ?: return
            wv.evaluateJavascript(PortalHrpcDomScripts.tryCloseInPageMarksheetOrModalJs()) { raw ->
                val obj = runCatching {
                    val decoded = if (raw != null && raw.startsWith("\"")) {
                        org.json.JSONArray("[$raw]").getString(0)
                    } else {
                        raw.orEmpty()
                    }
                    org.json.JSONObject(decoded)
                }.getOrNull()

                if (obj?.optBoolean("handled", false) == true) {
                    isOnMarksheetSubpage = false
                    return@evaluateJavascript
                }
                val onMarksheetView = obj?.optBoolean("onMarksheetView", false) == true
                if (onMarksheetView) {
                    automationController.pendingHrpcRestoreFromBack = true
                    isOnMarksheetSubpage = false
                    if (wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        val snapStr = automationController.getLastHrpcSnapshot()
                        val snapUrl = runCatching {
                            org.json.JSONObject(snapStr ?: "{}").optString("url", "")
                        }.getOrDefault("")
                        if (snapUrl.startsWith("http", ignoreCase = true)) {
                            wv.loadUrl(snapUrl)
                        }
                    }
                    return@evaluateJavascript
                }
                if (wv.canGoBack()) {
                    automationController.pendingHrpcRestoreFromBack = true
                    wv.goBack()
                } else {
                    isOnMarksheetSubpage = false
                }
            }
        }

        BackHandler(
            enabled = drawerState.isOpen ||
                currentScreen != NavScreen.Portal ||
                popupWebViewState != null ||
                (portalWebView?.canGoBack() == true) ||
                isOnMarksheetSubpage
        ) {
            when {
                drawerState.isOpen -> scope.launch { drawerState.close() }
                currentScreen is NavScreen.Tool -> {
                    currentScreen = if (openedToolFromHub) NavScreen.Hub else NavScreen.Portal
                    openedToolFromHub = false
                }
                currentScreen == NavScreen.Hub -> {
                    val tv = toolWebView
                    if (tv != null) {
                        tv.evaluateJavascript(
                            "(function(){var v=document.getElementById('toolView');if(v&&!v.hidden&&typeof back==='function'){back();return 'BACK_INTERNAL';}return 'LEAVE_HUB';})();"
                        ) { res ->
                            if (res?.contains("BACK_INTERNAL") != true) {
                                currentScreen = NavScreen.Portal
                            }
                        }
                    } else {
                        currentScreen = NavScreen.Portal
                    }
                }
                currentScreen != NavScreen.Portal -> currentScreen = NavScreen.Portal
                popupWebViewState != null -> {
                    val pv = popupWebViewState
                    if (pv != null && pv.canGoBack()) {
                        pv.goBack()
                    } else {
                        closePopupWebView()
                    }
                }
                else -> handlePortalBackNavigation()
            }
        }

        val primaryBlue = Color(0xFF123B70)
        val saffronStart = Color(0xFFFF9933)
        val saffronEnd = Color(0xFFF57C00)
        val screenTitle = when (val s = currentScreen) {
            NavScreen.Portal -> if (locale == AppLocale.BN) "এসএমএস পোর্টাল" else "SMS Portal"
            NavScreen.Hub -> if (locale == AppLocale.BN) "টুলস হাব" else "Tools Hub"
            NavScreen.AdminPanel -> if (locale == AppLocale.BN) "অ্যাডমিন মনিটর" else "Admin Monitor"
            is NavScreen.Tool -> if (locale == AppLocale.BN) s.tool.titleBn else s.tool.titleEn
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentScreen != NavScreen.Portal || drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(296.dp),
                    drawerContainerColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(primaryBlue)
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            brush = Brush.linearGradient(listOf(saffronStart, saffronEnd)),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "PB",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            lineHeight = 14.sp
                                        )
                                        Text(
                                            text = "SMS",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (session.isAdmin) {
                                            "BSP-SMS Admin"
                                        } else {
                                            session.fullName.ifBlank { session.username.ifBlank { "BSP-SMS User" } }
                                        },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (session.isAdmin) {
                                            "ID: ${session.userId} · No Expiry"
                                        } else {
                                            val handle = session.username.takeIf { it.isNotBlank() }?.let { "@$it · " } ?: ""
                                            "${handle}${session.userId} · Exp: ${session.expiryDate}"
                                        },
                                        color = Color(0xFFD6E6FA),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (locale == AppLocale.BN) "পোর্টাল ও হাব" else "Portal & Workspace",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )

                        NavigationDrawerItem(
                            selected = currentScreen == NavScreen.Portal,
                            onClick = {
                                currentScreen = NavScreen.Portal
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                            label = {
                                Text(if (locale == AppLocale.BN) "বাংলারশিক্ষা এসএমএস পোর্টাল" else "Banglarshiksha SMS Portal")
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFE8F0FE),
                                selectedIconColor = primaryBlue,
                                selectedTextColor = primaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        NavigationDrawerItem(
                            selected = currentScreen == NavScreen.Hub,
                            onClick = {
                                currentScreen = NavScreen.Hub
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            label = {
                                Text(if (locale == AppLocale.BN) "সকল অটোমেশন টুল (হাব)" else "All Automation Tools (Hub)")
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFE8F0FE),
                                selectedIconColor = primaryBlue,
                                selectedTextColor = primaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        NavigationDrawerItem(
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                resetToFreshPortalLogin()
                            },
                            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            label = {
                                Text(
                                    if (locale == AppLocale.BN) {
                                        "নতুন লগইন / পোর্টাল সেশন মুছুন"
                                    } else {
                                        "Fresh Login / Clear Portal Session"
                                    }
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = if (locale == AppLocale.BN) "৮টি অটোমেশন টুলস" else "8 Automation Tools",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )

                        ToolDestination.entries.forEach { tool ->
                            val selected = (currentScreen as? NavScreen.Tool)?.tool == tool
                            val isAllowed = session.isAdmin || session.toolPermissions.isAllowed(tool.backendKey)
                            NavigationDrawerItem(
                                selected = selected,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    tryOpenTool(tool, fromHub = false)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isAllowed) tool.icon else Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = if (isAllowed) Color.Unspecified else Color(0xFF94A3B8)
                                    )
                                },
                                label = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (locale == AppLocale.BN) tool.titleBn else tool.titleEn,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isAllowed) Color.Unspecified else Color(0xFF94A3B8)
                                            )
                                            if (!isAllowed) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (locale == AppLocale.BN) "(বন্ধ)" else "(Locked)",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFDC2626),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (!isAllowed) {
                                                if (locale == AppLocale.BN) "অ্যাডমিন দ্বারা বন্ধ করা আছে" else "Disabled by Admin"
                                            } else if (locale == AppLocale.BN) {
                                                tool.descBn
                                            } else {
                                                tool.descEn
                                            },
                                            fontSize = 11.sp,
                                            color = if (isAllowed) Color(0xFF64748B) else Color(0xFFB91C1C),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFE8F0FE),
                                    selectedIconColor = primaryBlue,
                                    selectedTextColor = primaryBlue
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        if (session.isAdmin) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = if (locale == AppLocale.BN) "অ্যাডমিন কন্ট্রোল" else "Administration",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                            NavigationDrawerItem(
                                selected = currentScreen == NavScreen.AdminPanel,
                                onClick = {
                                    currentScreen = NavScreen.AdminPanel
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                                label = {
                                    Text(if (locale == AppLocale.BN) "অ্যাডমিন ইউজার মনিটর" else "Admin User Monitor")
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFFFF7ED),
                                    selectedIconColor = saffronEnd,
                                    selectedTextColor = saffronEnd
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider()

                        if (!session.isAdmin) {
                            NavigationDrawerItem(
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    val msg = "Hello Admin, I need support in BSP-SMS.\nUser ID: ${session.userId}\nPhone: ${session.phone}"
                                    ContactAndMessageHelper.openAdminWhatsApp(context, msg)
                                },
                                icon = {
                                    Icon(
                                        Icons.Outlined.SupportAgent,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D)
                                    )
                                },
                                label = {
                                    Column {
                                        Text(
                                            text = if (locale == AppLocale.BN) "অ্যাডমিন যোগাযোগ (WhatsApp)" else "Admin Contact (One-Click)",
                                            color = Color(0xFF15803D),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "9679843774 · Direct WhatsApp / SMS",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        } else {
                            NavigationDrawerItem(
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    currentScreen = NavScreen.AdminPanel
                                },
                                icon = {
                                    Icon(
                                        Icons.Outlined.Chat,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (locale == AppLocale.BN) "এক-ক্লিক মেসেজ (অ্যাডমিন)" else "One-Click Message (Admin)",
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        NavigationDrawerItem(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onSignOut()
                                }
                            },
                            icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = Color(0xFFDC2626)) },
                            label = {
                                Text(
                                    text = if (locale == AppLocale.BN) "সাইন আউট" else "Sign Out",
                                    color = Color(0xFFDC2626)
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 6.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = locale == AppLocale.EN,
                                    onClick = { onChangeLocale(AppLocale.EN) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("English")
                                }
                                SegmentedButton(
                                    selected = locale == AppLocale.BN,
                                    onClick = { onChangeLocale(AppLocale.BN) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("বাংলা")
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = primaryBlue,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        ),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                            }
                        },
                        title = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = screenTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentScreen == NavScreen.Portal) {
                                    Text(
                                        text = "Develop by Pulakesh Bera",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == NavScreen.Portal) {
                                TextButton(
                                    onClick = { resetToFreshPortalLogin() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Logout,
                                        contentDescription = "Logout / Clear Portal Session",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (locale == AppLocale.BN) "লগআউট" else "Clear Portal",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (!session.isAdmin) {
                                            onVerifySession(true)
                                        }
                                        flushPortalCookies(profileName)
                                        val err = portalErrorState
                                        if (err != null && err.canRefreshCaptcha) {
                                            portalErrorState = null
                                            val target = err.failedUrl
                                                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                                                ?: portalWebView?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
                                                ?: effectivePortalUrl
                                            portalWebView?.loadUrl(target)
                                        } else {
                                            portalWebView?.reload()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload portal")
                                }
                                Box {
                                    IconButton(onClick = { showPortalMenu = true }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "Portal session options")
                                    }
                                    DropdownMenu(
                                        expanded = showPortalMenu,
                                        onDismissRequest = { showPortalMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (locale == AppLocale.BN) {
                                                        "পোর্টাল সেশন মুছুন / নতুন লগইন"
                                                    } else {
                                                        "Logout / Clear Portal Session"
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                                            },
                                            onClick = { resetToFreshPortalLogin() }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (locale == AppLocale.BN) "অ্যাপ সাইন আউট" else "Sign Out of App",
                                                    color = Color(0xFFDC2626)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.PowerSettingsNew,
                                                    contentDescription = null,
                                                    tint = Color(0xFFDC2626)
                                                )
                                            },
                                            onClick = {
                                                showPortalMenu = false
                                                onSignOut()
                                            }
                                        )
                                    }
                                }
                            } else {
                                IconButton(onClick = { currentScreen = NavScreen.Portal }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Return to portal")
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Layer 0: Host container where isolated per-student temporary background WebViews mount at full viewport bounds during automation
                    if (isPortalSessionReady) {
                        key(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
                            DisposableEffect(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
                                onDispose {
                                    destroyTemporaryBackgroundWebView()
                                    bgMarksheetHost?.removeAllViews()
                                    bgMarksheetHost = null
                                }
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    FrameLayout(ctx).also { host ->
                                        bgMarksheetHost = host
                                    }
                                }
                            )
                        }
                    }

                    // Layer 1 (Opaque foreground): Keep Portal WebView mounted so login session and HRPC list stay 100% undisturbed
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF8FAFC))
                    ) {
                        if (currentScreen == NavScreen.Portal && portalProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { portalProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF1766A7)
                            )
                        }

                        val isBulkActive = bulkState.jobStatus == AutomationJobStatus.RUNNING || bulkState.jobStatus == AutomationJobStatus.PAUSED
                        val isResultActive = resultState.jobStatus == AutomationJobStatus.RUNNING || resultState.jobStatus == AutomationJobStatus.PAUSED
                        if (currentScreen == NavScreen.Portal && (isBulkActive || isResultActive)) {
                            PortalAutomationHudBar(
                                isBulk = isBulkActive,
                                bulkState = bulkState,
                                resultState = resultState,
                                onPauseResume = {
                                    if (isBulkActive) {
                                        if (bulkState.jobStatus == AutomationJobStatus.PAUSED) {
                                            automationController.resumeBulk()
                                        } else {
                                            automationController.pauseBulk()
                                        }
                                    } else {
                                        if (resultState.jobStatus == AutomationJobStatus.PAUSED) {
                                            automationController.resumeResult()
                                        } else {
                                            automationController.pauseResult()
                                        }
                                    }
                                },
                                onStop = {
                                    if (isBulkActive) automationController.stopBulk() else automationController.stopResult()
                                },
                                onOpenDashboard = {
                                    openedToolFromHub = false
                                    currentScreen = NavScreen.Tool(
                                        if (isBulkActive) ToolDestination.BULK_MARKSHEET else ToolDestination.RESULT_READY
                                    )
                                }
                            )
                        }

                        val activePortalError = portalErrorState
                        if (currentScreen == NavScreen.Portal && activePortalError != null && !activePortalError.isBlockingErrorPage) {
                            PortalErrorBanner(
                                error = activePortalError,
                                locale = locale,
                                onRefreshCaptcha = {
                                    portalErrorState = null
                                    refreshPortalCaptcha(portalWebView, profileName)
                                },
                                onRetry = {
                                    if (!session.isAdmin) {
                                        onVerifySession(true)
                                    }
                                    portalErrorState = null
                                    flushPortalCookies(profileName)
                                    val targetUrl = activePortalError.failedUrl
                                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                                        ?: portalWebView?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
                                        ?: effectivePortalUrl
                                    // Use clean GET navigation so expired POST captcha payloads are not re-submitted
                                    portalWebView?.loadUrl(targetUrl)
                                },
                                onOpenFreshLogin = {
                                    resetToFreshPortalLogin()
                                },
                                onDismiss = { portalErrorState = null }
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isPortalSessionReady) {
                                key(ownerKey, loginEpoch, portalInstanceEpoch, adminResetEpoch) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                            createPortalWebView(
                                                ctx = ctx,
                                                profileName = profileName,
                                                bridge = bridge,
                                                initialPortalUrl = effectivePortalUrl,
                                                requireFreshSignIn = requireFreshSignIn,
                                                onProgress = { portalProgress = it },
                                                onTitle = { portalPageTitle = it },
                                                onPortalError = { portalErrorState = it },
                                                onPortalAuthStateChanged = { isActive ->
                                                    if (!session.isAdmin && lastReportedPortalActive != isActive) {
                                                        lastReportedPortalActive = isActive
                                                        val label = if (isActive) {
                                                            "Signed in to Banglarshiksha Portal"
                                                        } else {
                                                            "Portal Sign-In Page"
                                                        }
                                                        scope.launch {
                                                            repo.reportPortalState(session.userId, deviceId, isActive, label)
                                                        }
                                                    }
                                                },
                                                onOpenFileChooser = { callback, intent ->
                                                    filePathCallback?.onReceiveValue(null)
                                                    filePathCallback = callback
                                                    fileChooserLauncher.launch(intent)
                                                },
                                                onAudioPermission = { request ->
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        ctx,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        request.grant(request.resources)
                                                    } else {
                                                        pendingAudioRequest = request
                                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                },
                                                automationController = automationController,
                                                onMarksheetSubpageChanged = { isSub -> isOnMarksheetSubpage = isSub },
                                                onCreatePopupWindow = { isDialog, resultMsg ->
                                                    attachControlledPopupWindow(
                                                        ctx = ctx,
                                                        profileName = profileName,
                                                        bridge = bridge,
                                                        mainPortalWebView = portalWebView,
                                                        isDialog = isDialog,
                                                        resultMsg = resultMsg,
                                                        automationController = automationController,
                                                        previousPopupProvider = { popupPortalWebView },
                                                        onPopupAttached = { newPopup ->
                                                            popupPortalWebView = newPopup
                                                            popupWebViewState = newPopup
                                                            popupPageTitle = "Marksheet Preview / Print"
                                                            popupProgress = 10
                                                            popupErrorBanner = null
                                                        },
                                                        onPopupProgress = { prog -> popupProgress = prog },
                                                        onPopupTitle = { t ->
                                                            if (t.isNotBlank() && !t.equals("about:blank", ignoreCase = true)) {
                                                                popupPageTitle = t
                                                            }
                                                        },
                                                        onPopupError = { msg -> popupErrorBanner = msg },
                                                        onClosePopup = { closePopupWebView() },
                                                        onOpenFileChooser = { callback, intent ->
                                                            filePathCallback?.onReceiveValue(null)
                                                            filePathCallback = callback
                                                            fileChooserLauncher.launch(intent)
                                                        },
                                                        onAudioPermission = { request ->
                                                            val granted = ContextCompat.checkSelfPermission(
                                                                ctx,
                                                                Manifest.permission.RECORD_AUDIO
                                                            ) == PackageManager.PERMISSION_GRANTED
                                                            if (granted) {
                                                                request.grant(request.resources)
                                                            } else {
                                                                pendingAudioRequest = request
                                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                            }
                                                        }
                                                    )
                                                }
                                            ).also { portalWebView = it }
                                        }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFF8FAFC)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = primaryBlue)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (locale == AppLocale.BN) {
                                                "নতুন ও পরিচ্ছন্ন এসএমএস পোর্টাল সেশন প্রস্তুত হচ্ছে..."
                                            } else {
                                                "Preparing fresh SMS Portal Sign In..."
                                            },
                                            fontSize = 13.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            val activePopup = popupWebViewState
                            if (activePopup != null) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.White
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            if (activePopup.canGoBack()) {
                                                                activePopup.goBack()
                                                            } else {
                                                                closePopupWebView()
                                                            }
                                                        },
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                                            contentDescription = "Back to Student Table",
                                                            tint = primaryBlue,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = popupPageTitle,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = if (locale == AppLocale.BN) {
                                                                "নতুন ট্যাব · নিচের টেবিল সুরক্ষিত আছে"
                                                            } else {
                                                                "Preview / Print Tab · HRPC table preserved"
                                                            },
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF475569),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            popupErrorBanner = null
                                                            val curUrl = activePopup.url
                                                            if (!curUrl.isNullOrBlank() && curUrl.startsWith("http", ignoreCase = true)) {
                                                                activePopup.loadUrl(curUrl)
                                                            } else {
                                                                activePopup.reload()
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Refresh,
                                                            contentDescription = "Reload Tab",
                                                            tint = primaryBlue,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    TextButton(
                                                        onClick = { closePopupWebView() },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (locale == AppLocale.BN) "বন্ধ করুন" else "Back to Table",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFDC2626)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (popupProgress in 1..99) {
                                            LinearProgressIndicator(
                                                progress = { popupProgress / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF1766A7)
                                            )
                                        }

                                        val popErr = popupErrorBanner
                                        if (!popErr.isNullOrBlank()) {
                                            Surface(
                                                color = Color(0xFFFFFBEB),
                                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = popErr,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF92400E),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(onClick = { closePopupWebView() }) {
                                                        Text("Back to Table", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        key(activePopup) {
                                            AndroidView(
                                                modifier = Modifier.fillMaxSize(),
                                                factory = {
                                                    (activePopup.parent as? ViewGroup)?.removeView(activePopup)
                                                    activePopup
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val blockingError = portalErrorState
                            if (currentScreen == NavScreen.Portal && blockingError != null && blockingError.isBlockingErrorPage) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFFF8FAFC)
                                ) {
                                    PortalBlockingRetryCard(
                                        error = blockingError,
                                        locale = locale,
                                        onRetry = {
                                            portalErrorState = null
                                            flushPortalCookies(profileName)
                                            val target = blockingError.failedUrl
                                                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                                            if (target != null) {
                                                portalWebView?.loadUrl(target)
                                            } else {
                                                portalWebView?.reload()
                                            }
                                        },
                                        onOpenFreshLogin = {
                                            resetToFreshPortalLogin()
                                        },
                                        onDismiss = { portalErrorState = null }
                                    )
                                }
                            }
                        }
                    }

                    // Overlay the active Tool / Hub screen cleanly while preserving Portal WebView state underneath
                    if (currentScreen != NavScreen.Portal) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFFF8FAFC)
                        ) {
                            when (val screen = currentScreen) {
                                NavScreen.Hub -> {
                                    ToolWebViewPane(
                                        entryPath = "hub.html",
                                        bridge = bridge,
                                        assetLoader = assetLoader,
                                        locale = locale,
                                        onOpenFileChooser = { callback, intent ->
                                            filePathCallback?.onReceiveValue(null)
                                            filePathCallback = callback
                                            fileChooserLauncher.launch(intent)
                                        },
                                        onAudioPermission = { request ->
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                request.grant(request.resources)
                                            } else {
                                                pendingAudioRequest = request
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        onBackToPortal = { currentScreen = NavScreen.Portal }
                                    )
                                }
                                is NavScreen.Tool -> {
                                    when (screen.tool) {
                                        ToolDestination.BULK_MARKSHEET -> {
                                            BulkMarksheetScreen(
                                                controller = automationController,
                                                locale = locale,
                                                onPickSafFolder = {
                                                    safFolderPickerLauncher.launch(null)
                                                },
                                                onOpenPortal = {
                                                    currentScreen = NavScreen.Portal
                                                }
                                            )
                                        }
                                        ToolDestination.RESULT_READY -> {
                                            ResultReadyScreen(
                                                controller = automationController,
                                                locale = locale,
                                                onPickSafFolder = {
                                                    safFolderPickerLauncher.launch(null)
                                                },
                                                onCreateExcelDocument = { suggested ->
                                                    pendingExcelFileName = suggested
                                                    createExcelLauncher.launch(suggested)
                                                },
                                                onOpenPortal = {
                                                    currentScreen = NavScreen.Portal
                                                }
                                            )
                                        }
                                        ToolDestination.HPRC_SCAN -> {
                                            HprcScanScreen(
                                                locale = locale,
                                                portalWebViewProvider = { portalWebView },
                                                onRequestCameraPermission = { cb ->
                                                    pendingCameraCallback = cb
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                },
                                                onCreateHprcExcel = { suggestedName, snapshot ->
                                                    pendingHprcExcelFileName = suggestedName
                                                    pendingHprcStudents = snapshot
                                                    createHprcExcelLauncher.launch(suggestedName)
                                                },
                                                onOpenPortal = {
                                                    currentScreen = NavScreen.Portal
                                                }
                                            )
                                        }
                                        else -> {
                                            val exists = remember(screen.tool) {
                                                bridge.assetExists(screen.tool.entryPath)
                                            }
                                            if (exists) {
                                                ToolWebViewPane(
                                                    entryPath = screen.tool.entryPath,
                                                    bridge = bridge,
                                                    assetLoader = assetLoader,
                                                    locale = locale,
                                                    onOpenFileChooser = { callback, intent ->
                                                        filePathCallback?.onReceiveValue(null)
                                                        filePathCallback = callback
                                                        fileChooserLauncher.launch(intent)
                                                    },
                                                    onAudioPermission = { request ->
                                                        val granted = ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.RECORD_AUDIO
                                                        ) == PackageManager.PERMISSION_GRANTED
                                                        if (granted) {
                                                            request.grant(request.resources)
                                                        } else {
                                                            pendingAudioRequest = request
                                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                        }
                                                    },
                                                    onBackToPortal = { currentScreen = NavScreen.Portal }
                                                )
                                            } else {
                                                MissingToolAssetNotice(
                                                    tool = screen.tool,
                                                    locale = locale,
                                                    onReturnToPortal = { currentScreen = NavScreen.Portal }
                                                )
                                            }
                                        }
                                    }
                                }
                                NavScreen.AdminPanel -> {
                                    AdminPanelScreen(
                                        repo = repo,
                                        locale = locale
                                    )
                                }
                                NavScreen.Portal -> Unit
                            }
                        }
                    }
                }
            }

            val deniedMsg = toolDeniedNotice
            if (!deniedMsg.isNullOrBlank()) {
                AlertDialog(
                    onDismissRequest = { toolDeniedNotice = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color(0xFFDC2626)
                        )
                    },
                    title = {
                        Text(
                            text = if (locale == AppLocale.BN) "টুল অ্যাক্সেস বন্ধ আছে" else "Tool Access Disabled",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(deniedMsg, fontSize = 13.sp, color = Color(0xFF334155))
                    },
                    confirmButton = {
                        Button(
                            onClick = { toolDeniedNotice = null },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun ToolWebViewPane(
        entryPath: String,
        bridge: ChromeCompatBridge,
        assetLoader: WebViewAssetLoader,
        locale: AppLocale,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit,
        onBackToPortal: () -> Unit
    ) {
        val url = "$ASSET_BASE${entryPath.trimStart('/')}"
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            factory = { ctx ->
                createToolWebView(
                    ctx = ctx,
                    initialUrl = url,
                    bridge = bridge,
                    assetLoader = assetLoader,
                    onOpenFileChooser = onOpenFileChooser,
                    onAudioPermission = onAudioPermission
                ).also { toolWebView = it }
            }
        )
    }

    @Composable
    private fun MissingToolAssetNotice(
        tool: ToolDestination,
        locale: AppLocale,
        onReturnToPortal: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (locale == AppLocale.BN) tool.titleBn else tool.titleEn,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (locale == AppLocale.BN) tool.descBn else tool.descEn,
                fontSize = 14.sp,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (locale == AppLocale.BN) {
                            "এই টুলটির এক্সটেনশন ফোল্ডার এখনো যোগ করা হয়নি:"
                        } else {
                            "This tool's extension subfolder has not been added to assets yet:"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "app/src/main/assets/extension/${tool.entryPath}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF123B70)
                    )
                    if (tool.contentScript != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "app/src/main/assets/extension/${tool.contentScript}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF123B70)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onReturnToPortal) {
                Text(if (locale == AppLocale.BN) "পোর্টালে ফিরে যান" else "Back to Portal")
            }
        }
    }

    private fun refreshPortalCaptcha(webView: WebView?, profileName: String? = null) {
        val activeView = webView ?: return
        flushPortalCookies(profileName)
        activeView.evaluateJavascript(
            """
            (function() {
                var refreshBtn = document.querySelector(
                    'a[onclick*="captcha" i], button[onclick*="captcha" i], [id*="captcha" i][id*="refresh" i], [class*="captcha" i][class*="refresh" i], a[title*="Refresh Captcha" i], button[title*="Refresh Captcha" i], a[aria-label*="captcha" i], button[aria-label*="captcha" i]'
                );
                if (refreshBtn && typeof refreshBtn.click === "function") {
                    refreshBtn.click();
                    return true;
                }
                var img = document.querySelector(
                    'img[src*="captcha" i], img[id*="captcha" i], img[alt*="captcha" i], img[class*="captcha" i]'
                );
                if (img && img.getAttribute("src")) {
                    var base = img.getAttribute("src").replace(/([?&])_bsp_ts=\d+/g, "").replace(/[?&]$/, "");
                    var sep = base.indexOf("?") >= 0 ? "&" : "?";
                    img.src = base + sep + "_bsp_ts=" + Date.now();
                    return true;
                }
                return false;
            })();
            """.trimIndent(),
            null
        )
    }

    @Composable
    private fun PortalErrorBanner(
        error: PortalErrorState,
        locale: AppLocale,
        onRefreshCaptcha: () -> Unit,
        onRetry: () -> Unit,
        onOpenFreshLogin: () -> Unit,
        onDismiss: () -> Unit
    ) {
        var remainingSeconds by remember(error) {
            mutableIntStateOf(
                if (error.kind == PortalErrorKind.RATE_LIMITED) error.waitSeconds.coerceAtLeast(1) else 0
            )
        }

        LaunchedEffect(error) {
            if (error.kind == PortalErrorKind.RATE_LIMITED) {
                while (remainingSeconds > 0) {
                    kotlinx.coroutines.delay(1000L)
                    remainingSeconds--
                }
            }
        }

        val liveDetail = if (error.kind == PortalErrorKind.RATE_LIMITED) {
            if (remainingSeconds > 0) {
                if (locale == AppLocale.BN) {
                    "অনেকবার ভুল চেষ্টার কারণে পোর্টাল লক হয়েছে। ${remainingSeconds} সেকেন্ড অপেক্ষা করুন (সময়ের আগে সাবমিট বা রিলোড করবেন না)।"
                } else {
                    "Too many failed attempts. Wait ${remainingSeconds}s before trying again — reloading or submitting early resets the portal lock."
                }
            } else {
                if (locale == AppLocale.BN) {
                    "অপেক্ষার সময় শেষ হয়েছে। নতুন ক্যাপচা পেতে নিচের 'লগইন রিলোড' বোতামে ট্যাপ করুন।"
                } else {
                    "Cooldown complete. Tap Reload Login to fetch a fresh captcha and sign in."
                }
            }
        } else {
            error.detail
        }

        Surface(
            color = Color(0xFFFFFBEB),
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (error.kind == PortalErrorKind.RATE_LIMITED && remainingSeconds > 0) {
                                "${error.title} (${remainingSeconds}s)"
                            } else {
                                error.title
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = liveDetail,
                    fontSize = 12.sp,
                    color = Color(0xFF78350F)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.kind == PortalErrorKind.RATE_LIMITED) {
                        Button(
                            onClick = onOpenFreshLogin,
                            enabled = remainingSeconds == 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            val label = if (remainingSeconds > 0) {
                                if (locale == AppLocale.BN) "অপেক্ষা করুন (${remainingSeconds}s)" else "Wait ${remainingSeconds}s..."
                            } else {
                                if (locale == AppLocale.BN) "লগইন রিলোড" else "Reload Login (Fresh Captcha)"
                            }
                            Text(label, fontSize = 12.sp)
                        }
                    } else if (error.canRefreshCaptcha) {
                        Button(
                            onClick = onRefreshCaptcha,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (locale == AppLocale.BN) "ক্যাপচা রিফ্রেশ" else "Refresh Captcha", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (locale == AppLocale.BN) "পুনরায় চেষ্টা" else "Retry Page", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (locale == AppLocale.BN) "পুনরায় চেষ্টা" else "Retry", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenFreshLogin,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (locale == AppLocale.BN) "লগইন রিলোড" else "Reload Login", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PortalBlockingRetryCard(
        error: PortalErrorState,
        locale: AppLocale,
        onRetry: () -> Unit,
        onOpenFreshLogin: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error.detail,
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (locale == AppLocale.BN) "পুনরায় চেষ্টা করুন" else "Retry Request")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenFreshLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (locale == AppLocale.BN) "নতুন করে লগইন খুলুন" else "Open Fresh Login Page")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text(if (locale == AppLocale.BN) "পেজ দেখুন" else "View Underlying Page")
                    }
                }
            }
        }
    }

    private fun resolveProfileCookieManager(profileName: String?): CookieManager {
        if (!profileName.isNullOrBlank() && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val cm = runCatching {
                ProfileStore.getInstance().getOrCreateProfile(profileName).cookieManager
            }.getOrNull()
            if (cm != null) return cm
        }
        return CookieManager.getInstance()
    }

    private fun resolveProfileWebStorage(profileName: String?): WebStorage {
        if (!profileName.isNullOrBlank() && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val storage = runCatching {
                ProfileStore.getInstance().getOrCreateProfile(profileName).webStorage
            }.getOrNull()
            if (storage != null) return storage
        }
        return WebStorage.getInstance()
    }

    private fun flushPortalCookies(profileName: String?) {
        runCatching { resolveProfileCookieManager(profileName).flush() }
        runCatching { CookieManager.getInstance().flush() }
    }

    private fun wipeWebViewPortalData(
        context: android.content.Context,
        profileName: String?,
        webView: WebView?
    ) {
        runCatching { resolveProfileWebStorage(profileName).deleteAllData() }
        val usingMultiProfile = !profileName.isNullOrBlank() &&
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        if (!usingMultiProfile) {
            @Suppress("DEPRECATION")
            runCatching {
                val db = WebViewDatabase.getInstance(context)
                db.clearFormData()
                db.clearHttpAuthUsernamePassword()
            }
            runCatching { context.deleteDatabase("webview.db") }
            runCatching { context.deleteDatabase("webviewCache.db") }
        }
        webView?.apply {
            stopLoading()
            clearCache(true)
            clearFormData()
            clearHistory()
            evaluateJavascript(
                """
                (function(){
                    try{localStorage.clear();}catch(e){}
                    try{sessionStorage.clear();}catch(e){}
                    try{if(window.caches&&caches.keys){caches.keys().then(function(k){k.forEach(function(n){caches.delete(n);});});}}catch(e){}
                    try{if(navigator.serviceWorker&&navigator.serviceWorker.getRegistrations){navigator.serviceWorker.getRegistrations().then(function(r){r.forEach(function(reg){reg.unregister();});});}}catch(e){}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private fun clearPortalDataForSession(
        context: android.content.Context,
        profileName: String?,
        webView: WebView? = null,
        clearDefaultStoreAlso: Boolean = false,
        afterCleared: () -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        wipeWebViewPortalData(context, profileName, webView)
        if (clearDefaultStoreAlso) {
            runCatching { WebStorage.getInstance().deleteAllData() }
        }

        val cm = resolveProfileCookieManager(profileName)
        val defaultCm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        defaultCm.setAcceptCookie(true)

        if (clearDefaultStoreAlso && cm !== defaultCm) {
            runCatching {
                defaultCm.removeSessionCookies(null)
                defaultCm.removeAllCookies(null)
                defaultCm.flush()
            }
        }

        cm.removeSessionCookies(null)
        cm.removeAllCookies {
            cm.flush()
            if (clearDefaultStoreAlso && cm !== defaultCm) {
                defaultCm.removeAllCookies {
                    defaultCm.flush()
                    mainHandler.post {
                        wipeWebViewPortalData(context, profileName, webView)
                        runCatching { WebStorage.getInstance().deleteAllData() }
                        if (webView != null) {
                            cm.setAcceptThirdPartyCookies(webView, true)
                        }
                        afterCleared()
                    }
                }
            } else {
                mainHandler.post {
                    wipeWebViewPortalData(context, profileName, webView)
                    if (webView != null) {
                        cm.setAcceptThirdPartyCookies(webView, true)
                    }
                    afterCleared()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPortalWebView(
        ctx: android.content.Context,
        profileName: String,
        bridge: ChromeCompatBridge,
        initialPortalUrl: String = PORTAL_URL,
        requireFreshSignIn: Boolean = true,
        onProgress: (Int) -> Unit,
        onTitle: (String) -> Unit,
        onPortalError: (PortalErrorState?) -> Unit,
        onPortalAuthStateChanged: (Boolean) -> Unit,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit,
        automationController: MarksheetAutomationController? = null,
        onMarksheetSubpageChanged: (Boolean) -> Unit = {},
        onCreatePopupWindow: (Boolean, Message) -> Boolean = { _, _ -> false }
    ): WebView {
        val resolvedPortalUrl = initialPortalUrl.trim()
            .takeIf { it.startsWith("http", ignoreCase = true) }
            ?: PORTAL_URL
        return WebView(ctx).apply {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching { WebViewCompat.setProfile(this, profileName) }
            }

            if (requireFreshSignIn) {
                clearCache(true)
                clearFormData()
                clearHistory()
            }

            val cookieManager = resolveProfileCookieManager(profileName)
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                @Suppress("DEPRECATION")
                saveFormData = false
                cacheMode = if (requireFreshSignIn) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                allowFileAccess = false
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = (userAgentString ?: "")
                    .replace("; wv)", ")")
                    .replace("; wv;", ";")
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                private var lastMainFrameUrl: String = resolvedPortalUrl
                private var sawSignInForm: Boolean = !requireFreshSignIn
                private var autoForcedLogoutCount: Int = 0
                private var pendingLogoutReload: Boolean = false
                private var cacheMissRecoveryCount: Int = 0
                private var lastCacheMissRecoveryAtMs: Long = 0L
                private var dbErrorRecoveryCount: Int = 0

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https") {
                        if (request.isForMainFrame) {
                            val targetUrl = uri.toString()
                            val currentUrl = view?.url.orEmpty()
                            val isLocked = automationController?.isMainTabNavigationLocked() == true
                            val isAuto = automationController?.isAnyAutomationActive() == true
                            if (isLocked || isAuto) {
                                val bgView = automationController?.getBackgroundWebView()
                                if (bgView != null && targetUrl != currentUrl) {
                                    Handler(Looper.getMainLooper()).post {
                                        if (currentUrl.startsWith("http", ignoreCase = true)) {
                                            bgView.loadUrl(targetUrl, mapOf("Referer" to currentUrl))
                                        } else {
                                            bgView.loadUrl(targetUrl)
                                        }
                                    }
                                    return true
                                }
                                if (isLocked) {
                                    return true
                                }
                                val lowerTarget = targetUrl.lowercase()
                                if (lowerTarget.contains("marksheet") ||
                                    lowerTarget.contains("report_card") ||
                                    lowerTarget.contains("evaluation") ||
                                    lowerTarget.contains("print") ||
                                    lowerTarget.contains("preview")
                                ) {
                                    return true
                                }
                            }
                        }
                        return false
                    }
                    if (scheme == "blob" || scheme == "data" || scheme == "about") {
                        return false
                    }
                    return try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) {
                        false
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    cookieManager.flush()
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        lastMainFrameUrl = url
                    }
                    automationController?.clearWebViewTransientError()
                    onPortalError(null)
                    view?.evaluateJavascript(PortalHrpcDomScripts.installSameSessionPopupInterceptorJs(), null)
                    if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                        view?.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    cookieManager.flush()
                    val reqUrl = request?.url?.toString().orEmpty()
                    val errorCode = error?.errorCode ?: 0
                    val desc = error?.description?.toString()?.ifBlank { "Connection timed out" } ?: "Network error"
                    val isCacheMiss = errorCode == -400 ||
                        desc.contains("ERR_CACHE_MISS", ignoreCase = true) ||
                        desc.contains("CACHE_MISS", ignoreCase = true)

                    if (request?.isForMainFrame == true) {
                        if (isCacheMiss) {
                            automationController?.notifyWebViewCacheMiss()
                            onPortalError(null)
                            if (automationController?.isAnyAutomationActive() == true) {
                                // MarksheetAutomationController handles clean GET + filter/page recovery while keeping dataset intact
                                return
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastCacheMissRecoveryAtMs > 6_000L) {
                                cacheMissRecoveryCount = 0
                            }
                            if (cacheMissRecoveryCount < 2 && view != null) {
                                cacheMissRecoveryCount++
                                lastCacheMissRecoveryAtMs = now
                                val snapStr = automationController?.getLastHrpcSnapshot()
                                val snapUrl = runCatching {
                                    org.json.JSONObject(snapStr ?: "{}").optString("url", "")
                                }.getOrDefault("")
                                val validReqUrl = reqUrl.takeIf {
                                    it.startsWith("http", ignoreCase = true) && !it.contains("chromewebdata", ignoreCase = true)
                                }
                                val recoveryUrl = validReqUrl
                                    ?: snapUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                                    ?: lastMainFrameUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                                    ?: resolvedPortalUrl
                                if (!snapStr.isNullOrBlank() && (validReqUrl == null || validReqUrl == snapUrl || recoveryUrl == snapUrl)) {
                                    automationController?.pendingHrpcRestoreFromBack = true
                                }
                                view.post {
                                    view.stopLoading()
                                    view.loadUrl(recoveryUrl)
                                }
                                return
                            }
                        }

                        automationController?.notifyWebViewMainFrameError(desc)
                        onPortalError(
                            PortalErrorState(
                                title = "Portal Connection Error",
                                detail = "Could not complete request ($desc). Your cookies and session state are kept intact — tap Retry Request or Open Fresh Login Page.",
                                isBlockingErrorPage = true,
                                kind = PortalErrorKind.CONNECTION_TIMEOUT,
                                failedUrl = reqUrl.ifBlank { lastMainFrameUrl }
                            )
                        )
                    } else if (reqUrl.contains("captcha", ignoreCase = true)) {
                        onPortalError(
                            PortalErrorState(
                                title = "Captcha Load Error",
                                detail = "Could not load captcha image ($desc). Session cookies are preserved — tap Refresh Captcha to try again.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.INVALID_CAPTCHA,
                                failedUrl = lastMainFrameUrl,
                                canRefreshCaptcha = true
                            )
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val code = errorResponse?.statusCode ?: 0
                    val reqUrl = request?.url?.toString().orEmpty()
                    val isCaptchaReq = reqUrl.contains("captcha", ignoreCase = true)
                    val isStaticAsset = !isCaptchaReq && listOf(".css", ".js", ".woff", ".woff2", ".png", ".jpg", ".svg", ".ico")
                        .any { reqUrl.substringBefore("?").endsWith(it, ignoreCase = true) }
                    if (code == 429 && (request?.isForMainFrame == true || !isStaticAsset)) {
                        cookieManager.flush()
                        onPortalError(
                            PortalErrorState(
                                title = "Too Many Failed Attempts",
                                detail = "Banglarshiksha returned HTTP 429 rate limit. Please wait 30 seconds before trying again.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.RATE_LIMITED,
                                failedUrl = resolvedPortalUrl,
                                canRefreshCaptcha = false,
                                waitSeconds = 30
                            )
                        )
                    } else if (code >= 500 && (request?.isForMainFrame == true || !isStaticAsset)) {
                        cookieManager.flush()
                        onPortalError(
                            PortalErrorState(
                                title = "Portal Server Error (HTTP $code)",
                                detail = "The Banglarshiksha server returned HTTP $code. Session cookies were preserved — tap Refresh Captcha or Retry Page to load a fresh captcha.",
                                isBlockingErrorPage = false,
                                kind = PortalErrorKind.SERVER_ERROR,
                                failedUrl = lastMainFrameUrl,
                                canRefreshCaptcha = true
                            )
                        )
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    cookieManager.flush()
                    onPortalError(
                        PortalErrorState(
                            title = "WebView Renderer Reset",
                            detail = "Android reclaimed WebView memory while idle. Tap Open Fresh Login Page to resume.",
                            isBlockingErrorPage = true,
                            kind = PortalErrorKind.RENDERER_GONE,
                            failedUrl = lastMainFrameUrl
                        )
                    )
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    cookieManager.flush()
                    val activeView = view ?: return
                    if (activeView.settings.cacheMode == WebSettings.LOAD_NO_CACHE) {
                        activeView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // Strictly read-only portal state inspection + passive AJAX error observer;
                    // never modifies captcha inputs, form values, or input event listeners.
                    activeView.evaluateJavascript(
                        """
                        (function() {
                            function detectState() {
                                var txt = (document.body && document.body.innerText) ? document.body.innerText : "";
                                var rateMatch = txt.match(/too\s*many\s*(?:failed\s*)?attempts[\s\S]{0,80}?(?:wait|after|in)\s*(\d+)\s*seconds?/i)
                                    || txt.match(/(?:please\s*wait|try\s*again\s*(?:after|in))\s*(\d+)\s*seconds?/i);
                                if (rateMatch || /too\s*many\s*(?:failed\s*)?attempts/i.test(txt)) {
                                    return { status: "RATE_LIMITED", waitSeconds: rateMatch ? parseInt(rateMatch[1], 10) || 30 : 30 };
                                }
                                if (/server\s*error.*try\s*again/i.test(txt)) {
                                    return { status: "SERVER_ERROR", waitSeconds: 0 };
                                }
                                if (/invalid\s*captcha|captcha\s*(?:mismatch|incorrect|wrong|expired|not\s*match)/i.test(txt)) {
                                    return { status: "INVALID_CAPTCHA", waitSeconds: 0 };
                                }
                                return { status: "OK", waitSeconds: 0 };
                            }
                            var txtAll = (document.body && document.body.innerText) ? document.body.innerText : "";
                            var hasAuthForm = !!document.querySelector(
                                'input[type="password"], input[name*="captcha" i], input[id*="captcha" i], input[name*="dise" i], input[id*="dise" i], img[src*="captcha" i], img[id*="captcha" i]'
                            );
                            var logoutLinkEl = document.querySelector('a[href*="logout" i]');
                            var logoutHref = (logoutLinkEl && logoutLinkEl.href) ? logoutLinkEl.href : "";
                            var looksAuthenticated = !hasAuthForm && (
                                !!logoutHref || /SCHOOL\s*\/\s*HOI\s*DASHBOARD|HOI\s*DASHBOARD/i.test(txtAll)
                            );
                            var detected = detectState();
                            if (hasAuthForm && !window.__bspStatusObserverInstalled && document.body && window.MutationObserver) {
                                window.__bspStatusObserverInstalled = true;
                                var lastReported = detected.status;
                                var timer = null;
                                var obs = new MutationObserver(function() {
                                    clearTimeout(timer);
                                    timer = setTimeout(function() {
                                        var next = detectState();
                                        if (next.status !== "OK" && next.status !== lastReported && window.BspAndroidBridge && window.BspAndroidBridge.notifyPortalStatus) {
                                            lastReported = next.status;
                                            window.BspAndroidBridge.notifyPortalStatus(next.status, String(next.waitSeconds || ""));
                                        } else if (next.status === "OK") {
                                            lastReported = "OK";
                                        }
                                    }, 250);
                                });
                                obs.observe(document.body, { childList: true, subtree: true, characterData: true });
                            }
                            return JSON.stringify({
                                hasAuthForm: hasAuthForm,
                                looksAuthenticated: looksAuthenticated,
                                logoutHref: logoutHref,
                                alreadyCompatInstalled: !!window.__bspChromeCompatInstalled,
                                alreadyInjected: !!window.__bspContentScriptsInjected,
                                status: detected.status,
                                waitSeconds: detected.waitSeconds
                            });
                        })();
                        """.trimIndent()
                    ) { raw ->
                        val parsed = try {
                            org.json.JSONObject(org.json.JSONArray("[$raw]").getString(0))
                        } catch (_: Exception) {
                            null
                        }
                        val hasAuthForm = parsed?.optBoolean("hasAuthForm", false) ?: false
                        val looksAuthenticated = parsed?.optBoolean("looksAuthenticated", false) ?: false
                        val logoutHref = parsed?.optString("logoutHref", "")?.trim().orEmpty()
                        val alreadyCompatInstalled = parsed?.optBoolean("alreadyCompatInstalled", false) ?: false
                        val alreadyInjected = parsed?.optBoolean("alreadyInjected", false) ?: false
                        val status = parsed?.optString("status", "OK") ?: "OK"
                        val waitSeconds = parsed?.optInt("waitSeconds", 30)?.coerceIn(5, 120) ?: 30

                        if (hasAuthForm) {
                            sawSignInForm = true
                            pendingLogoutReload = false
                            onPortalAuthStateChanged(false)
                        } else if (pendingLogoutReload) {
                            pendingLogoutReload = false
                            clearPortalDataForSession(ctx, profileName, activeView) {
                                activeView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                activeView.loadUrl(resolvedPortalUrl, NO_CACHE_HEADERS)
                            }
                            return@evaluateJavascript
                        } else if (requireFreshSignIn && !sawSignInForm && looksAuthenticated && autoForcedLogoutCount < 2) {
                            autoForcedLogoutCount++
                            if (logoutHref.startsWith("http", ignoreCase = true)) {
                                pendingLogoutReload = true
                                activeView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                activeView.loadUrl(logoutHref, NO_CACHE_HEADERS)
                            } else {
                                clearPortalDataForSession(ctx, profileName, activeView) {
                                    activeView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                    activeView.loadUrl(resolvedPortalUrl, NO_CACHE_HEADERS)
                                }
                            }
                            return@evaluateJavascript
                        } else if (looksAuthenticated) {
                            onPortalAuthStateChanged(true)
                        }

                        when (status) {
                            "RATE_LIMITED" -> {
                                onPortalError(
                                    PortalErrorState(
                                        title = "Too Many Failed Attempts",
                                        detail = "Banglarshiksha requires a ${waitSeconds}-second cooldown before trying again. Wait for the countdown to reach 0s, then tap Reload Login.",
                                        isBlockingErrorPage = false,
                                        kind = PortalErrorKind.RATE_LIMITED,
                                        failedUrl = resolvedPortalUrl,
                                        canRefreshCaptcha = false,
                                        waitSeconds = waitSeconds
                                    )
                                )
                            }
                            "SERVER_ERROR" -> {
                                onPortalError(
                                    PortalErrorState(
                                        title = "Portal Server Error",
                                        detail = "Portal reported 'Server error. Please try again after sometime.' Session cookies are preserved — tap Refresh Captcha or Retry Page for a fresh captcha.",
                                        isBlockingErrorPage = false,
                                        kind = PortalErrorKind.SERVER_ERROR,
                                        failedUrl = url ?: lastMainFrameUrl,
                                        canRefreshCaptcha = hasAuthForm
                                    )
                                )
                            }
                            "INVALID_CAPTCHA" -> {
                                onPortalError(
                                    PortalErrorState(
                                        title = "Invalid Captcha",
                                        detail = "Captcha is case-sensitive and tied to the latest page load. Tap Refresh Captcha to reload a synchronized captcha without losing session cookies, then submit again.",
                                        isBlockingErrorPage = false,
                                        kind = PortalErrorKind.INVALID_CAPTCHA,
                                        failedUrl = url ?: lastMainFrameUrl,
                                        canRefreshCaptcha = hasAuthForm
                                    )
                                )
                            }
                        }

                        // Skip extension compat and filler scripts on login/captcha pages to prevent any interference
                        if (!hasAuthForm) {
                            activeView.evaluateJavascript(PortalHrpcDomScripts.installSameSessionPopupInterceptorJs(), null)
                            if (!alreadyCompatInstalled) {
                                val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                                if (!compatJs.isNullOrBlank()) {
                                    activeView.evaluateJavascript(compatJs, null)
                                }
                            }
                            if (!alreadyInjected) {
                                activeView.evaluateJavascript("window.__bspContentScriptsInjected = true;", null)
                                listOf("lpcd/content.js", "bco/ab_form_filler_v2/content.js").forEach { path ->
                                    val script = bridge.readExtensionAssetText(path)
                                    if (!script.isNullOrBlank()) {
                                        activeView.evaluateJavascript(script, null)
                                    }
                                }
                            }
                            if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                                activeView.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                            }

                            // Track HRPC Student Information table vs Marksheet View/Print subpage vs CodeIgniter DB errors
                            activeView.evaluateJavascript(
                                """
                                (function() {
                                    var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
                                    var hasDbError = /An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt);
                                    var onStudentTable = !hasDbError && (
                                        /Showing\s+\d+\s+to\s+\d+\s+of\s+\d+\s+entries/i.test(bodyTxt) ||
                                        !!document.querySelector('table[data-bsp-student-table="true"]')
                                    );
                                    var onMarksheetView = !hasDbError && !onStudentTable && (
                                        /Summative\s+Evaluation|Progress\s+Report|Holistic\s+Report|Mark\s*sheet/i.test(bodyTxt) ||
                                        !!document.querySelector('section.sheet, .sheet, .print-page, .marksheet-page, #printableArea, #printArea')
                                    );
                                    return JSON.stringify({
                                        hasDbError: hasDbError,
                                        onStudentTable: onStudentTable,
                                        onMarksheetView: onMarksheetView
                                    });
                                })();
                                """.trimIndent()
                            ) { stateRaw ->
                                val stObj = runCatching {
                                    val dec = if (stateRaw != null && stateRaw.startsWith("\"")) {
                                        org.json.JSONArray("[$stateRaw]").getString(0)
                                    } else {
                                        stateRaw.orEmpty()
                                    }
                                    org.json.JSONObject(dec)
                                }.getOrNull()
                                val hasDbError = stObj?.optBoolean("hasDbError", false) == true
                                val onStudentTable = stObj?.optBoolean("onStudentTable", false) == true
                                val onMarksheetView = stObj?.optBoolean("onMarksheetView", false) == true
                                onMarksheetSubpageChanged(onMarksheetView)

                                if (hasDbError) {
                                    automationController?.markUrlAsDbError(url)
                                    if (automationController?.isAnyAutomationActive() != true && dbErrorRecoveryCount < 2) {
                                        dbErrorRecoveryCount++
                                        val snapJson = automationController?.getLastHrpcSnapshot()
                                        val snapObj = runCatching { org.json.JSONObject(snapJson ?: "{}") }.getOrNull()
                                        val menuHref = snapObj?.optString("hrpcMenuHref", "")?.trim().orEmpty()
                                        val recoveryUrl = when {
                                            menuHref.startsWith("http", ignoreCase = true) && menuHref != url -> menuHref
                                            else -> resolvedPortalUrl
                                        }
                                        if (!snapJson.isNullOrBlank()) {
                                            automationController?.pendingHrpcRestoreFromBack = true
                                        }
                                        activeView.post {
                                            activeView.stopLoading()
                                            activeView.loadUrl(recoveryUrl)
                                        }
                                    }
                                    return@evaluateJavascript
                                }

                                if (onStudentTable) {
                                    cacheMissRecoveryCount = 0
                                    dbErrorRecoveryCount = 0
                                    if (automationController?.isAnyAutomationActive() != true) {
                                        val snapPendingRestore = automationController?.pendingHrpcRestoreFromBack == true
                                        if (snapPendingRestore) {
                                            automationController?.pendingHrpcRestoreFromBack = false
                                            val snapJson = automationController?.getLastHrpcSnapshot()
                                            val savedPage = runCatching {
                                                org.json.JSONObject(snapJson ?: "{}").optInt("currentPage", 1)
                                            }.getOrDefault(1)
                                            if (savedPage > 1) {
                                                activeView.evaluateJavascript(
                                                    PortalHrpcDomScripts.clickPaginationPageJs(savedPage, "exact"),
                                                    null
                                                )
                                            }
                                        } else {
                                            activeView.evaluateJavascript(PortalHrpcDomScripts.snapshotHrpcStateJs()) { snapRaw ->
                                                val decodedSnap = runCatching {
                                                    if (snapRaw != null && snapRaw.startsWith("\"")) {
                                                        org.json.JSONArray("[$snapRaw]").getString(0)
                                                    } else {
                                                        snapRaw.orEmpty()
                                                    }
                                                }.getOrNull()
                                                if (!decodedSnap.isNullOrBlank()) {
                                                    automationController?.updateLastHrpcSnapshot(decodedSnap)
                                                }
                                            }
                                        }
                                    }
                                } else if (!onMarksheetView &&
                                    automationController?.isAnyAutomationActive() != true &&
                                    automationController?.pendingHrpcRestoreFromBack == true
                                ) {
                                    val snapJson = automationController?.getLastHrpcSnapshot()
                                    if (!snapJson.isNullOrBlank()) {
                                        activeView.evaluateJavascript(PortalHrpcDomScripts.restoreHrpcFiltersJs(snapJson)) { res ->
                                            val triggered = runCatching {
                                                val dec = if (res != null && res.startsWith("\"")) {
                                                    org.json.JSONArray("[$res]").getString(0)
                                                } else {
                                                    res.orEmpty()
                                                }
                                                org.json.JSONObject(dec).optBoolean("triggeredSearch", false)
                                            }.getOrDefault(false)
                                            if (!triggered) {
                                                automationController?.pendingHrpcRestoreFromBack = false
                                            }
                                        }
                                    } else {
                                        automationController?.pendingHrpcRestoreFromBack = false
                                    }
                                }
                            }
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        onTitle(title)
                    }
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsAlert(view, url, message, result)
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsConfirm(view, url, message, result)
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.cancel()
                        return true
                    }
                    return super.onJsPrompt(view, url, message, defaultValue, result)
                }

                override fun onJsBeforeUnload(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsBeforeUnload(view, url, message, result)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val hit = view?.hitTestResult
                    val hitType = hit?.type
                    val hitExtra = hit?.extra?.trim().orEmpty()
                    if (automationController?.isAnyAutomationActive() == true ||
                        automationController?.isMainTabNavigationLocked() == true
                    ) {
                        // Ignore view?.hitTestResult here because JS .click() does not update
                        // touch hitTestResult and would read a stale previously-tapped link.
                        val bgView = automationController?.getBackgroundWebView()
                        if (bgView != null && resultMsg != null) {
                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                            bgView.stopLoading()
                            transport.webView = bgView
                            resultMsg.sendToTarget()
                            return true
                        }
                        return false
                    }
                    if (resultMsg != null) {
                        return onCreatePopupWindow(isDialog, resultMsg)
                    }
                    if ((hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                            hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                        hitExtra.startsWith("http", ignoreCase = true)
                    ) {
                        view.loadUrl(hitExtra)
                        return false
                    }
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        onAudioPermission(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "text/csv",
                                "text/plain",
                                "text/comma-separated-values",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    }
                    onOpenFileChooser(filePathCallback, Intent.createChooser(intent, "Select Excel / CSV file"))
                    return true
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                    automationController.onPortalDownloadTriggered(url, mimeType, userAgent)
                } else if (url.startsWith("http", ignoreCase = true)) {
                    enqueueCookieAwarePortalDownload(ctx, cookieManager, url, userAgent, contentDisposition, mimeType)
                }
            }

            if (requireFreshSignIn) {
                loadUrl(resolvedPortalUrl, NO_CACHE_HEADERS)
            } else {
                loadUrl(resolvedPortalUrl)
            }
        }
    }

    private fun enqueueCookieAwarePortalDownload(
        ctx: android.content.Context,
        cookieManager: CookieManager,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        runCatching {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .ifBlank { "portal_document.pdf" }
            val dm = ctx.getSystemService(DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                val cookies = runCatching { cookieManager.getCookie(url) }.getOrNull()
                if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription("Downloading from Banglarshiksha SMS Portal")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }
            dm.enqueue(req)
            Toast.makeText(ctx, "Downloading $fileName...", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attachControlledPopupWindow(
        ctx: android.content.Context,
        profileName: String,
        bridge: ChromeCompatBridge,
        mainPortalWebView: WebView?,
        isDialog: Boolean,
        resultMsg: Message,
        automationController: MarksheetAutomationController?,
        previousPopupProvider: () -> WebView?,
        onPopupAttached: (WebView) -> Unit,
        onPopupProgress: (Int) -> Unit,
        onPopupTitle: (String) -> Unit,
        onPopupError: (String?) -> Unit,
        onClosePopup: () -> Unit,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val cookieManager = resolveProfileCookieManager(profileName)
        var attachedToUi = false

        val childWebView = WebView(ctx).apply {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching { WebViewCompat.setProfile(this, profileName) }
            }

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                @Suppress("DEPRECATION")
                saveFormData = false
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                allowFileAccess = false
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = (userAgentString ?: "")
                    .replace("; wv)", ")")
                    .replace("; wv;", ";")
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                private var lastPopupUrl: String = ""
                private var popupCacheMissRetries: Int = 0

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https" || scheme == "blob" || scheme == "data" || scheme == "about") {
                        return false
                    }
                    return try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) {
                        false
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    cookieManager.flush()
                    if (!attachedToUi && view != null) {
                        attachedToUi = true
                        onPopupAttached(view)
                    }
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        lastPopupUrl = url
                    }
                    automationController?.clearWebViewTransientError()
                    onPopupError(null)
                    if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                        view?.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    cookieManager.flush()
                    val reqUrl = request?.url?.toString().orEmpty()
                    val errorCode = error?.errorCode ?: 0
                    val desc = error?.description?.toString()?.ifBlank { "Connection error" } ?: "Network error"
                    val isCacheMiss = errorCode == -400 ||
                        desc.contains("ERR_CACHE_MISS", ignoreCase = true) ||
                        desc.contains("CACHE_MISS", ignoreCase = true)

                    if (request?.isForMainFrame == true) {
                        if (isCacheMiss) {
                            automationController?.notifyWebViewCacheMiss()
                            if (automationController?.isAnyAutomationActive() == true) {
                                return
                            }
                            val recoveryUrl = reqUrl.takeIf {
                                it.startsWith("http", ignoreCase = true) && !it.contains("chromewebdata", ignoreCase = true)
                            } ?: lastPopupUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                            if (popupCacheMissRetries < 1 && !recoveryUrl.isNullOrBlank() && view != null) {
                                popupCacheMissRetries++
                                view.post {
                                    view.stopLoading()
                                    view.loadUrl(recoveryUrl)
                                }
                                return
                            }
                            onPopupError("Cache expired ($desc). Tap Back to Table to continue safely.")
                        } else {
                            automationController?.notifyWebViewMainFrameError(desc)
                            if (automationController?.isAnyAutomationActive() != true) {
                                onPopupError("Could not load preview/print page ($desc).")
                            }
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    cookieManager.flush()
                    val activeView = view ?: return
                    if (!attachedToUi) {
                        attachedToUi = true
                        onPopupAttached(activeView)
                    }
                    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) {
                        lastPopupUrl = url
                        popupCacheMissRetries = 0
                    }
                    if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                        activeView.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                    }
                    activeView.evaluateJavascript(
                        """
                        (function() {
                            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
                            var isDbError = /An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt);
                            var isCacheMiss = isDbError || (location.href.indexOf('chromewebdata') !== -1 &&
                                /ERR_CACHE_MISS|Confirm Form Resubmission|Webpage not available/i.test(bodyTxt));
                            return JSON.stringify({
                                isCacheMiss: isCacheMiss,
                                isDbError: isDbError,
                                hasCompat: !!window.__bspChromeCompatInstalled
                            });
                        })();
                        """.trimIndent()
                    ) { raw ->
                        val obj = runCatching {
                            val dec = if (raw != null && raw.startsWith("\"")) {
                                org.json.JSONArray("[$raw]").getString(0)
                            } else {
                                raw.orEmpty()
                            }
                            org.json.JSONObject(dec)
                        }.getOrNull()
                        if (obj?.optBoolean("isCacheMiss", false) == true) {
                            if (obj.optBoolean("isDbError", false)) {
                                automationController?.markUrlAsDbError(url)
                            } else {
                                automationController?.notifyWebViewCacheMiss()
                            }
                            if (automationController?.isAnyAutomationActive() != true) {
                                onPopupError("Session/database context required. Tap Back to Table to reload safely.")
                            }
                            return@evaluateJavascript
                        }
                        if (obj?.optBoolean("hasCompat", false) != true) {
                            val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                            if (!compatJs.isNullOrBlank()) {
                                activeView.evaluateJavascript(compatJs, null)
                            }
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onPopupProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        onPopupTitle(title)
                    }
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsAlert(view, url, message, result)
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsConfirm(view, url, message, result)
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.cancel()
                        return true
                    }
                    return super.onJsPrompt(view, url, message, defaultValue, result)
                }

                override fun onJsBeforeUnload(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (automationController?.isAnyAutomationActive() == true) {
                        result?.confirm()
                        return true
                    }
                    return super.onJsBeforeUnload(view, url, message, result)
                }

                override fun onCloseWindow(window: WebView?) {
                    onClosePopup()
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isNestedDialog: Boolean,
                    isUserGesture: Boolean,
                    nestedMsg: Message?
                ): Boolean {
                    if (nestedMsg == null) return false
                    return attachControlledPopupWindow(
                        ctx = ctx,
                        profileName = profileName,
                        bridge = bridge,
                        mainPortalWebView = mainPortalWebView,
                        isDialog = isNestedDialog,
                        resultMsg = nestedMsg,
                        automationController = automationController,
                        previousPopupProvider = previousPopupProvider,
                        onPopupAttached = onPopupAttached,
                        onPopupProgress = onPopupProgress,
                        onPopupTitle = onPopupTitle,
                        onPopupError = onPopupError,
                        onClosePopup = onClosePopup,
                        onOpenFileChooser = onOpenFileChooser,
                        onAudioPermission = onAudioPermission
                    )
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        onAudioPermission(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    onOpenFileChooser(filePathCallback, Intent.createChooser(intent, "Select File"))
                    return true
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (automationController?.isAutomatedPrintSuppressionActive() == true) {
                    automationController.onPortalDownloadTriggered(url, mimeType, userAgent)
                } else if (url.startsWith("http", ignoreCase = true)) {
                    enqueueCookieAwarePortalDownload(ctx, cookieManager, url, userAgent, contentDisposition, mimeType)
                    if (this@apply.url.isNullOrBlank() || this@apply.url == "about:blank") {
                        onClosePopup()
                    }
                }
            }
        }

        val oldPopup = previousPopupProvider()
        transport.webView = childWebView
        resultMsg.sendToTarget()
        if (oldPopup != null && oldPopup !== childWebView) {
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    oldPopup.stopLoading()
                    (oldPopup.parent as? ViewGroup)?.removeView(oldPopup)
                    oldPopup.destroy()
                }
            }
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBackgroundMarksheetWebView(
        ctx: android.content.Context,
        profileName: String,
        bridge: ChromeCompatBridge,
        automationController: MarksheetAutomationController
    ): WebView {
        return WebView(ctx).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO

            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                runCatching { WebViewCompat.setProfile(this, profileName) }
            }

            val cookieManager = resolveProfileCookieManager(profileName)
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                @Suppress("DEPRECATION")
                saveFormData = false
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                allowFileAccess = false
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = (userAgentString ?: "")
                    .replace("; wv)", ")")
                    .replace("; wv;", ";")
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: return false
                    return !(scheme == "http" || scheme == "https" || scheme == "blob" || scheme == "data" || scheme == "about")
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    cookieManager.flush()
                    automationController.clearBackgroundTransientError()
                    if (url != null && url != "about:blank") {
                        view?.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    cookieManager.flush()
                    if (request?.isForMainFrame != true) return
                    val reqUrl = request.url?.toString().orEmpty()
                    if (reqUrl.isBlank() || reqUrl == "about:blank") return
                    val errorCode = error?.errorCode ?: 0
                    val desc = error?.description?.toString()?.ifBlank { "Network error" } ?: "Network error"
                    if (desc.contains("ERR_ABORTED", ignoreCase = true)) {
                        return
                    }
                    val isCacheMiss = errorCode == -400 ||
                        desc.contains("ERR_CACHE_MISS", ignoreCase = true) ||
                        desc.contains("CACHE_MISS", ignoreCase = true)
                    if (isCacheMiss) {
                        automationController.notifyBackgroundCacheMiss()
                    } else {
                        automationController.notifyBackgroundMainFrameError(desc)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val code = errorResponse?.statusCode ?: 0
                    if (request?.isForMainFrame == true && code >= 500) {
                        automationController.notifyBackgroundMainFrameError("Portal returned HTTP $code for marksheet")
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    cookieManager.flush()
                    automationController.notifyBackgroundMainFrameError("Background marksheet renderer reset")
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    cookieManager.flush()
                    val activeView = view ?: return
                    if (url.isNullOrBlank() || url == "about:blank") return
                    activeView.evaluateJavascript(PortalHrpcDomScripts.suppressAutoPrintJs(), null)
                    activeView.evaluateJavascript(
                        """
                        (function() {
                            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
                            var isDbError = /An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt);
                            var isCacheMiss = location.href.indexOf('chromewebdata') !== -1 &&
                                /ERR_CACHE_MISS|Confirm Form Resubmission|Webpage not available/i.test(bodyTxt);
                            return JSON.stringify({
                                isDbError: isDbError,
                                isCacheMiss: isCacheMiss,
                                hasCompat: !!window.__bspChromeCompatInstalled
                            });
                        })();
                        """.trimIndent()
                    ) { raw ->
                        val obj = runCatching {
                            val dec = if (raw != null && raw.startsWith("\"")) {
                                org.json.JSONArray("[$raw]").getString(0)
                            } else {
                                raw.orEmpty()
                            }
                            org.json.JSONObject(dec)
                        }.getOrNull()
                        if (obj?.optBoolean("isDbError", false) == true) {
                            automationController.markUrlAsDbError(url)
                            automationController.notifyBackgroundMainFrameError("Portal database error for marksheet")
                            return@evaluateJavascript
                        }
                        if (obj?.optBoolean("isCacheMiss", false) == true) {
                            automationController.notifyBackgroundCacheMiss()
                            return@evaluateJavascript
                        }
                        if (obj?.optBoolean("hasCompat", false) != true) {
                            val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                            if (!compatJs.isNullOrBlank()) {
                                activeView.evaluateJavascript(compatJs, null)
                            }
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    result?.cancel()
                    return true
                }

                override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = this@apply
                    resultMsg.sendToTarget()
                    return true
                }
            }

            setDownloadListener { url, userAgent, _, mimeType, _ ->
                automationController.onPortalDownloadTriggered(url, mimeType, userAgent)
            }
        }
    }

    @Composable
    private fun PortalAutomationHudBar(
        isBulk: Boolean,
        bulkState: BulkMarksheetState,
        resultState: ResultReadyState,
        onPauseResume: () -> Unit,
        onStop: () -> Unit,
        onOpenDashboard: () -> Unit
    ) {
        val status = if (isBulk) bulkState.jobStatus else resultState.jobStatus
        val isPaused = status == AutomationJobStatus.PAUSED
        val title = if (isBulk) "Bulk Marksheet" else "Result Ready"
        val currentText = if (isBulk) {
            "${bulkState.completedDownloads}/${bulkState.totalStudents} · P${bulkState.currentPageNumber} · ${bulkState.currentRollNo} ${bulkState.currentStudentName}"
        } else {
            "${resultState.completedCount}/${resultState.totalStudents} · P${resultState.currentPageNumber} · ${resultState.currentRollNo} ${resultState.currentStudentName}"
        }
        Surface(
            color = if (isPaused) Color(0xFFFFFBEB) else Color(0xFFECFDF5),
            border = BorderStroke(1.dp, if (isPaused) Color(0xFFFDE68A) else Color(0xFFA7F3D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$title (${if (isPaused) "PAUSED" else "RUNNING"})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) Color(0xFFB45309) else Color(0xFF047857)
                    )
                    Text(
                        text = currentText.trim(),
                        fontSize = 10.sp,
                        color = Color(0xFF334155),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPauseResume, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = "Pause or Resume",
                            tint = Color(0xFF123B70),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(
                        onClick = onOpenDashboard,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createToolWebView(
        ctx: android.content.Context,
        initialUrl: String,
        bridge: ChromeCompatBridge,
        assetLoader: WebViewAssetLoader,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit
    ): WebView {
        return WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    val response = assetLoader.shouldInterceptRequest(uri) ?: return null
                    val path = uri.path ?: ""
                    if (path.endsWith(".html", ignoreCase = true) && !path.endsWith("/hub.html", ignoreCase = true)) {
                        return try {
                            val html = response.data.bufferedReader().use { it.readText() }
                            val compatTag = "<script src=\"/assets/extension/chrome-compat.js\"></script>"
                            val injectedHtml = if (!html.contains("chrome-compat.js")) {
                                if (html.contains("<head>", ignoreCase = true)) {
                                    html.replaceFirst("<head>", "<head>$compatTag", ignoreCase = true)
                                } else {
                                    "$compatTag$html"
                                }
                            } else {
                                html
                            }
                            WebResourceResponse(
                                "text/html",
                                "UTF-8",
                                injectedHtml.byteInputStream()
                            )
                        } catch (_: Exception) {
                            response
                        }
                    }
                    return response
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                    if (!compatJs.isNullOrBlank()) {
                        view?.evaluateJavascript(compatJs, null)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        onAudioPermission(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "text/csv",
                                "text/plain",
                                "text/comma-separated-values",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    }
                    onOpenFileChooser(filePathCallback, Intent.createChooser(intent, "Select Excel / CSV file"))
                    return true
                }
            }

            loadUrl(initialUrl)
        }
    }
}
