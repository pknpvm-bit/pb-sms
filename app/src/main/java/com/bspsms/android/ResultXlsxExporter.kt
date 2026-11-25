package com.bspsms.android

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

data class DynamicSubjectDef(
    val key: String,
    val code: String,
    val displayName: String,
    val maxT1: Double = 20.0,
    val maxT2: Double = 30.0,
    val maxT3: Double = 50.0,
    val maxTotal: Double = 100.0
)

data class DynamicSubjectTermMarks(
    val t1: String = "",
    val t2: String = "",
    val t3: String = "",
    val total: String = ""
)

data class ResultTermHeaders(
    val term1Header: String = "1ST SUMMATIVE (20)",
    val term2Header: String = "2ND SUMMATIVE (30)",
    val term3Header: String = "3RD SUMMATIVE (50)"
)

data class StudentSummativeResultRow(
    val rollNo: String,
    val studentName: String,
    val orderedSubjects: List<DynamicSubjectDef>,
    val subjectMarks: Map<String, DynamicSubjectTermMarks>,
    val term1Total: String,
    val term2Total: String,
    val term3Total: String,
    val grandTotal: String,
    val fullMarksTotal: Double,
    val percentage: String,
    var position: String = "",
    val termHeaders: ResultTermHeaders = ResultTermHeaders()
) {
    fun grandTotalNumeric(): Double {
        return grandTotal.toDoubleOrNull() ?: -1.0
    }

    fun markFor(subjectKey: String, termIndex: Int): String {
        val m = subjectMarks[subjectKey] ?: return ""
        return when (termIndex) {
            1 -> m.t1
            2 -> m.t2
            3 -> m.t3
            else -> m.total
        }
    }
}

object ResultXlsxExporter {

    private val FALLBACK_SUBJECTS = listOf(
        DynamicSubjectDef("ben", "BEN", "Bengali", 20.0, 30.0, 50.0, 100.0),
        DynamicSubjectDef("eng", "ENG", "English", 20.0, 30.0, 50.0, 100.0),
        DynamicSubjectDef("evs", "EVS", "Our Environment", 20.0, 30.0, 50.0, 100.0),
        DynamicSubjectDef("math", "MATH", "Mathematics", 20.0, 30.0, 50.0, 100.0),
        DynamicSubjectDef("health", "HEALTH", "Health & Physical Ed.", 20.0, 30.0, 50.0, 100.0),
        DynamicSubjectDef("art", "ART", "Art & Work Ed.", 20.0, 30.0, 50.0, 100.0)
    )

    fun resolveSubjectSchema(rows: List<StudentSummativeResultRow>): List<DynamicSubjectDef> {
        if (rows.isEmpty()) return FALLBACK_SUBJECTS
        val ordered = LinkedHashMap<String, DynamicSubjectDef>()
        for (row in rows) {
            for (sub in row.orderedSubjects) {
                val existing = ordered[sub.key]
                if (existing == null) {
                    ordered[sub.key] = sub
                } else {
                    ordered[sub.key] = existing.copy(
                        maxT1 = maxOf(existing.maxT1, sub.maxT1),
                        maxT2 = maxOf(existing.maxT2, sub.maxT2),
                        maxT3 = maxOf(existing.maxT3, sub.maxT3),
                        maxTotal = maxOf(existing.maxTotal, sub.maxTotal)
                    )
                }
            }
        }
        return if (ordered.isEmpty()) FALLBACK_SUBJECTS else ordered.values.toList()
    }

    fun resolveTermHeaders(rows: List<StudentSummativeResultRow>, schema: List<DynamicSubjectDef>): ResultTermHeaders {
        val firstHeaders = rows.firstOrNull()?.termHeaders
        if (firstHeaders != null && firstHeaders.term1Header.isNotBlank()) {
            return firstHeaders
        }
        val m1 = formatMaxSet(schema.map { it.maxT1 }.distinct())
        val m2 = formatMaxSet(schema.map { it.maxT2 }.distinct())
        val m3 = formatMaxSet(schema.map { it.maxT3 }.distinct())
        return ResultTermHeaders(
            term1Header = "1ST SUMMATIVE ($m1)",
            term2Header = "2ND SUMMATIVE ($m2)",
            term3Header = "3RD SUMMATIVE ($m3)"
        )
    }

