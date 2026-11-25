package com.bspsms.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkMarksheetScreen(
    controller: MarksheetAutomationController,
    locale: AppLocale,
    onPickSafFolder: () -> Unit,
    onOpenPortal: () -> Unit
) {
    val state by controller.bulkState.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Students / Controls, 1 = History, 2 = Failed
    var manualOpenRoll by remember { mutableStateOf("") }
    var manualOpenName by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF123B70)
    val accentGreen = Color(0xFF15803D)
    val isRunning = state.jobStatus == AutomationJobStatus.RUNNING
    val isPaused = state.jobStatus == AutomationJobStatus.PAUSED
    val progressFraction = if (state.totalStudents > 0) {
        (state.completedDownloads.toFloat() / state.totalStudents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title & View Portal shortcut
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (locale == AppLocale.BN) "বাল্ক মার্কশিট ডাউনলোডার" else "BULK MARKSHEET DOWNLOADER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Automatic & Manual HRPC Marksheet Download",
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

        // Mode selector (Auto vs Manual)
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == BulkDownloaderMode.AUTO,
                    onClick = { controller.setBulkMode(BulkDownloaderMode.AUTO) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(if (locale == AppLocale.BN) "অটো মোড" else "Auto Mode", fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = state.mode == BulkDownloaderMode.MANUAL,
                    onClick = { controller.setBulkMode(BulkDownloaderMode.MANUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(if (locale == AppLocale.BN) "ম্যানুয়াল মোড" else "Manual Mode", fontSize = 12.sp)
                }
            }
        }

        // SAF Destination Folder Picker Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (state.hasSafFolder) Color(0xFFBBF7D0) else Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = if (state.hasSafFolder) accentGreen else Color(0xFFB45309),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (state.hasSafFolder) "Destination Folder Ready (Direct Save)" else "Select Destination Folder (Required)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (state.hasSafFolder) state.safFolderLabel else "No folder chosen — saves silently with zero prompts once chosen.",
                                fontSize = 11.sp,
                                color = Color(0xFF475569),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPickSafFolder,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (state.hasSafFolder) "Change" else "Choose Folder", fontSize = 11.sp)
                    }
                }
            }
        }

        // Marksheet Pages 1..5 Selector
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select Marksheet Pages (5 Total Pages)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (state.selectedPages.isEmpty()) {
                                    "None selected -> All 5 pages will be downloaded"
                                } else {
                                    "Selected: Pages ${state.selectedPages.sorted().joinToString(", ")}"
                                },
                                fontSize = 11.sp,
                                color = primaryBlue
                            )
                        }
                        Row {
                            TextButton(
                                onClick = { controller.selectAllPages() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("All 5", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = { controller.clearSelectedPages() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (1..5).forEach { p ->
                            val checked = state.selectedPages.contains(p)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { controller.toggleSelectedPage(p) }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { controller.toggleSelectedPage(p) },
                                    colors = CheckboxDefaults.colors(checkedColor = primaryBlue),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("P$p", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
            }
        }

        // Dashboard Counters + Live Progress
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricBox("Total", state.totalStudents.toString(), primaryBlue, Modifier.weight(1f))
                        MetricBox("Completed", state.completedDownloads.toString(), accentGreen, Modifier.weight(1f))
                        MetricBox("Pending", state.pendingDownloads.toString(), Color(0xFF0369A1), Modifier.weight(1f))
                        MetricBox("Failed", state.failedDownloadsCount.toString(), Color(0xFFDC2626), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (state.failedDownloadsCount > 0 && !isRunning) Color(0xFFD97706) else accentGreen,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page: ${state.currentPageNumber}/${state.totalPages}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = if (state.currentRollNo.isNotBlank() || state.currentStudentName.isNotBlank()) {
                                "Current: ${state.currentRollNo} · ${state.currentStudentName}"
                            } else {
                                "Status: ${state.jobStatus.name}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = if (state.isError) Color(0xFFFEF2F2) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.statusMessage,
                            fontSize = 11.sp,
                            color = if (state.isError) Color(0xFF991B1B) else Color(0xFF334155),
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = !isRunning,
                            onClick = { controller.scanCurrentTableForBulk() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Table", fontSize = 12.sp, maxLines = 1)
                        }

                        if (state.mode == BulkDownloaderMode.AUTO) {
                            when {
                                !isRunning && !isPaused -> {
                                    Button(
                                        onClick = { controller.startBulkAutoDownload(clearPreviousHistory = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                        modifier = Modifier.weight(1.3f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Start Auto", fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                isRunning -> {
                                    Button(
                                        onClick = { controller.pauseBulk() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Pause", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { controller.stopBulk() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                }
                                isPaused -> {
                                    Button(
                                        onClick = { controller.resumeBulk() },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Resume", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { controller.stopBulk() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (state.failedDownloadsCount > 0 && !isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { controller.retryFailedBulkDownloads() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Failed Downloads (${state.failedDownloadsCount})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Manual Mode Open Marksheet direct capture helper
        if (state.mode == BulkDownloaderMode.MANUAL) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Manual Mode — Save Currently Open Marksheet",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryBlue
                        )
                        Text(
                            text = "Already opened a student's Print/View marksheet in Portal? Enter Roll & Name or tap Download on any student below.",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualOpenRoll,
                                onValueChange = { manualOpenRoll = it },
                                label = { Text("Roll No", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.35f)
                            )
                            OutlinedTextField(
                                value = manualOpenName,
                                onValueChange = { manualOpenName = it },
                                label = { Text("Student Name", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.65f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            enabled = state.singleBusyKey == null && !isRunning,
                            onClick = { controller.captureOpenMarksheetManually(manualOpenRoll, manualOpenName) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Currently Open Marksheet PDF", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Sub-Tabs: Student List | Download History | Failed List
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Page List (${state.scannedStudents.size})", fontSize = 11.sp, maxLines = 1)
                }
                SegmentedButton(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("History (${state.downloadHistory.size})", fontSize = 11.sp, maxLines = 1)
                }
                SegmentedButton(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Failed (${state.failedList.size})", fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        when (activeTab) {
            0 -> {
                if (state.scannedStudents.isEmpty()) {
                    item {
                        EmptyListNotice("No students scanned yet. Open the HRPC Student Information table in Portal after clicking Search, then tap Scan Table.")
                    }
                } else {
                    items(state.scannedStudents, key = { "${it.rowIndex}_${it.rollNo}_${it.name}" }) { student ->
                        val isDone = state.completedKeys.contains(student.uniqueKey)
                        val isThisBusy = state.singleBusyKey == student.uniqueKey
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, if (isDone) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${student.rollNo} · ${student.name}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = SafStorageHelper.sanitizeMarksheetFileName(student.rollNo, student.name, "pdf"),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isThisBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isDone) {
                                            Icon(
                                                Icons.Outlined.CheckCircle,
                                                contentDescription = "Downloaded",
                                                tint = accentGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        OutlinedButton(
                                            enabled = !isRunning && state.singleBusyKey == null,
                                            onClick = { controller.downloadSingleStudentManual(student) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isDone) "Redownload" else "Download", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (state.downloadHistory.isEmpty()) {
                    item {
                        EmptyListNotice("No marksheets downloaded yet in this session.")
                    }
                } else {
                    items(state.downloadHistory) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = accentGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.fileName, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                    Text(
                                        "Roll: ${item.rollNo} · ${item.studentName} · Page ${item.pageNumber} · PDF Pages [${item.pagesSaved.joinToString(",")}] · ${item.timestamp}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (state.failedList.isEmpty()) {
                    item {
                        EmptyListNotice("No failed downloads.")
                    }
                } else {
                    items(state.failedList) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${item.rollNo} · ${item.studentName} (Table Page ${item.pageNumber})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                    Text(item.reason, fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyListNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(14.dp)
        )
    }
}
