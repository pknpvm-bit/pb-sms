package com.bspsms.android

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Multi-sheet OOXML .xlsx generator for Tool #8 (HPRC Scan Tool).
 * Produces exact 6 required sheets:
 *  1. Student_List
 *  2. Formative
 *  3. Summative
 *  4. LPCD
 *  5. BCO
 *  6. Original_Format
 */
object HprcWorkbookExporter {

    private val SHEET_NAMES = listOf(
        "Student_List",
        "Formative",
        "Summative",
        "LPCD",
        "BCO",
        "Original_Format"
    )

    fun writeWorkbook(
        students: List<HprcStudentScanRecord>,
        outStream: OutputStream
    ) {
        ZipOutputStream(outStream).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())
            writeZipEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)

            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildStudentListSheet(students))
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", buildSectionMatrixSheet(students, "FORMATIVE", { it.formativeMarks }))
            writeZipEntry(zip, "xl/worksheets/sheet3.xml", buildSummativeSheet(students))
            writeZipEntry(zip, "xl/worksheets/sheet4.xml", buildSectionMatrixSheet(students, "LPCD", { it.lpcdResponses }))
            writeZipEntry(zip, "xl/worksheets/sheet5.xml", buildSectionMatrixSheet(students, "BCO", { it.bcoResponses }))
            writeZipEntry(zip, "xl/worksheets/sheet6.xml", buildOriginalFormatSheet(students))

            zip.finish()
            zip.flush()
        }
    }

    private fun buildStudentListSheet(students: List<HprcStudentScanRecord>): String {
        val headers = listOf(
            "SL NO",
            "ROLL NO",
            "STUDENT NAME",
            "STUDENT CODE",
            "CLASS",
            "SECTION",
            "MEDIUM",
            "ACADEMIC YEAR",
            "SCANNED PAGES",
            "FORMATIVE COUNT",
            "SUMMATIVE COUNT",
            "LPCD COUNT",
            "BCO COUNT",
            "REMARKS"
        )
        val rows = students.mapIndexed { idx, s ->
            listOf(
                (idx + 1).toString(),
                s.rollNo,
                s.studentName,
                s.studentCode,
                s.className,
                s.section,
                s.medium,
                s.academicYear,
                s.scannedPages.sorted().joinToString(","),
                s.formativeMarks.size.toString(),
                s.summativeMarks.size.toString(),
                s.lpcdResponses.size.toString(),
                s.bcoResponses.size.toString(),
                s.remarks
            )
        }
        return buildSimpleGridXml(headers, rows, freezeCols = 3)
    }

    private fun buildSummativeSheet(students: List<HprcStudentScanRecord>): String {
        val subjectSet = linkedSetOf<String>()
        students.forEach { st ->
            st.summativeMarks.keys.forEach { k ->
                val subj = k.substringBefore("|").trim()
                if (subj.isNotBlank()) subjectSet.add(subj)
            }
        }
        val subjects = if (subjectSet.isEmpty()) {
            listOf("Bengali", "English", "Our Environment", "Mathematics", "Health & Physical Ed.", "Art & Work Ed.")
        } else {
            subjectSet.toList()
        }

        val headers = mutableListOf("ROLL NO", "STUDENT NAME", "CLASS", "SECTION")
        for (subj in subjects) {
            headers.add("$subj (1ST)")
            headers.add("$subj (2ND)")
            headers.add("$subj (3RD)")
            headers.add("$subj (TOT)")
        }
        headers.add("GRAND TOTAL")

        val rows = students.map { s ->
            val r = mutableListOf(s.rollNo, s.studentName, s.className, s.section)
            var grandSum = 0.0
            var hasGrand = false
            for (subj in subjects) {
                val t1 = s.summativeMarks["$subj|T1"].orEmpty()
                val t2 = s.summativeMarks["$subj|T2"].orEmpty()
                val t3 = s.summativeMarks["$subj|T3"].orEmpty()
                val sum = listOf(t1, t2, t3).mapNotNull { it.toDoubleOrNull() }
                val tot = if (sum.isNotEmpty()) {
                    val v = sum.sum()
                    grandSum += v
                    hasGrand = true
                    formatNum(v)
                } else {
                    val manualTot = s.summativeMarks["$subj|TOTAL"].orEmpty()
                    manualTot.toDoubleOrNull()?.let {
                        grandSum += it
                        hasGrand = true
                    }
                    manualTot
                }
                r.add(t1)
                r.add(t2)
                r.add(t3)
                r.add(tot)
            }
            r.add(if (hasGrand) formatNum(grandSum) else "")
            r
        }

        return buildSimpleGridXml(headers, rows, freezeCols = 2)
    }

    private fun buildSectionMatrixSheet(
        students: List<HprcStudentScanRecord>,
        prefix: String,
        extractor: (HprcStudentScanRecord) -> Map<String, String>
    ): String {
        val dynamicKeys = linkedSetOf<String>()
        students.forEach { s ->
            dynamicKeys.addAll(extractor(s).keys)
        }
        val keyList = dynamicKeys.toList()
        val headers = mutableListOf("ROLL NO", "STUDENT NAME", "CLASS", "SECTION")
        if (keyList.isEmpty()) {
            headers.add("$prefix STATUS")
        } else {
            keyList.forEach { headers.add(it.replace("|", " · ")) }
        }

        val rows = students.map { s ->
            val map = extractor(s)
            val r = mutableListOf(s.rollNo, s.studentName, s.className, s.section)
            if (keyList.isEmpty()) {
                r.add("")
            } else {
                keyList.forEach { k -> r.add(map[k].orEmpty()) }
            }
            r
        }
        return buildSimpleGridXml(headers, rows, freezeCols = 2)
    }

    private fun buildOriginalFormatSheet(students: List<HprcStudentScanRecord>): String {
        val headers = listOf(
            "STUDENT",
            "ROLL",
            "CODE",
            "CLASS / SEC",
            "HPRC SECTION",
            "ITEM / SUBJECT",
            "TERM 1 / RESP",
            "TERM 2",
            "TERM 3",
            "TOTAL / REMARKS"
        )
        val rows = mutableListOf<List<String>>()
        for (s in students) {
            val clsSec = listOf(s.className, s.section).filter { it.isNotBlank() }.joinToString("-")

            val sumSubjs = linkedSetOf<String>()
            s.summativeMarks.keys.forEach { k -> sumSubjs.add(k.substringBefore("|")) }
            sumSubjs.forEach { subj ->
                val t1 = s.summativeMarks["$subj|T1"].orEmpty()
                val t2 = s.summativeMarks["$subj|T2"].orEmpty()
                val t3 = s.summativeMarks["$subj|T3"].orEmpty()
                val tot = s.summativeMarks["$subj|TOTAL"].orEmpty()
                rows.add(listOf(s.studentName, s.rollNo, s.studentCode, clsSec, "Summative", subj, t1, t2, t3, tot))
            }

            val formItems = linkedSetOf<String>()
            s.formativeMarks.keys.forEach { k -> formItems.add(k.substringBefore("|")) }
            formItems.forEach { item ->
                rows.add(
                    listOf(
                        s.studentName, s.rollNo, s.studentCode, clsSec, "Formative", item,
                        s.formativeMarks["$item|T1"].orEmpty(),
                        s.formativeMarks["$item|T2"].orEmpty(),
                        s.formativeMarks["$item|T3"].orEmpty(),
                        ""
                    )
                )
            }

            val lpcdItems = linkedSetOf<String>()
            s.lpcdResponses.keys.forEach { k -> lpcdItems.add(k.substringBefore("|")) }
            lpcdItems.forEach { item ->
                rows.add(
                    listOf(
                        s.studentName, s.rollNo, s.studentCode, clsSec, "LPCD", item,
                        s.lpcdResponses["$item|T1"].orEmpty(),
                        s.lpcdResponses["$item|T2"].orEmpty(),
                        s.lpcdResponses["$item|T3"].orEmpty(),
                        ""
                    )
                )
            }

            val bcoItems = linkedSetOf<String>()
            s.bcoResponses.keys.forEach { k -> bcoItems.add(k.substringBefore("|")) }
            bcoItems.forEach { item ->
                rows.add(
                    listOf(
                        s.studentName, s.rollNo, s.studentCode, clsSec, "BCO", item,
                        s.bcoResponses["$item|T1"].orEmpty(),
                        s.bcoResponses["$item|T2"].orEmpty(),
                        s.bcoResponses["$item|T3"].orEmpty(),
                        s.remarks
                    )
                )
            }

            if (sumSubjs.isEmpty() && formItems.isEmpty() && lpcdItems.isEmpty() && bcoItems.isEmpty()) {
                rows.add(listOf(s.studentName, s.rollNo, s.studentCode, clsSec, "Header Only", "-", "", "", "", s.remarks))
            }
        }
        return buildSimpleGridXml(headers, rows, freezeCols = 2)
    }

    private fun buildSimpleGridXml(
        headers: List<String>,
        rows: List<List<String>>,
        freezeCols: Int
    ): String {
        val sb = StringBuilder(8192)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val topCell = "${colLetter(freezeCols)}2"
        sb.append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0"><pane xSplit="$freezeCols" ySplit="1" topLeftCell="$topCell" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        headers.forEachIndexed { idx, h ->
            val w = when {
                idx == 0 -> 10
                h.contains("NAME", ignoreCase = true) || h.contains("ITEM", ignoreCase = true) -> 24
                else -> 14
            }
            sb.append("""<col min="${idx + 1}" max="${idx + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        sb.append("""<row r="1" ht="22" customHeight="1">""")
        headers.forEachIndexed { cIdx, h ->
            sb.append(stringCell("${colLetter(cIdx)}1", h, 1))
        }
        sb.append("</row>")

        rows.forEachIndexed { rIdx, rowVals ->
            val rNum = rIdx + 2
            sb.append("""<row r="$rNum" ht="19" customHeight="1">""")
            rowVals.forEachIndexed { cIdx, v ->
                val ref = "${colLetter(cIdx)}$rNum"
                sb.append(autoCell(ref, v, if (cIdx <= 1) 2 else 3))
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun autoCell(ref: String, raw: String, style: Int): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return stringCell(ref, "", style)
        val num = clean.toDoubleOrNull()
        return if (num != null && num.isFinite() && !clean.startsWith("0") || clean == "0") {
            val n = clean.toDoubleOrNull()
            if (n != null && n.isFinite() && clean.length <= 10) {
                """<c r="$ref" s="$style"><v>${formatNum(n)}</v></c>"""
            } else {
                stringCell(ref, clean, style)
            }
        } else {
            stringCell(ref, clean, style)
        }
    }

    private fun formatNum(n: Double): String {
        return if (n == n.roundToInt().toDouble()) {
            n.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
        }
    }

    private fun stringCell(ref: String, value: String, style: Int): String {
        return """<c r="$ref" s="$style" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(): String {
        val sheetOverrides = SHEET_NAMES.indices.joinToString("\n") { i ->
            """  <Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheetOverrides
</Types>"""
    }

    private fun buildWorkbookRelsXml(): String {
        val rels = SHEET_NAMES.indices.joinToString("\n") { i ->
            """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
  <Relationship Id="rId${SHEET_NAMES.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        val sheets = SHEET_NAMES.mapIndexed { i, name ->
            """    <sheet name="${escapeXml(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheets
  </sheets>
</workbook>"""
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
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF123B70"/></patternFill></fill>
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
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
}