    private fun formatMaxSet(vals: List<Double>): String {
        val positive = vals.filter { it > 0.0 }
        if (positive.isEmpty()) return "-"
        return positive.joinToString("/") { v ->
            if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else v.toString()
        }
    }

    fun recalculatePositions(rows: List<StudentSummativeResultRow>): List<StudentSummativeResultRow> {
        val ranked = rows
            .mapIndexed { idx, row -> idx to row.grandTotalNumeric() }
            .sortedByDescending { it.second }

        val result = rows.map { it.copy() }
        var currentRank = 0
        var lastScore = Double.NaN
        var rankedCount = 0
        for ((origIdx, score) in ranked) {
            if (score < 0.0 || result[origIdx].grandTotal.isBlank()) {
                result[origIdx].position = ""
                continue
            }
            if (lastScore.isNaN() || score != lastScore) {
                currentRank = rankedCount + 1
                lastScore = score
            }
            rankedCount++
            result[origIdx].position = currentRank.toString()
        }
        return result
    }

    fun writeWorkbook(
        rowsInput: List<StudentSummativeResultRow>,
        sheetTitle: String = "Summative Evaluation",
        outStream: OutputStream
    ) {
        val rows = recalculatePositions(rowsInput)
        val schema = resolveSubjectSchema(rows)
        val termHeaders = resolveTermHeaders(rows, schema)
        val cleanTitle = sheetTitle
            .replace(Regex("[:\\\\/?*\\[\\]]"), " ")
            .trim()
            .take(31)
            .ifBlank { "Summative Result" }

        ZipOutputStream(outStream).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml(cleanTitle))
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(rows, schema, termHeaders))
            zip.finish()
            zip.flush()
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildWorkbookXml(sheetName: String): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
    }

    private fun buildSheetXml(
        rows: List<StudentSummativeResultRow>,
        schema: List<DynamicSubjectDef>,
        termHeaders: ResultTermHeaders
    ): String {
        val subjCount = schema.size.coerceAtLeast(1)
        val termWidth = subjCount + 1 // subjects + TOTAL
        val idStart = 0
        val t1Start = 2
        val t2Start = t1Start + termWidth
        val t3Start = t2Start + termWidth
        val totStart = t3Start + termWidth
        val finalStart = totStart + subjCount
        val totalCols = finalStart + 3 // GRAND TOTAL, PERCENTAGE, POSITION

        val columnHeaders = mutableListOf<String>()
        columnHeaders.add("ROLL NO")
        columnHeaders.add("STUDENT NAME")
        for (term in 1..3) {
            schema.forEach { columnHeaders.add(it.code) }
            columnHeaders.add("TOTAL")
        }
        schema.forEach { columnHeaders.add("${it.code} TOTAL") }
        columnHeaders.add("GRAND TOTAL")
        columnHeaders.add("PERCENTAGE")
        columnHeaders.add("POSITION")

        val sb = StringBuilder(16384)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Freeze top 2 header rows + first 2 identity columns
        sb.append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0"><pane xSplit="2" ySplit="2" topLeftCell="C3" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>""")

        // Dynamic column widths
        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="11" customWidth="1"/>""") // ROLL NO
        sb.append("""<col min="2" max="2" width="26" customWidth="1"/>""") // STUDENT NAME
        if (totStart > t1Start) {
            sb.append("""<col min="${t1Start + 1}" max="$totStart" width="9.5" customWidth="1"/>""")
        }
        if (finalStart > totStart) {
            sb.append("""<col min="${totStart + 1}" max="$finalStart" width="11.5" customWidth="1"/>""")
        }
        sb.append("""<col min="${finalStart + 1}" max="$totalCols" width="13.5" customWidth="1"/>""")
        sb.append("</cols>")

        sb.append("<sheetData>")

        // Row 1: Dynamic Grouped Super-Headers
        sb.append("""<row r="1" ht="24" customHeight="1">""")
        for (cIdx in 0 until totalCols) {
            val ref = "${colLetter(cIdx)}1"
            val (label, styleIdx) = when (cIdx) {
                idStart -> "STUDENT DETAILS" to 1
                in 1 until t1Start -> "" to 1
                t1Start -> termHeaders.term1Header to 2
                in (t1Start + 1) until t2Start -> "" to 2
                t2Start -> termHeaders.term2Header to 3
                in (t2Start + 1) until t3Start -> "" to 3
                t3Start -> termHeaders.term3Header to 4
                in (t3Start + 1) until totStart -> "" to 4
                totStart -> "SUBJECT TOTAL" to 5
                in (totStart + 1) until finalStart -> "" to 5
                finalStart -> "FINAL RESULT" to 1
                else -> "" to 1
            }
            sb.append(stringCell(ref, label, styleIdx))
        }
        sb.append("</row>")

        // Row 2: Dynamic Column Headers
        sb.append("""<row r="2" ht="22" customHeight="1">""")
        columnHeaders.forEachIndexed { cIdx, header ->
            val styleIdx = when {
                cIdx in t1Start until t2Start -> 2
                cIdx in t2Start until t3Start -> 3
                cIdx in t3Start until totStart -> 4
                cIdx in totStart until finalStart -> 5
                else -> 1
            }
            sb.append(stringCell("${colLetter(cIdx)}2", header, styleIdx))
        }
        sb.append("</row>")

        // Student rows (Row 3+)
        rows.forEachIndexed { idx, row ->
            val rNum = idx + 3
            sb.append("""<row r="$rNum" ht="19" customHeight="1">""")
            val vals = mutableListOf<String>()
            vals.add(row.rollNo)
            vals.add(row.studentName)
            for (sub in schema) vals.add(row.markFor(sub.key, 1))
            vals.add(row.term1Total)
            for (sub in schema) vals.add(row.markFor(sub.key, 2))
            vals.add(row.term2Total)
            for (sub in schema) vals.add(row.markFor(sub.key, 3))
            vals.add(row.term3Total)
            for (sub in schema) vals.add(row.markFor(sub.key, 4))
            vals.add(row.grandTotal)
            vals.add(row.percentage)
            vals.add(row.position)

            vals.forEachIndexed { cIdx, v ->
                val ref = "${colLetter(cIdx)}$rNum"
                val isBoldCol =
                    cIdx == (t2Start - 1) ||
                        cIdx == (t3Start - 1) ||
                        cIdx == (totStart - 1) ||
                        cIdx >= finalStart
                val style = if (cIdx == 1) 7 else if (isBoldCol) 8 else 6
                if (cIdx == 0 || cIdx == 1) {
                    sb.append(stringCell(ref, v, style))
                } else {
                    sb.append(autoNumericOrStringCell(ref, v, style))
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData>")

        // Dynamic merged cells for super-header row 1
        sb.append("""<mergeCells count="6">""")
        sb.append("""<mergeCell ref="${colLetter(idStart)}1:${colLetter(t1Start - 1)}1"/>""")
        sb.append("""<mergeCell ref="${colLetter(t1Start)}1:${colLetter(t2Start - 1)}1"/>""")
        sb.append("""<mergeCell ref="${colLetter(t2Start)}1:${colLetter(t3Start - 1)}1"/>""")
        sb.append("""<mergeCell ref="${colLetter(t3Start)}1:${colLetter(totStart - 1)}1"/>""")
        sb.append("""<mergeCell ref="${colLetter(totStart)}1:${colLetter(finalStart - 1)}1"/>""")
        sb.append("""<mergeCell ref="${colLetter(finalStart)}1:${colLetter(totalCols - 1)}1"/>""")
        sb.append("</mergeCells>")

        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun stringCell(ref: String, value: String, style: Int): String {
        return """<c r="$ref" s="$style" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""
    }

    private fun autoNumericOrStringCell(ref: String, raw: String, style: Int): String {
        val clean = raw.trim()
        if (clean.isEmpty()) {
            return """<c r="$ref" s="$style" t="inlineStr"><is><t></t></is></c>"""
        }
        val num = clean.removeSuffix("%").trim().toDoubleOrNull()
        return if (num != null && num.isFinite() && !clean.endsWith("%")) {
            val formatted = if (num == num.roundToInt().toDouble()) {
                num.roundToInt().toString()
            } else {
                String.format(Locale.US, "%.2f", num).trimEnd('0').trimEnd('.')
            }
            """<c r="$ref" s="$style"><v>$formatted</v></c>"""
        } else {
            stringCell(ref, clean, style)
        }
    }

    private fun colLetter(index0: Int): String {
        var idx = index0
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A' + (idx % 26)))
            idx = (idx / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="8">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF123B70"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1D4ED8"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF047857"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF6D28D9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFB45309"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF8FAFC"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="9">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="1" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="1" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="1" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="7" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
}
