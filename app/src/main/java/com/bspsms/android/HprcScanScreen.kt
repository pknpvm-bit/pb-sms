package com.bspsms.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

private const val HPRC_SESSION_PREFS = "bsp_hprc_scan_session_v1"
private const val KEY_STUDENTS_JSON = "students_json"
private const val KEY_QUEUE_JSON = "queue_json"
private const val KEY_ACTIVE_STUDENT_ID = "active_student_id"

object HprcSessionStore {
    fun saveSession(
        context: Context,
        students: List<HprcStudentScanRecord>,
        queue: List<HprcUncertainField>,
        activeStudentId: String?
    ) {
        val prefs = context.getSharedPreferences(HPRC_SESSION_PREFS, Context.MODE_PRIVATE)
        val stuArr = JSONArray().apply { students.forEach { put(it.toJson()) } }
        val qArr = JSONArray().apply { queue.forEach { put(it.toJson()) } }
        prefs.edit()
            .putString(KEY_STUDENTS_JSON, stuArr.toString())
            .putString(KEY_QUEUE_JSON, qArr.toString())
            .putString(KEY_ACTIVE_STUDENT_ID, activeStudentId.orEmpty())
            .apply()
    }

    fun loadStudents(context: Context): List<HprcStudentScanRecord> {
        val raw = context.getSharedPreferences(HPRC_SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STUDENTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { HprcStudentScanRecord.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun loadQueue(context: Context): List<HprcUncertainField> {
        val raw = context.getSharedPreferences(HPRC_SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { HprcUncertainField.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun loadActiveStudentId(context: Context): String? {
        return context.getSharedPreferences(HPRC_SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_STUDENT_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun exportToStream(students: List<HprcStudentScanRecord>, outStream: OutputStream) {
        HprcWorkbookExporter.writeWorkbook(students, outStream)
    }
}

@Composable
fun HprcScanScreen(
    locale: AppLocale,
    portalWebViewProvider: () -> WebView?,
    onRequestCameraPermission: ((Boolean) -> Unit) -> Unit,
    onCreateHprcExcel: (suggestedFileName: String, studentsSnapshot: List<HprcStudentScanRecord>) -> Unit,
    onOpenPortal: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primaryBlue = Color(0xFF123B70)
    val accentGreen = Color(0xFF15803D)
    val accentPurple = Color(0xFF6D28D9)

    var students by remember { mutableStateOf(HprcSessionStore.loadStudents(context)) }
    var reviewQueue by remember { mutableStateOf(HprcSessionStore.loadQueue(context)) }
    var activeStudentId by remember {
        mutableStateOf(HprcSessionStore.loadActiveStudentId(context) ?: students.lastOrNull()?.localId)
    }
    var previewMode by remember { mutableStateOf(HprcPreviewMode.STUDENT_WISE) }
    var isScanning by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var statusMessage by remember {
        mutableStateOf(
            if (students.isNotEmpty()) {
                "Loaded saved session (${students.size} student(s)). Tap SCAN to continue & append."
            } else {
                "Tap SCAN to point camera at HPRC report card pages. All pages for one student merge automatically."
            }
        )
    }
    var lastFrameProcessedAtMs by remember { mutableLongStateOf(0L) }
    var lastCapturedSignature by remember { mutableStateOf("") }

    // Fill dialog state
    var showFillDialog by remember { mutableStateOf(false) }
    var fillContext by remember { mutableStateOf<HprcPortalContext?>(null) }
    var fillPreviewRows by remember { mutableStateOf<List<HprcFillPreviewRow>>(emptyList()) }
    var fillPlanJson by remember { mutableStateOf("[]") }
    var isFillInspecting by remember { mutableStateOf(false) }

    // Manual edit dialog state
    var editingQueueItem by remember { mutableStateOf<HprcUncertainField?>(null) }
    var editingQueueValue by remember { mutableStateOf("") }

    LaunchedEffect(students, reviewQueue, activeStudentId) {
        HprcSessionStore.saveSession(context, students, reviewQueue, activeStudentId)
    }

    fun startNewStudentSlot() {
        val newStudent = HprcStudentScanRecord(localId = UUID.randomUUID().toString())
        students = students + newStudent
        activeStudentId = newStudent.localId
        lastCapturedSignature = ""
        statusMessage = "Ready for Next Student (#${students.size}). Scan Page 1–5."
    }

    fun absorbParsedPage(parsed: HprcParsedPageResult) {
        if (!parsed.hasUsefulData) return
        if (parsed.pageSignature == lastCapturedSignature) return

        val existingList = students.toMutableList()
        var targetIdx = existingList.indexOfFirst { it.localId == activeStudentId }

        // Detect if OCR clearly found a DIFFERENT student already (different name/roll/code) -> create/switch student automatically
        if (targetIdx >= 0) {
            val cur = existingList[targetIdx]
            val differentCode = parsed.studentCode.isNotBlank() && cur.studentCode.isNotBlank() &&
                parsed.studentCode != cur.studentCode
            val differentRoll = parsed.rollNo.isNotBlank() && cur.rollNo.isNotBlank() &&
                parsed.rollNo != cur.rollNo
            val differentName = parsed.studentName.isNotBlank() && cur.studentName.isNotBlank() &&
                !namesLikelySame(parsed.studentName, cur.studentName)
            if (differentCode || (differentRoll && differentName)) {
                val matchIdx = existingList.indexOfFirst { st ->
                    (parsed.studentCode.isNotBlank() && st.studentCode == parsed.studentCode) ||
                        (parsed.rollNo.isNotBlank() && st.rollNo == parsed.rollNo && parsed.studentName.isNotBlank() && namesLikelySame(st.studentName, parsed.studentName))
                }
                if (matchIdx >= 0) {
                    targetIdx = matchIdx
                } else {
                    val nextSt = HprcStudentScanRecord(localId = UUID.randomUUID().toString())
                    existingList.add(nextSt)
                    targetIdx = existingList.lastIndex
                }
            }
        } else {
            val newSt = HprcStudentScanRecord(localId = UUID.randomUUID().toString())
            existingList.add(newSt)
            targetIdx = existingList.lastIndex
        }

        val current = existingList[targetIdx]
        if (current.rawPageSignatures.contains(parsed.pageSignature)) {
            lastCapturedSignature = parsed.pageSignature
            return
        }

        val updatedPages = if (parsed.detectedPageNumber != null && !current.scannedPages.contains(parsed.detectedPageNumber)) {
            current.scannedPages + parsed.detectedPageNumber
        } else {
            current.scannedPages
        }

        val mergedStudent = current.copy(
            studentName = current.studentName.ifBlank { parsed.studentName },
            rollNo = current.rollNo.ifBlank { parsed.rollNo },
            studentCode = current.studentCode.ifBlank { parsed.studentCode },
            className = current.className.ifBlank { parsed.className },
            section = current.section.ifBlank { parsed.section },
            academicYear = current.academicYear.ifBlank { parsed.academicYear },
            medium = current.medium.ifBlank { parsed.medium },
            scannedPages = updatedPages,
            formativeMarks = current.formativeMarks + parsed.formativeMarks,
            summativeMarks = current.summativeMarks + parsed.summativeMarks,
            lpcdResponses = current.lpcdResponses + parsed.lpcdResponses,
            bcoResponses = current.bcoResponses + parsed.bcoResponses,
            remarks = current.remarks.ifBlank { parsed.remarks },
            rawPageSignatures = current.rawPageSignatures + parsed.pageSignature
        )

        existingList[targetIdx] = mergedStudent
        students = existingList
        activeStudentId = mergedStudent.localId
        lastCapturedSignature = parsed.pageSignature

        if (parsed.uncertainFields.isNotEmpty()) {
            val newQueueItems = parsed.uncertainFields.map {
                it.copy(
                    studentLocalId = mergedStudent.localId,
                    studentLabel = mergedStudent.displayTitle
                )
            }
            val existingKeys = reviewQueue.map { "${it.studentLocalId}|${it.section}|${it.fieldKey}|${it.rawOcrText}" }.toSet()
            val deduped = newQueueItems.filterNot {
                existingKeys.contains("${it.studentLocalId}|${it.section}|${it.fieldKey}|${it.rawOcrText}")
            }
            if (deduped.isNotEmpty()) {
                reviewQueue = reviewQueue + deduped
            }
        }

        val secSummary = parsed.detectedSections.joinToString(", ").ifBlank { "Student Info" }
        statusMessage = "Captured [$secSummary] for ${mergedStudent.displayTitle} (Total fields: " +
            "${mergedStudent.summativeMarks.size + mergedStudent.formativeMarks.size + mergedStudent.lpcdResponses.size + mergedStudent.bcoResponses.size})"
    }

    fun inspectPortalAndPrepareFill() {
        val wv = portalWebViewProvider()
        if (wv == null) {
            statusMessage = "Open Portal page with marks-entry table first before tapping FILL."
            return
        }
        if (students.isEmpty()) {
            statusMessage = "Scan at least one HPRC card before using FILL."
            return
        }
        isFillInspecting = true
        wv.evaluateJavascript(PortalHrpcDomScripts.inspectHprcFillContextJs()) { raw ->
            isFillInspecting = false
            val decoded = runCatching {
                if (raw != null && raw.startsWith("\"")) JSONArray("[$raw]").getString(0) else raw.orEmpty()
            }.getOrDefault("{}")
            val obj = runCatching { JSONObject(decoded) }.getOrNull()
            if (obj == null || !obj.optBoolean("ok", false)) {
                statusMessage = "Could not inspect Portal marks table."
                return@evaluateJavascript
            }
            val ctx = HprcPortalContext(
                detected = obj.optBoolean("detected", false),
                category = obj.optString("category", "Summative"),
                academicYear = obj.optString("academicYear", ""),
                medium = obj.optString("medium", ""),
                className = obj.optString("className", ""),
                section = obj.optString("section", ""),
                subject = obj.optString("subject", ""),
                term = obj.optString("term", ""),
                portalUrl = obj.optString("portalUrl", ""),
                writableRowCount = obj.optInt("writableRowCount", 0)
            )
            fillContext = ctx

            val portalRowsArr = obj.optJSONArray("rows") ?: JSONArray()
            val previewList = mutableListOf<HprcFillPreviewRow>()
            val planArr = JSONArray()

            for (i in 0 until portalRowsArr.length()) {
                val rObj = portalRowsArr.optJSONObject(i) ?: continue
                val rowIdx = rObj.optInt("rowIndex", i)
                val pName = rObj.optString("studentName", "")
                val pRoll = rObj.optString("roll", "")
                val pCode = rObj.optString("studentCode", "")
                val fieldsArr = rObj.optJSONArray("fields") ?: JSONArray()

                val matched = findBestStudentMatch(students, pCode, pRoll, pName)
                if (matched == null) continue

                val (studentRec, reason) = matched
                val writesArr = JSONArray()
                val writesMap = LinkedHashMap<String, String>()

                for (fi in 0 until fieldsArr.length()) {
                    val fObj = fieldsArr.optJSONObject(fi) ?: continue
                    val fieldIdx = fObj.optInt("fieldIdx", fi)
                    val label = fObj.optString("label", "Field ${fi + 1}")
                    val maxStr = fObj.optString("max", "").toDoubleOrNull()
                    val value = resolveValueForPortalField(
                        student = studentRec,
                        category = ctx.category,
                        pageSubject = ctx.subject,
                        pageTerm = ctx.term,
                        fieldLabel = label,
                        fieldIndex = fi
                    )
                    if (!value.isNullOrBlank()) {
                        val numVal = value.toDoubleOrNull()
                        if (maxStr != null && numVal != null && numVal > maxStr) {
                            continue
                        }
                        writesArr.put(JSONObject().put("fieldIdx", fieldIdx).put("value", value))
                        writesMap[label] = value
                    }
                }

                if (writesMap.isNotEmpty()) {
                    planArr.put(JSONObject().put("rowIndex", rowIdx).put("writes", writesArr))
                }

                previewList.add(
                    HprcFillPreviewRow(
                        studentLocalId = studentRec.localId,
                        scannedName = studentRec.studentName,
                        scannedRoll = studentRec.rollNo,
                        portalRowIndex = rowIdx,
                        portalStudentName = pName,
                        portalRoll = pRoll,
                        portalStudentCode = pCode,
                        matchReason = reason,
                        fieldsToFill = writesMap,
                        skippedReason = if (writesMap.isEmpty()) "No matching ${ctx.category} values scanned" else ""
                    )
                )
            }

            fillPreviewRows = previewList
            fillPlanJson = planArr.toString()
            showFillDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Portal quick link
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (locale == AppLocale.BN) "HPRC স্ক্যান টুল" else "HPRC SCAN TOOL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Live continuous OCR scan → 6-sheet Excel & safe Portal fill",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
                OutlinedButton(
                    onClick = onOpenPortal,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Portal", fontSize = 11.sp)
                }
            }
        }

        // Primary Action Bar: SCAN / STOP SCAN + FILL + NEXT STUDENT + EXPORT
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isScanning) {
                            Button(
                                onClick = {
                                    if (hasCameraPermission) {
                                        isScanning = true
                                        statusMessage = "Live Camera Scanning active — hold steady over each HPRC page."
                                    } else {
                                        onRequestCameraPermission { granted ->
                                            hasCameraPermission = granted
                                            if (granted) {
                                                isScanning = true
                                                statusMessage = "Live Camera Scanning active — point at HPRC pages."
                                            } else {
                                                statusMessage = "Camera permission is required to scan HPRC cards."
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("SCAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isScanning = false
                                    statusMessage = "Scan paused. ${students.size} student(s) in session."
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("STOP SCAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            enabled = !isFillInspecting && students.isNotEmpty(),
                            onClick = { inspectPortalAndPrepareFill() },
                            colors = ButtonDefaults.buttonColors(containerColor = accentPurple),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isFillInspecting) "..." else "FILL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = students.isNotEmpty(),
                            onClick = {
                                val fileName = "HPRC_Scanned_${System.currentTimeMillis() / 1000}.xlsx"
                                onCreateHprcExcel(fileName, students)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".XLSX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { startNewStudentSlot() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Next Student", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            enabled = students.isNotEmpty(),
                            onClick = {
                                students = emptyList()
                                reviewQueue = emptyList()
                                activeStudentId = null
                                lastCapturedSignature = ""
                                statusMessage = "Started fresh HPRC scan session."
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Session", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            fontSize = 11.sp,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Live CameraX Preview Surface when SCAN is active
        if (isScanning && hasCameraPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, accentGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LiveCameraOcrView(
                            lifecycleOwner = lifecycleOwner,
                            activeStudentLocalId = activeStudentId ?: "pending",
                            activeStudentLabel = students.firstOrNull { it.localId == activeStudentId }?.displayTitle ?: "New Student",
                            onThrottleCheck = { now ->
                                if (now - lastFrameProcessedAtMs >= 650L) {
                                    lastFrameProcessedAtMs = now
                                    true
                                } else {
                                    false
                                }
                            },
                            onPageParsed = { parsed ->
                                absorbParsedPage(parsed)
                            }
                        )
                        Surface(
                            color = Color(0xAA0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            val activeLabel = students.firstOrNull { it.localId == activeStudentId }?.displayTitle ?: "Auto-Detect Student"
                            Text(
                                text = "Scanning: $activeLabel · Auto-capturing pages continuously",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Review Queue Card (Uncertain OCR fields — never guessed)
        val unresolvedQueue = reviewQueue.filter { !it.resolved }
        if (unresolvedQueue.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Review Queue (${unresolvedQueue.size} uncertain item(s))",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        unresolvedQueue.take(6).forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${q.studentLabel} · ${q.section} (${q.fieldKey})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "OCR: ${q.rawOcrText}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = q.reason,
                                            fontSize = 10.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingQueueItem = q
                                                editingQueueValue = q.candidateValue
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Verify", tint = primaryBlue, modifier = Modifier.size(17.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                reviewQueue = reviewQueue.map {
                                                    if (it.id == q.id) it.copy(resolved = true) else it
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Dismiss", tint = Color(0xFF94A3B8), modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview Mode Switcher (Student_Wise vs Original_Format)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanned Records (${students.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "6-Sheet Export Ready",
                            fontSize = 10.sp,
                            color = accentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = previewMode == HprcPreviewMode.STUDENT_WISE,
                            onClick = { previewMode = HprcPreviewMode.STUDENT_WISE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Student_Wise", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = previewMode == HprcPreviewMode.ORIGINAL_FORMAT,
                            onClick = { previewMode = HprcPreviewMode.ORIGINAL_FORMAT },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Original_Format", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (students.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.TableChart, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No HPRC cards scanned yet.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "Tap SCAN above to scan printed or handwritten Formative, Summative, LPCD & BCO pages live.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else if (previewMode == HprcPreviewMode.STUDENT_WISE) {
            items(students, key = { it.localId }) { st ->
                val isSelected = st.localId == activeStudentId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) primaryBlue else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = st.displayTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryBlue
                                )
                                val metaLine = listOfNotNull(
                                    st.className.takeIf { it.isNotBlank() }?.let { "Class $it" },
                                    st.section.takeIf { it.isNotBlank() }?.let { "Sec $it" },
                                    st.studentCode.takeIf { it.isNotBlank() }?.let { "ID: $it" }
                                ).joinToString(" · ")
                                if (metaLine.isNotBlank()) {
                                    Text(metaLine, fontSize = 11.sp, color = Color(0xFF475569))
                                }
                            }
                            Row {
                                TextButton(onClick = { activeStudentId = st.localId }) {
                                    Text(if (isSelected) "Active" else "Resume", fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = {
                                        students = students.filterNot { it.localId == st.localId }
                                        reviewQueue = reviewQueue.filterNot { it.studentLocalId == st.localId }
                                        if (activeStudentId == st.localId) activeStudentId = students.lastOrNull()?.localId
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(17.dp))
                                }
                            }
                        }

                        if (st.summativeMarks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Summative (${st.summativeMarks.size}):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF047857))
                            Text(
                                text = st.summativeMarks.entries.joinToString("  |  ") { "${it.key}: ${it.value}" },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF334155)
                            )
                        }
                        if (st.formativeMarks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Formative (${st.formativeMarks.size}):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                            Text(
                                text = st.formativeMarks.entries.joinToString("  |  ") { "${it.key}: ${it.value}" },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF334155)
                            )
                        }
                        if (st.lpcdResponses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("LPCD (${st.lpcdResponses.size}):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6D28D9))
                            Text(
                                text = st.lpcdResponses.entries.joinToString("  |  ") { "${it.key}: ${it.value}" },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF334155)
                            )
                        }
                        if (st.bcoResponses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("BCO (${st.bcoResponses.size}):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
                            Text(
                                text = st.bcoResponses.entries.joinToString("  |  ") { "${it.key}: ${it.value}" },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        } else {
            // Original_Format preview table
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .padding(10.dp)
                    ) {
                        Row(modifier = Modifier.background(primaryBlue)) {
                            HprcTableCell("ROLL", 56.dp, true)
                            HprcTableCell("STUDENT NAME", 140.dp, true)
                            HprcTableCell("SECTION", 84.dp, true)
                            HprcTableCell("SUBJECT / ITEM", 160.dp, true)
                            HprcTableCell("T1", 56.dp, true)
                            HprcTableCell("T2", 56.dp, true)
                            HprcTableCell("T3", 56.dp, true)
                            HprcTableCell("TOTAL", 64.dp, true)
                        }
                        students.forEach { st ->
                            val groupedItems = buildList {
                                val sumSubjs = st.summativeMarks.keys.map { it.substringBefore("|") }.distinct()
                                sumSubjs.forEach { s ->
                                    add(
                                        listOf(
                                            st.rollNo, st.studentName, "Summative", s,
                                            st.summativeMarks["$s|T1"].orEmpty(),
                                            st.summativeMarks["$s|T2"].orEmpty(),
                                            st.summativeMarks["$s|T3"].orEmpty(),
                                            st.summativeMarks["$s|TOTAL"].orEmpty()
                                        )
                                    )
                                }
                                val formItems = st.formativeMarks.keys.map { it.substringBefore("|") }.distinct()
                                formItems.forEach { f ->
                                    add(
                                        listOf(
                                            st.rollNo, st.studentName, "Formative", f,
                                            st.formativeMarks["$f|T1"].orEmpty(),
                                            st.formativeMarks["$f|T2"].orEmpty(),
                                            st.formativeMarks["$f|T3"].orEmpty(),
                                            ""
                                        )
                                    )
                                }
                                val lpcdItems = st.lpcdResponses.keys.map { it.substringBefore("|") }.distinct()
                                lpcdItems.forEach { l ->
                                    add(
                                        listOf(
                                            st.rollNo, st.studentName, "LPCD", l,
                                            st.lpcdResponses["$l|T1"].orEmpty(),
                                            st.lpcdResponses["$l|T2"].orEmpty(),
                                            st.lpcdResponses["$l|T3"].orEmpty(),
                                            ""
                                        )
                                    )
                                }
                                val bcoItems = st.bcoResponses.keys.map { it.substringBefore("|") }.distinct()
                                bcoItems.forEach { b ->
                                    add(
                                        listOf(
                                            st.rollNo, st.studentName, "BCO", b,
                                            st.bcoResponses["$b|T1"].orEmpty(),
                                            st.bcoResponses["$b|T2"].orEmpty(),
                                            st.bcoResponses["$b|T3"].orEmpty(),
                                            ""
                                        )
                                    )
                                }
                            }
                            groupedItems.forEach { row ->
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                Row {
                                    HprcTableCell(row[0], 56.dp, false)
                                    HprcTableCell(row[1], 140.dp, false)
                                    HprcTableCell(row[2], 84.dp, false)
                                    HprcTableCell(row[3], 160.dp, false)
                                    HprcTableCell(row[4], 56.dp, false)
                                    HprcTableCell(row[5], 56.dp, false)
                                    HprcTableCell(row[6], 56.dp, false)
                                    HprcTableCell(row[7], 64.dp, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Review Queue Manual Verification Dialog
    val qEdit = editingQueueItem
    if (qEdit != null) {
        AlertDialog(
            onDismissRequest = { editingQueueItem = null },
            title = { Text("Verify OCR Field (${qEdit.section})", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${qEdit.studentLabel} · ${qEdit.fieldKey}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Raw OCR: ${qEdit.rawOcrText}", fontSize = 11.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = editingQueueValue,
                        onValueChange = { editingQueueValue = it },
                        label = { Text("Verified Mark / Grade") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanVal = editingQueueValue.trim()
                        if (cleanVal.isNotBlank()) {
                            students = students.map { st ->
                                if (st.localId != qEdit.studentLocalId) st
                                else when (qEdit.section.lowercase(Locale.US)) {
                                    "formative" -> st.copy(formativeMarks = st.formativeMarks + (qEdit.fieldKey to cleanVal))
                                    "lpcd" -> st.copy(lpcdResponses = st.lpcdResponses + (qEdit.fieldKey to cleanVal))
                                    "bco" -> st.copy(bcoResponses = st.bcoResponses + (qEdit.fieldKey to cleanVal))
                                    else -> st.copy(summativeMarks = st.summativeMarks + (qEdit.fieldKey to cleanVal))
                                }
                            }
                        }
                        reviewQueue = reviewQueue.map { if (it.id == qEdit.id) it.copy(resolved = true) else it }
                        editingQueueItem = null
                    }
                ) { Text("Save Value") }
            },
            dismissButton = {
                TextButton(onClick = { editingQueueItem = null }) { Text("Cancel") }
            }
        )
    }

    // Safe Portal Fill Confirmation & Dry-Run Preview Dialog (NEVER auto-submits final form)
    if (showFillDialog) {
        val ctx = fillContext
        val fillableCount = fillPreviewRows.count { it.fieldsToFill.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { showFillDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = accentPurple)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Portal Auto-Fill", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (ctx != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Detected Portal Category: ${ctx.category}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryBlue)
                                val meta = listOfNotNull(
                                    ctx.academicYear.takeIf { it.isNotBlank() }?.let { "Year: $it" },
                                    ctx.className.takeIf { it.isNotBlank() }?.let { "Class: $it" },
                                    ctx.section.takeIf { it.isNotBlank() }?.let { "Sec: $it" },
                                    ctx.subject.takeIf { it.isNotBlank() }?.let { "Subject: $it" },
                                    ctx.term.takeIf { it.isNotBlank() }?.let { "Term: $it" }
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Text("Portal Writable Rows: ${ctx.writableRowCount} · Matched Fillable: $fillableCount", fontSize = 11.sp, color = Color(0xFF334155))
                            }
                        }
                    }

                    if (fillPreviewRows.isEmpty()) {
                        Text(
                            text = "No matching writable students found on the current Portal page. Open Formative / Summative / LPCD / BCO entry page first.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    } else {
                        fillPreviewRows.take(15).forEach { pr ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Roll ${pr.portalRoll.ifBlank { pr.scannedRoll }} · ${pr.portalStudentName.ifBlank { pr.scannedName }}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Match: ${pr.matchReason}", fontSize = 10.sp, color = Color(0xFF475569))
                                    if (pr.fieldsToFill.isNotEmpty()) {
                                        Text(
                                            text = "Will fill: " + pr.fieldsToFill.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                            fontSize = 11.sp,
                                            color = accentGreen
                                        )
                                    } else {
                                        Text(pr.skippedReason, fontSize = 11.sp, color = Color(0xFFB45309))
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Safety rule: Only matching writable fields are filled. Final portal submission is NEVER triggered automatically — review on Portal and submit yourself.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = fillableCount > 0,
                    onClick = {
                        val wv = portalWebViewProvider()
                        showFillDialog = false
                        if (wv != null) {
                            wv.evaluateJavascript(PortalHrpcDomScripts.applyHprcFillPlanJs(fillPlanJson)) { res ->
                                val decoded = runCatching {
                                    if (res != null && res.startsWith("\"")) JSONArray("[$res]").getString(0) else res.orEmpty()
                                }.getOrDefault("{}")
                                val resObj = runCatching { JSONObject(decoded) }.getOrNull()
                                val rowsCount = resObj?.optInt("updatedRows", 0) ?: 0
                                val fieldCount = resObj?.optInt("filledFields", 0) ?: 0
                                statusMessage = "Filled $fieldCount field(s) across $rowsCount student row(s) on Portal (NOT auto-submitted). Review & Submit on Portal."
                                onOpenPortal()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                ) {
                    Text("Fill Portal Fields ($fillableCount)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFillDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun LiveCameraOcrView(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    activeStudentLocalId: String,
    activeStudentLabel: String,
    onThrottleCheck: (Long) -> Boolean,
    onPageParsed: (HprcParsedPageResult) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recognizer.close() }
            runCatching { cameraExecutor.shutdown() }
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp)),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                val mediaImage = imageProxy.image
                                if (mediaImage == null || !onThrottleCheck(now)) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImg = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                recognizer.process(inputImg)
                                    .addOnSuccessListener(mainExecutor) { visionText ->
                                        val parsed = HprcOcrParser.parseMlKitText(
                                            visionText = visionText,
                                            currentStudentLocalId = activeStudentLocalId,
                                            currentStudentLabel = activeStudentLabel
                                        )
                                        if (parsed.hasUsefulData) {
                                            onPageParsed(parsed)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                        }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    }
                },
                mainExecutor
            )
            previewView
        }
    )
}

@Composable
private fun HprcTableCell(text: String, width: androidx.compose.ui.unit.Dp, isHeader: Boolean) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 10.sp else 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeader) Color.White else Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun namesLikelySame(a: String, b: String): Boolean {
    val na = a.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    val nb = b.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    if (na.isBlank() || nb.isBlank()) return false
    return na == nb || na.contains(nb) || nb.contains(na)
}

private fun findBestStudentMatch(
    students: List<HprcStudentScanRecord>,
    portalCode: String,
    portalRoll: String,
    portalName: String
): Pair<HprcStudentScanRecord, String>? {
    if (portalCode.isNotBlank()) {
        students.firstOrNull { it.studentCode == portalCode }?.let {
            return it to "Exact Student Code ($portalCode)"
        }
    }
    if (portalRoll.isNotBlank() && portalName.isNotBlank()) {
        students.firstOrNull { it.rollNo == portalRoll && namesLikelySame(it.studentName, portalName) }?.let {
            return it to "Roll $portalRoll + Name"
        }
    }
    if (portalName.isNotBlank()) {
        students.firstOrNull { namesLikelySame(it.studentName, portalName) }?.let {
            return it to "Name Match"
        }
    }
    if (portalRoll.isNotBlank()) {
        students.firstOrNull { it.rollNo == portalRoll }?.let {
            return it to "Roll Match ($portalRoll)"
        }
    }
    return null
}

private fun resolveValueForPortalField(
    student: HprcStudentScanRecord,
    category: String,
    pageSubject: String,
    pageTerm: String,
    fieldLabel: String,
    fieldIndex: Int
): String? {
    val map = when (category.lowercase(Locale.US)) {
        "formative" -> student.formativeMarks
        "lpcd" -> student.lpcdResponses
        "bco" -> student.bcoResponses
        else -> student.summativeMarks
    }
    if (map.isEmpty()) return null

    val lowerLabel = "$fieldLabel $pageTerm".lowercase(Locale.US)
    val wantedTerm = when {
        Regex("1st|first|term\\s*1|\\bt1\\b").containsMatchIn(lowerLabel) -> "T1"
        Regex("2nd|second|term\\s*2|\\bt2\\b").containsMatchIn(lowerLabel) -> "T2"
        Regex("3rd|third|term\\s*3|\\bt3\\b").containsMatchIn(lowerLabel) -> "T3"
        else -> null
    }

    // Check matching subject or indicator first
    val candidateContext = "$pageSubject $fieldLabel".lowercase(Locale.US)
    for ((k, v) in map) {
        val parts = k.split("|")
        val itemPart = parts.getOrNull(0)?.lowercase(Locale.US).orEmpty()
        val termPart = parts.getOrNull(1).orEmpty()
        val subjMatched = pageSubject.isNotBlank() && (candidateContext.contains(itemPart) || itemPart.contains(pageSubject.lowercase(Locale.US)))
        val fieldMatched = fieldLabel.lowercase(Locale.US).contains(itemPart)
        if ((subjMatched || fieldMatched) && (wantedTerm == null || wantedTerm == termPart)) {
            return v
        }
    }

    // Fallback: match by term across selected pageSubject or ordered entries
    if (wantedTerm != null) {
        map.entries.firstOrNull { it.key.endsWith("|$wantedTerm") }?.let { return it.value }
    }
    return map.values.elementAtOrNull(fieldIndex)
}
