package com.bspsms.android

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultReadyScreen(
    controller: MarksheetAutomationController,
    locale: AppLocale,
    onPickSafFolder: () -> Unit,
    onCreateExcelDocument: (suggestedFileName: String) -> Unit,
    onOpenPortal: () -> Unit
) {
    val state by controller.resultState.collectAsState()
    val primaryBlue = Color(0xFF123B70)
    val accentGreen = Color(0xFF15803D)
    val isRunning = state.jobStatus == AutomationJobStatus.RUNNING
    val isPaused = state.jobStatus == AutomationJobStatus.PAUSED
    val progressFraction = if (state.totalStudents > 0) {
        (state.completedCount.toFloat() / state.totalStudents.toFloat()).coerceIn(0f, 1f)
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (locale == AppLocale.BN) "রেজাল্ট রেডি টুল" else "RESULT READY TOOL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Extract Summative Evaluation marks directly to Excel (.xlsx)",
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

        // Control & Live Progress Card
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
                        ResultMetricCard("Total", state.totalStudents.toString(), primaryBlue, Modifier.weight(1f))
                        ResultMetricCard("Extracted", state.completedCount.toString(), accentGreen, Modifier.weight(1f))
                        ResultMetricCard("Pending", state.pendingCount.toString(), Color(0xFF0369A1), Modifier.weight(1f))
                        ResultMetricCard("Failed", state.failedCount.toString(), Color(0xFFDC2626), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = accentGreen,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = !isRunning,
                            onClick = { controller.scanCurrentTableForResult() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Table", fontSize = 12.sp, maxLines = 1)
                        }

                        when {
                            !isRunning && !isPaused -> {
                                Button(
                                    onClick = { controller.startResultExtraction(clearPrevious = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    modifier = Modifier.weight(1.3f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Extraction", fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            isRunning -> {
                                Button(
                                    onClick = { controller.pauseResult() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pause", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { controller.stopResult() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Stop", fontSize = 12.sp)
                                }
                            }
                            isPaused -> {
                                Button(
                                    onClick = { controller.resumeResult() },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Resume", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { controller.stopResult() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Stop", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (state.failedCount > 0 && !isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { controller.retryFailedResultExtractions() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Failed Extractions (${state.failedCount})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Excel Export (.xlsx) Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TableChart, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Generate & Download Excel (.xlsx)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "${state.extractedRows.size} student row(s) ready · Offline native OOXML .xlsx generator",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = state.extractedRows.isNotEmpty(),
                            onClick = {
                                val fileName = "Result_Ready_${System.currentTimeMillis() / 1000}.xlsx"
                                onCreateExcelDocument(fileName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download .xlsx", fontSize = 12.sp, maxLines = 1)
                        }

                        if (state.hasSafFolder) {
                            OutlinedButton(
                                enabled = state.extractedRows.isNotEmpty(),
                                onClick = { controller.exportResultWorkbookToSafFolder() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save to Folder", fontSize = 11.sp, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onPickSafFolder,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Folder", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    if (state.lastExportedFileName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Saved: ${state.lastExportedFileName}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = accentGreen
                        )
                    }
                }
            }
        }

        // Scrollable Live Spreadsheet Preview with exact required columns
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Extracted Result Sheet Preview (${state.extractedRows.size} Students)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val hScroll = rememberScrollState()
                    val previewSchema = state.subjectSchema.ifEmpty {
                        ResultXlsxExporter.resolveSubjectSchema(emptyList())
                    }
                    val previewHeaders = state.termHeaders
                    val termWidthDp = ((previewSchema.size + 1) * 54).dp
                    val subjTotalWidthDp = (previewSchema.size * 64).dp
                    val totalTableWidthDp = 200.dp + termWidthDp + termWidthDp + termWidthDp + subjTotalWidthDp + 215.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                    ) {
                        // Dynamic Group Header Row 1
                        Row {
                            HeaderGroupCell("STUDENT", 200.dp, primaryBlue)
                            HeaderGroupCell(previewHeaders.term1Header, termWidthDp, Color(0xFF1D4ED8))
                            HeaderGroupCell(previewHeaders.term2Header, termWidthDp, Color(0xFF047857))
                            HeaderGroupCell(previewHeaders.term3Header, termWidthDp, Color(0xFF6D28D9))
                            HeaderGroupCell("SUBJECT TOTAL", subjTotalWidthDp, Color(0xFFB45309))
                            HeaderGroupCell("FINAL", 215.dp, primaryBlue)
                        }
                        // Dynamic Column Header Row 2
                        Row {
                            ColCell("ROLL NO", 64.dp, primaryBlue, isHeader = true)
                            ColCell("STUDENT NAME", 136.dp, primaryBlue, isHeader = true)
                            previewSchema.forEach { sub ->
                                ColCell(sub.code, 54.dp, Color(0xFF1D4ED8), isHeader = true)
                            }
                            ColCell("TOTAL", 54.dp, Color(0xFF1D4ED8), isHeader = true)

                            previewSchema.forEach { sub ->
                                ColCell(sub.code, 54.dp, Color(0xFF047857), isHeader = true)
                            }
                            ColCell("TOTAL", 54.dp, Color(0xFF047857), isHeader = true)

                            previewSchema.forEach { sub ->
                                ColCell(sub.code, 54.dp, Color(0xFF6D28D9), isHeader = true)
                            }
                            ColCell("TOTAL", 54.dp, Color(0xFF6D28D9), isHeader = true)

                            previewSchema.forEach { sub ->
                                ColCell("${sub.code} TOT", 64.dp, Color(0xFFB45309), isHeader = true)
                            }
                            ColCell("GRAND TOT", 75.dp, primaryBlue, isHeader = true)
                            ColCell("PERCENTAGE", 80.dp, primaryBlue, isHeader = true)
                            ColCell("POSITION", 60.dp, primaryBlue, isHeader = true)
                        }

                        if (state.extractedRows.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(totalTableWidthDp)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "No extracted rows yet. Tap 'Start Extraction' above on the HRPC Student Information table.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else {
                            state.extractedRows.forEach { row ->
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                Row {
                                    ColCell(row.rollNo, 64.dp, Color.White, isHeader = false)
                                    ColCell(row.studentName, 136.dp, Color.White, isHeader = false, leftAlign = true)
                                    previewSchema.forEach { sub ->
                                        ColCell(row.markFor(sub.key, 1), 54.dp, Color.White, isHeader = false)
                                    }
                                    ColCell(row.term1Total, 54.dp, Color(0xFFF8FAFC), isHeader = false, bold = true)

                                    previewSchema.forEach { sub ->
                                        ColCell(row.markFor(sub.key, 2), 54.dp, Color.White, isHeader = false)
                                    }
                                    ColCell(row.term2Total, 54.dp, Color(0xFFF8FAFC), isHeader = false, bold = true)

                                    previewSchema.forEach { sub ->
                                        ColCell(row.markFor(sub.key, 3), 54.dp, Color.White, isHeader = false)
                                    }
                                    ColCell(row.term3Total, 54.dp, Color(0xFFF8FAFC), isHeader = false, bold = true)

                                    previewSchema.forEach { sub ->
                                        ColCell(row.markFor(sub.key, 4), 64.dp, Color(0xFFFFFBEB), isHeader = false)
                                    }
                                    ColCell(row.grandTotal, 75.dp, Color(0xFFF0FDF4), isHeader = false, bold = true)
                                    ColCell(row.percentage, 80.dp, Color(0xFFF0FDF4), isHeader = false, bold = true)
                                    ColCell(row.position, 60.dp, Color(0xFFEFF6FF), isHeader = false, bold = true)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.failedList.isNotEmpty()) {
            items(state.failedList) { fail ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Failed: ${fail.rollNo} · ${fail.studentName} (Page ${fail.pageNumber})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B)
                        )
                        Text(text = fail.reason, fontSize = 11.sp, color = Color(0xFF7F1D1D))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
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
private fun HeaderGroupCell(text: String, width: Dp, bg: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ColCell(
    text: String,
    width: Dp,
    bg: Color,
    isHeader: Boolean,
    leftAlign: Boolean = false,
    bold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = if (leftAlign) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isHeader) Color.White else Color(0xFF0F172A),
            fontSize = if (isHeader) 9.sp else 11.sp,
            fontWeight = if (isHeader || bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (leftAlign) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
