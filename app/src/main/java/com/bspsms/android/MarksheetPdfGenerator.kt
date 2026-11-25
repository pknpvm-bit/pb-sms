package com.bspsms.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class MarksheetLayoutInfo(
    val contentLeftPx: Float,
    val contentTopPx: Float,
    val contentWidthPx: Float,
    val contentHeightPx: Float,
    val totalLogicalPages: Int,
    val pageSlicesPx: List<Pair<Float, Float>>
)

object MarksheetPdfGenerator {
    // Standard A4 in PDF points (72 dpi)
    private const val A4_PT_WIDTH = 595
    private const val A4_PT_HEIGHT = 842

    // High-resolution raster capture buffer (~150 dpi A4) for crisp Bangla & English text/tables
    private const val RENDER_BMP_WIDTH = 1190
    private const val RENDER_BMP_HEIGHT = 1684

    suspend fun captureSelectedPagesToPdfBytes(
        webView: WebView,
        selectedPagesInput: List<Int>,
        totalExpectedPages: Int = 5
    ): Result<ByteArray> {
        val normalizedSelected = if (selectedPagesInput.isEmpty()) {
            (1..totalExpectedPages).toList()
        } else {
            selectedPagesInput.filter { it in 1..totalExpectedPages }.distinct().sorted()
        }.ifEmpty { (1..totalExpectedPages).toList() }

        val layoutResult = withContext(Dispatchers.Main) {
            runCatching {
                applyCleanPrintStyles(webView)
                inspectMarksheetLayout(webView, totalExpectedPages)
            }
        }

        val layout = layoutResult.getOrElse {
            withContext(Dispatchers.Main) { runCatching { restoreCleanPrintStyles(webView) } }
            return Result.failure(it)
        }

        return try {
            val scale = webView.scale.takeIf { it > 0.1f } ?: 1f
            val doc = PdfDocument()
            try {
                var outPageNum = 1
                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                for (logicalPage in normalizedSelected) {
                    val idx = logicalPage - 1
                    val slice = layout.pageSlicesPx.getOrNull(idx) ?: continue
                    val sliceTopPx = max(0f, slice.first)
                    val sliceHeightPx = max(40f, slice.second)

                    val pageBitmap = withContext(Dispatchers.Main) {
                        val bmp = Bitmap.createBitmap(RENDER_BMP_WIDTH, RENDER_BMP_HEIGHT, Bitmap.Config.ARGB_8888)
                        val bmpCanvas = Canvas(bmp)
                        bmpCanvas.drawColor(Color.WHITE)

                        val contentScaledW = max(120f, layout.contentWidthPx * scale)
                        val contentScaledH = max(120f, sliceHeightPx * scale)

                        // Fit full marksheet page cleanly inside A4 with small print margin
                        val marginPx = 22f
                        val availW = RENDER_BMP_WIDTH - marginPx * 2f
                        val availH = RENDER_BMP_HEIGHT - marginPx * 2f
                        val drawScale = min(availW / contentScaledW, availH / contentScaledH).coerceAtLeast(0.05f)

                        val renderedW = max(1f, contentScaledW * drawScale)
                        val renderedH = max(1f, min(availH, contentScaledH * drawScale))
                        val offsetX = max(0f, (RENDER_BMP_WIDTH - renderedW) / 2f)

                        bmpCanvas.save()
                        bmpCanvas.translate(offsetX, marginPx)
                        bmpCanvas.clipRect(0f, 0f, renderedW, renderedH)
                        bmpCanvas.scale(drawScale, drawScale)
                        bmpCanvas.translate(
                            -(max(0f, layout.contentLeftPx) * scale),
                            -(sliceTopPx * scale)
                        )
                        webView.draw(bmpCanvas)
                        bmpCanvas.restore()
                        bmp
                    }

                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(A4_PT_WIDTH, A4_PT_HEIGHT, outPageNum++).create()
                        val pdfPage = doc.startPage(pageInfo)
                        pdfPage.canvas.drawColor(Color.WHITE)
                        pdfPage.canvas.drawBitmap(
                            pageBitmap,
                            Rect(0, 0, pageBitmap.width, pageBitmap.height),
                            Rect(0, 0, A4_PT_WIDTH, A4_PT_HEIGHT),
                            drawPaint
                        )
                        doc.finishPage(pdfPage)
                    } finally {
                        pageBitmap.recycle()
                    }
                }

                if (outPageNum == 1) {
                    error("No printable marksheet pages matched selection.")
                }

                val baos = ByteArrayOutputStream()
                doc.writeTo(baos)
                Result.success(baos.toByteArray())
            } finally {
                runCatching { doc.close() }
                withContext(Dispatchers.Main) { runCatching { restoreCleanPrintStyles(webView) } }
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) { runCatching { restoreCleanPrintStyles(webView) } }
            Result.failure(t)
        }
    }

    fun filterExistingPdfBytes(
        rawPdfBytes: ByteArray,
        selectedPagesInput: List<Int>,
        cacheDir: File,
        outStream: OutputStream
    ): Result<Int> {
        return runCatching {
            val tempFile = File.createTempFile("bsp_raw_ms_", ".pdf", cacheDir)
            try {
                tempFile.writeBytes(rawPdfBytes)
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pageCount = renderer.pageCount
                        if (pageCount <= 0) error("Empty PDF received from portal")
                        val targetPages = if (selectedPagesInput.isEmpty()) {
                            (1..pageCount).toList()
                        } else {
                            selectedPagesInput.filter { it in 1..pageCount }.distinct().sorted()
                        }.ifEmpty { (1..pageCount).toList() }

                        val outDoc = PdfDocument()
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        try {
                            var outNum = 1
                            for (pageNum1Based in targetPages) {
                                renderer.openPage(pageNum1Based - 1).use { srcPage ->
                                    val w = A4_PT_WIDTH
                                    val h = A4_PT_HEIGHT
                                    val renderW = RENDER_BMP_WIDTH
                                    val renderH = (renderW.toFloat() * srcPage.height / max(1, srcPage.width)).roundToInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                    try {
                                        bmp.eraseColor(Color.WHITE)
                                        srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, outNum++).create()
                                        val page = outDoc.startPage(pageInfo)
                                        page.canvas.drawColor(Color.WHITE)
                                        val scale = min(w.toFloat() / renderW, h.toFloat() / renderH)
                                        val dstW = (renderW * scale).roundToInt()
                                        val dstH = (renderH * scale).roundToInt()
                                        val dx = (w - dstW) / 2
                                        val dy = (h - dstH) / 2
                                        page.canvas.drawBitmap(
                                            bmp,
                                            Rect(0, 0, renderW, renderH),
                                            Rect(dx, dy, dx + dstW, dy + dstH),
                                            paint
                                        )
                                        outDoc.finishPage(page)
                                    } finally {
                                        bmp.recycle()
                                    }
                                }
                            }
                            outDoc.writeTo(outStream)
                            outStream.flush()
                            outNum - 1
                        } finally {
                            runCatching { outDoc.close() }
                        }
                    }
                }
            } finally {
                runCatching { tempFile.delete() }
            }
        }
    }

    private suspend fun applyCleanPrintStyles(webView: WebView) {
        evalJs(
            webView,
            """
            (function() {
                window.scrollTo(0, 0);
                var old = document.getElementById('__bspPrintStyleFix');
                if (old) old.remove();
                var st = document.createElement('style');
                st.id = '__bspPrintStyleFix';
                st.textContent =
                    'header.main-header, aside.main-sidebar, nav.navbar, .sidebar, .breadcrumb, .no-print, .btn-print, button.print-btn { display: none !important; }' +
                    'html, body, .content-wrapper, .wrapper { background: #ffffff !important; margin: 0 !important; padding: 0 !important; overflow: visible !important; height: auto !important; }';
                (document.head || document.documentElement).appendChild(st);
                return 'ok';
            })();
            """.trimIndent()
        )
    }

    private suspend fun restoreCleanPrintStyles(webView: WebView) {
        evalJs(
            webView,
            """
            (function() {
                var st = document.getElementById('__bspPrintStyleFix');
                if (st) st.remove();
                return 'ok';
            })();
            """.trimIndent()
        )
    }

    private suspend fun inspectMarksheetLayout(
        webView: WebView,
        totalExpectedPages: Int
    ): MarksheetLayoutInfo {
        val rawJson = evalJs(
            webView,
            """
            (function() {
                var docEl = document.documentElement;
                var body = document.body;
                var scrollX = window.pageXOffset || docEl.scrollLeft || 0;
                var scrollY = window.pageYOffset || docEl.scrollTop || 0;
                var frameOffX = 0;
                var frameOffY = 0;
                var activeDoc = document;

                // Support marksheets rendered inside a visible same-origin iframe
                var frames = Array.from(document.querySelectorAll('iframe'));
                for (var fi = 0; fi < frames.length; fi++) {
                    try {
                        var fr = frames[fi];
                        var fDoc = fr.contentDocument;
                        var fRect = fr.getBoundingClientRect();
                        if (fDoc && fDoc.body && fRect.height > 200 && /Summative|Progress\s+Report|Mark\s*sheet/i.test(fDoc.body.innerText || '')) {
                            activeDoc = fDoc;
                            body = fDoc.body;
                            docEl = fDoc.documentElement || fDoc.body;
                            frameOffX = Math.max(0, fRect.left + scrollX);
                            frameOffY = Math.max(0, fRect.top + scrollY);
                            scrollX = 0;
                            scrollY = 0;
                            break;
                        }
                    } catch (e) {}
                }

                // Try finding dedicated printable marksheet container
                var rootCandidates = [
                    activeDoc.querySelector('#printableArea, #printArea, #marksheetArea, .marksheet-wrapper, .report-card-container, .print-container'),
                    activeDoc.querySelector('.content-wrapper .content, .content-wrapper, main, #content'),
                    body
                ];
                var container = body;
                for (var i = 0; i < rootCandidates.length; i++) {
                    var c = rootCandidates[i];
                    if (c && c.getBoundingClientRect().height > 200 && (c.innerText || '').trim().length > 40) {
                        container = c;
                        break;
                    }
                }

                var rect = container.getBoundingClientRect();
                var cLeft = Math.max(0, frameOffX + rect.left + scrollX);
                var cTop = Math.max(0, frameOffY + rect.top + scrollY);
                var cWidth = Math.max(container.scrollWidth || 0, rect.width || 0, body.scrollWidth || 0, 760);
                var cHeight = Math.max(container.scrollHeight || 0, rect.height || 0, body.scrollHeight || 0, 1000);

                // Look for explicit marksheet page blocks (e.g., 5 sheets / cards / page-break containers)
                var pageSelectors = [
                    'section.sheet',
                    '.sheet',
                    '.print-page',
                    '.marksheet-page',
                    '.report-page',
                    '.page-break',
                    '[style*="page-break-after"]',
                    '[style*="break-after"]'
                ];
                var slices = [];
                for (var s = 0; s < pageSelectors.length; s++) {
                    var els = container.querySelectorAll(pageSelectors[s]);
                    var valid = [];
                    for (var j = 0; j < els.length; j++) {
                        var er = els[j].getBoundingClientRect();
                        if (er.height >= 160 && er.width >= 200) {
                            valid.push({
                                top: Math.max(0, frameOffY + er.top + scrollY),
                                height: er.height
                            });
                        }
                    }
                    if (valid.length >= 2 && valid.length <= 8) {
                        slices = valid;
                        break;
                    }
                }

                // Fallback: divide continuous printable area cleanly into N logical A4 pages
                if (slices.length === 0) {
                    var targetN = ${totalExpectedPages};
                    var stepH = cHeight / targetN;
                    // If height is much shorter than multi-page (e.g. responsive narrow print), ensure sensible minimum slice
                    for (var p = 0; p < targetN; p++) {
                        slices.push({
                            top: cTop + (p * stepH),
                            height: stepH
                        });
                    }
                }

                return JSON.stringify({
                    left: cLeft,
                    top: cTop,
                    width: cWidth,
                    height: cHeight,
                    slices: slices
                });
            })();
            """.trimIndent()
        )

        val obj = JSONObject(rawJson)
        val slicesArr = obj.optJSONArray("slices") ?: JSONArray()
        val parsedSlices = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until slicesArr.length()) {
            val s = slicesArr.optJSONObject(i) ?: continue
            parsedSlices.add(
                s.optDouble("top", 0.0).toFloat() to s.optDouble("height", 800.0).toFloat()
            )
        }
        return MarksheetLayoutInfo(
            contentLeftPx = obj.optDouble("left", 0.0).toFloat(),
            contentTopPx = obj.optDouble("top", 0.0).toFloat(),
            contentWidthPx = obj.optDouble("width", 794.0).toFloat(),
            contentHeightPx = obj.optDouble("height", 1123.0).toFloat(),
            totalLogicalPages = parsedSlices.size,
            pageSlicesPx = parsedSlices
        )
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
}
