package com.bspsms.android

import com.google.mlkit.vision.text.Text
import java.security.MessageDigest
import java.util.Locale

data class HprcParsedPageResult(
    val pageSignature: String,
    val detectedPageNumber: Int?,
    val detectedSections: Set<String>,
    val studentName: String,
    val rollNo: String,
    val studentCode: String,
    val className: String,
    val section: String,
    val academicYear: String,
    val medium: String,
    val formativeMarks: Map<String, String>,
    val summativeMarks: Map<String, String>,
    val lpcdResponses: Map<String, String>,
    val bcoResponses: Map<String, String>,
    val remarks: String,
    val uncertainFields: List<HprcUncertainField>
) {
    val hasUsefulData: Boolean
        get() = studentName.isNotBlank() ||
            rollNo.isNotBlank() ||
            studentCode.isNotBlank() ||
            formativeMarks.isNotEmpty() ||
            summativeMarks.isNotEmpty() ||
            lpcdResponses.isNotEmpty() ||
            bcoResponses.isNotEmpty() ||
            remarks.isNotBlank() ||
            uncertainFields.isNotEmpty()
}

object HprcOcrParser {

    private val SUBJECT_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("bengali|1st\\s*lang|first\\s*lang|\\bben\\b|বাংলা", RegexOption.IGNORE_CASE) to "Bengali",
        Regex("english|2nd\\s*lang|second\\s*lang|\\beng\\b", RegexOption.IGNORE_CASE) to "English",
        Regex("environment\\s*(?:&|and)\\s*geo|geography", RegexOption.IGNORE_CASE) to "Geography",
        Regex("environment\\s*(?:&|and)\\s*hist|history", RegexOption.IGNORE_CASE) to "History",
        Regex("environment|our\\s*env|\\bevs\\b|paribesh|পরিবেশ", RegexOption.IGNORE_CASE) to "Our Environment",
        Regex("physical\\s*sci|phys\\.?\\s*sci", RegexOption.IGNORE_CASE) to "Physical Science",
        Regex("life\\s*sci", RegexOption.IGNORE_CASE) to "Life Science",
        Regex("\\bscience\\b", RegexOption.IGNORE_CASE) to "Science",
        Regex("math|ganit|arithmetic|গণিত", RegexOption.IGNORE_CASE) to "Mathematics",
        Regex("health|physical\\s*ed|hygiene|swasthya", RegexOption.IGNORE_CASE) to "Health & Physical Ed.",
        Regex("art|work\\s*ed|drawing|craft|music", RegexOption.IGNORE_CASE) to "Art & Work Ed.",
        Regex("sanskrit", RegexOption.IGNORE_CASE) to "Sanskrit",
        Regex("hindi", RegexOption.IGNORE_CASE) to "Hindi",
        Regex("computer|ict", RegexOption.IGNORE_CASE) to "Computer",
        Regex("communicate|communication", RegexOption.IGNORE_CASE) to "Ability to Communicate",
        Regex("correlate|correlation", RegexOption.IGNORE_CASE) to "Ability to Correlate",
        Regex("problem\\s*solv", RegexOption.IGNORE_CASE) to "Problem Solving"
    )

    private val FORMATIVE_INDICATORS: List<Pair<Regex, String>> = listOf(
        Regex("participation|participat", RegexOption.IGNORE_CASE) to "Participation",
        Regex("question|experiment|inquiry|curiosity", RegexOption.IGNORE_CASE) to "Questioning & Experimentation",
        Regex("interpret|applicat|understand", RegexOption.IGNORE_CASE) to "Interpretation & Application",
        Regex("empathy|co[-\\s]?operat|teamwork", RegexOption.IGNORE_CASE) to "Empathy & Cooperation",
        Regex("aesthetic|creative|expression", RegexOption.IGNORE_CASE) to "Aesthetic & Creative Expression"
    )

    private val LPCD_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("literacy|reading|writing|listen", RegexOption.IGNORE_CASE) to "Literacy & Comprehension",
        Regex("problem|critical|reason", RegexOption.IGNORE_CASE) to "Problem Solving",
        Regex("co[-\\s]?operat|collaborat|team", RegexOption.IGNORE_CASE) to "Cooperation & Sharing",
        Regex("discipline|punctual|regular|clean|hygiene", RegexOption.IGNORE_CASE) to "Discipline & Cleanliness",
        Regex("creativ|curios|initiative", RegexOption.IGNORE_CASE) to "Creativity & Curiosity"
    )

    private val BCO_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("part\\s*[-:]?\\s*a|self\\s*awareness|emotional|social\\s*skill", RegexOption.IGNORE_CASE) to "Part A (Social & Emotional)",
        Regex("part\\s*[-:]?\\s*b|co[-\\s]?curricular|sports|cultural|library|club", RegexOption.IGNORE_CASE) to "Part B (Co-Curricular)",
        Regex("attendance|working\\s*days|present", RegexOption.IGNORE_CASE) to "Attendance Observation",
        Regex("conduct|behaviour|behavior|attitude", RegexOption.IGNORE_CASE) to "Behaviour & Conduct"
    )

    fun normalizeDigits(input: String): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = bn.indexOf(ch)
            if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    fun parseMlKitText(
        visionText: Text,
        currentStudentLocalId: String,
        currentStudentLabel: String
    ): HprcParsedPageResult {
        val rawFull = visionText.text.orEmpty()
        val normalizedFull = normalizeDigits(rawFull)

        // Rebuild spatially ordered horizontal lines using bounding boxes so multi-column tables stay row-aligned
        val lineEntries = visionText.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val txt = normalizeDigits(line.text).replace(Regex("\\s+"), " ").trim()
                if (txt.isBlank()) null else Triple(line.boundingBox?.top ?: 0, line.boundingBox?.left ?: 0, txt)
            }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first / 26 }.thenBy { it.second })

        val mergedRows = mutableListOf<String>()
        var currentBand = Int.MIN_VALUE
        val currentRowParts = mutableListOf<String>()
        for ((top, _, txt) in lineEntries) {
            if (currentBand == Int.MIN_VALUE || kotlin.math.abs(top - currentBand) <= 22) {
                if (currentBand == Int.MIN_VALUE) currentBand = top
                currentRowParts.add(txt)
            } else {
                mergedRows.add(currentRowParts.joinToString(" | "))
                currentRowParts.clear()
                currentRowParts.add(txt)
                currentBand = top
            }
        }
        if (currentRowParts.isNotEmpty()) mergedRows.add(currentRowParts.joinToString(" | "))

        val sections = linkedSetOf<String>()
        if (Regex("summative", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) sections.add("Summative")
        if (Regex("formative", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) sections.add("Formative")
        if (Regex("\\blpcd\\b|literacy.*problem|qualitative\\s+indicator", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) {
            sections.add("LPCD")
        }
        if (Regex("\\bbco\\b|co[-\\s]?curricular|part\\s*[-:]?\\s*a.*part\\s*[-:]?\\s*b", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) {
            sections.add("BCO")
        }

        // Student metadata extraction
        val studentName = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Student(?:'s)?\\s*Name|Name\\s*of\\s*(?:the\\s*)?Student)\\s*[:\\-]\\s*([A-Za-z][A-Za-z .']{2,48})", RegexOption.IGNORE_CASE),
                Regex("\\bName\\b\\s*[:\\-]\\s*([A-Za-z][A-Za-z .']{2,48})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val rollNo = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Roll\\s*(?:No\\.?|Number)?)\\s*[:\\-]\\s*([0-9]{1,4})", RegexOption.IGNORE_CASE),
                Regex("\\bRoll\\b\\s*[:\\-]\\s*([0-9A-Za-z\\-]{1,8})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val studentCode = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Student\\s*(?:Code|ID)|BSP\\s*ID|Unique\\s*ID)\\s*[:\\-]\\s*([0-9]{6,18})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val className = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bClass\\b\\s*[:\\-]\\s*([IVX]{1,4}|PP|Pre[-\\s]?Primary|[0-9]{1,2})", RegexOption.IGNORE_CASE)
            )
        ).trim().uppercase(Locale.US)

        val section = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bSection\\b\\s*[:\\-]\\s*([A-Za-z0-9]{1,4})\\b", RegexOption.IGNORE_CASE)
            )
        ).trim().uppercase(Locale.US)

        val academicYear = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Academic\\s*Year|Session)\\s*[:\\-]\\s*(20\\d{2})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val medium = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bMedium\\b\\s*[:\\-]\\s*(Bengali|English|Hindi|Urdu|Nepali|Santhali)", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val detectedPageNumber = extractRegexGroup(
            normalizedFull,
            listOf(Regex("\\bPage\\s*[:\\-#]?\\s*([1-6])\\b", RegexOption.IGNORE_CASE))
        ).toIntOrNull()

        val summativeMarks = LinkedHashMap<String, String>()
        val formativeMarks = LinkedHashMap<String, String>()
        val lpcdResponses = LinkedHashMap<String, String>()
        val bcoResponses = LinkedHashMap<String, String>()
        val uncertainFields = mutableListOf<HprcUncertainField>()

        val inSummativeZone = sections.contains("Summative") || !sections.contains("Formative")
        val inFormativeZone = sections.contains("Formative")

        for (rowText in mergedRows) {
            val cleanRow = rowText.replace("|", " ").replace(Regex("\\s+"), " ").trim()
            if (cleanRow.length < 3) continue
            if (Regex("grand\\s*total|full\\s*marks|\\bf\\.?m\\.?\\b|signature|headmaster", RegexOption.IGNORE_CASE).containsMatchIn(cleanRow)) {
                continue
            }

            // 1. Check Summative Subject Row
            val matchedSubj = SUBJECT_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }?.second
            if (matchedSubj != null && (inSummativeZone || Regex("\\b\\d{1,3}\\b").containsMatchIn(cleanRow))) {
                val tail = cleanRow.replace(SUBJECT_PATTERNS.first { it.second == matchedSubj }.first, " ").trim()
                val rawTokens = tail.split(Regex("\\s+")).filter { it.isNotBlank() }

                val validNums = mutableListOf<String>()
                val uncertainTokens = mutableListOf<String>()
                val gradeTokens = mutableListOf<String>()

                for (tok in rawTokens) {
                    val cleanTok = tok.trim(',', ';', ':', '.', '(', ')', '[', ']')
                    if (cleanTok.isBlank()) continue
                    if (cleanTok in setOf("20", "30", "50", "100", "15", "25", "70") && rawTokens.size >= 5) {
                        // Possible F.M. column alongside obtained marks; still inspect numeric sequence carefully
                    }
                    when {
                        Regex("^\\d{1,2}(?:\\.\\d)?$").matches(cleanTok) -> {
                            val v = cleanTok.toDoubleOrNull()
                            if (v != null && v <= 100.0) {
                                validNums.add(cleanTok)
                            } else {
                                uncertainTokens.add(cleanTok)
                            }
                        }
                        Regex("^[A-D][+]?$", RegexOption.IGNORE_CASE).matches(cleanTok) -> {
                            gradeTokens.add(cleanTok.uppercase(Locale.US))
                        }
                        // Typical OCR confusion like 1O, l8, O5, I2, 2O, ?
                        Regex("^(?:[0-9]+[OIlSBG]|[OIl]+[0-9]+|\\?+|--\\?|\\d{3,})$").matches(cleanTok) -> {
                            uncertainTokens.add(cleanTok)
                        }
                    }
                }

                // Do NOT guess when ambiguous tokens appear on a subject row
                if (uncertainTokens.isNotEmpty()) {
                    uncertainFields.add(
                        HprcUncertainField(
                            studentLocalId = currentStudentLocalId,
                            studentLabel = currentStudentLabel,
                            section = "Summative",
                            fieldKey = "$matchedSubj|T1",
                            rawOcrText = cleanRow,
                            candidateValue = validNums.firstOrNull() ?: "",
                            reason = "Unclear OCR token(s): ${uncertainTokens.joinToString(", ")}"
                        )
                    )
                }

                if (validNums.isNotEmpty() && !sections.contains("Formative")) {
                    // Filter out repeated F.M. pairs if 6+ numbers like [20, 17, 30, 26, 50, 44]
                    val obtNums = if (validNums.size >= 6 &&
                        validNums[0] in setOf("15", "20") &&
                        validNums[2] in setOf("25", "30") &&
                        validNums[4] in setOf("50", "70")
                    ) {
                        listOfNotNull(validNums.getOrNull(1), validNums.getOrNull(3), validNums.getOrNull(5), validNums.getOrNull(6))
                    } else {
                        validNums
                    }
                    obtNums.getOrNull(0)?.let { summativeMarks["$matchedSubj|T1"] = it }
                    obtNums.getOrNull(1)?.let { summativeMarks["$matchedSubj|T2"] = it }
                    obtNums.getOrNull(2)?.let { summativeMarks["$matchedSubj|T3"] = it }
                    obtNums.getOrNull(3)?.let { summativeMarks["$matchedSubj|TOTAL"] = it }
                    sections.add("Summative")
                } else if (gradeTokens.isNotEmpty() && inFormativeZone) {
                    gradeTokens.getOrNull(0)?.let { formativeMarks["$matchedSubj|T1"] = it }
                    gradeTokens.getOrNull(1)?.let { formativeMarks["$matchedSubj|T2"] = it }
                    gradeTokens.getOrNull(2)?.let { formativeMarks["$matchedSubj|T3"] = it }
                    sections.add("Formative")
                }
            }

            // 2. Check Formative Indicators
            val matchedForm = FORMATIVE_INDICATORS.firstOrNull { it.first.containsMatchIn(cleanRow) }?.second
            if (matchedForm != null) {
                val tokens = extractGradesOrMarks(cleanRow)
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { formativeMarks["$matchedForm|T1"] = it }
                    tokens.getOrNull(1)?.let { formativeMarks["$matchedForm|T2"] = it }
                    tokens.getOrNull(2)?.let { formativeMarks["$matchedForm|T3"] = it }
                    sections.add("Formative")
                } else {
                    uncertainFields.add(
                        HprcUncertainField(
                            studentLocalId = currentStudentLocalId,
                            studentLabel = currentStudentLabel,
                            section = "Formative",
                            fieldKey = "$matchedForm|T1",
                            rawOcrText = cleanRow,
                            reason = "Indicator found without clear A/B/C/D grade or numeric score"
                        )
                    )
                }
            }

            // 3. Check LPCD Items
            val matchedLpcd = LPCD_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }?.second
            if (matchedLpcd != null) {
                val tokens = extractGradesOrMarks(cleanRow)
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { lpcdResponses["$matchedLpcd|T1"] = it }
                    tokens.getOrNull(1)?.let { lpcdResponses["$matchedLpcd|T2"] = it }
                    tokens.getOrNull(2)?.let { lpcdResponses["$matchedLpcd|T3"] = it }
                    sections.add("LPCD")
                } else if (Regex("\\b(yes|no|good|satisfactory|needs\\s+improvement)\\b", RegexOption.IGNORE_CASE).containsMatchIn(cleanRow)) {
                    val m = Regex("\\b(yes|no|good|satisfactory|needs\\s+improvement)\\b", RegexOption.IGNORE_CASE).find(cleanRow)
                    if (m != null) {
                        lpcdResponses["$matchedLpcd|T1"] = m.value.uppercase(Locale.US)
                        sections.add("LPCD")
                    }
                }
            }

            // 4. Check BCO Items
            val matchedBco = BCO_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }?.second
            if (matchedBco != null) {
                val tokens = extractGradesOrMarks(cleanRow)
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { bcoResponses["$matchedBco|T1"] = it }
                    tokens.getOrNull(1)?.let { bcoResponses["$matchedBco|T2"] = it }
                    tokens.getOrNull(2)?.let { bcoResponses["$matchedBco|T3"] = it }
                    sections.add("BCO")
                } else {
                    val yn = Regex("\\b(yes|no|a\\+|a|b\\+|b|c|d)\\b", RegexOption.IGNORE_CASE).find(cleanRow)?.value
                    if (yn != null) {
                        bcoResponses["$matchedBco|T1"] = yn.uppercase(Locale.US)
                        sections.add("BCO")
                    }
                }
            }
        }

        val remarks = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Teacher(?:'s)?\\s*Remarks?|General\\s*Remarks?|Observation)\\s*[:\\-]\\s*([^\n\r]{4,140})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val sigInput = "$studentName|$rollNo|$detectedPageNumber|${summativeMarks.entries}|${formativeMarks.entries}|${lpcdResponses.entries}|${bcoResponses.entries}"
        val signature = sha256Short(sigInput)

        return HprcParsedPageResult(
            pageSignature = signature,
            detectedPageNumber = detectedPageNumber,
            detectedSections = sections,
            studentName = studentName,
            rollNo = rollNo,
            studentCode = studentCode,
            className = className,
            section = section,
            academicYear = academicYear,
            medium = medium,
            formativeMarks = formativeMarks,
            summativeMarks = summativeMarks,
            lpcdResponses = lpcdResponses,
            bcoResponses = bcoResponses,
            remarks = remarks,
            uncertainFields = uncertainFields
        )
    }

    private fun extractRegexGroup(text: String, patterns: List<Regex>): String {
        for (rx in patterns) {
            val m = rx.find(text)
            if (m != null && m.groupValues.size > 1) {
                val valStr = m.groupValues[1].trim()
                if (valStr.isNotBlank()) return valStr
            }
        }
        return ""
    }

    private fun extractGradesOrMarks(line: String): List<String> {
        val tokens = line.split(Regex("[\\s,;:|]+")).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (t in tokens) {
            val clean = t.trim('(', ')', '[', ']', '.')
            when {
                Regex("^[A-D][+]?$", RegexOption.IGNORE_CASE).matches(clean) -> result.add(clean.uppercase(Locale.US))
                Regex("^\\d{1,2}(?:\\.\\d)?$").matches(clean) -> {
                    val v = clean.toDoubleOrNull()
                    if (v != null && v <= 100.0) result.add(clean)
                }
            }
        }
        return result
    }

    private fun sha256Short(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
