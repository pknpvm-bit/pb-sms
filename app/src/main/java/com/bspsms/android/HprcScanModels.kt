package com.bspsms.android

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class HprcEvaluationCategory(val label: String) {
    FORMATIVE("Formative"),
    SUMMATIVE("Summative"),
    LPCD("LPCD"),
    BCO("BCO")
}

enum class HprcPreviewMode(val label: String) {
    STUDENT_WISE("Student_Wise"),
    ORIGINAL_FORMAT("Original_Format")
}

data class HprcUncertainField(
    val id: String = UUID.randomUUID().toString(),
    val studentLocalId: String,
    val studentLabel: String,
    val section: String, // Formative, Summative, LPCD, BCO, StudentInfo
    val fieldKey: String,
    val rawOcrText: String,
    val candidateValue: String = "",
    val reason: String = "Low OCR clarity — please verify manually",
    val resolved: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("studentLocalId", studentLocalId)
        put("studentLabel", studentLabel)
        put("section", section)
        put("fieldKey", fieldKey)
        put("rawOcrText", rawOcrText)
        put("candidateValue", candidateValue)
        put("reason", reason)
        put("resolved", resolved)
    }

    companion object {
        fun fromJson(obj: JSONObject): HprcUncertainField = HprcUncertainField(
            id = obj.optString("id", UUID.randomUUID().toString()),
            studentLocalId = obj.optString("studentLocalId", ""),
            studentLabel = obj.optString("studentLabel", ""),
            section = obj.optString("section", "Summative"),
            fieldKey = obj.optString("fieldKey", ""),
            rawOcrText = obj.optString("rawOcrText", ""),
            candidateValue = obj.optString("candidateValue", ""),
            reason = obj.optString("reason", ""),
            resolved = obj.optBoolean("resolved", false)
        )
    }
}

/**
 * Keys format:
 * - formativeMarks: "SUBJECT|TERM" -> grade/mark ("A", "B", "C", "D" or numeric)
 * - summativeMarks: "SUBJECT|T1", "SUBJECT|T2", "SUBJECT|T3", "SUBJECT|TOTAL" -> numeric mark
 * - lpcdResponses: "INDICATOR|TERM" -> value/grade
 * - bcoResponses: "PART_ITEM|TERM" -> value ("YES"/"NO"/"A"/"B"/"C"/"D"/text)
 */
data class HprcStudentScanRecord(
    val localId: String = UUID.randomUUID().toString(),
    val studentName: String = "",
    val rollNo: String = "",
    val studentCode: String = "",
    val className: String = "",
    val section: String = "",
    val academicYear: String = "",
    val medium: String = "",
    val scannedPages: List<Int> = emptyList(),
    val formativeMarks: Map<String, String> = emptyMap(),
    val summativeMarks: Map<String, String> = emptyMap(),
    val lpcdResponses: Map<String, String> = emptyMap(),
    val bcoResponses: Map<String, String> = emptyMap(),
    val remarks: String = "",
    val rawPageSignatures: List<String> = emptyList()
) {
    val displayTitle: String
        get() = buildString {
            if (rollNo.isNotBlank()) append("Roll $rollNo")
            if (rollNo.isNotBlank() && studentName.isNotBlank()) append(" · ")
            if (studentName.isNotBlank()) append(studentName)
            if (isEmpty()) append("Student (${localId.take(6)})")
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("localId", localId)
        put("studentName", studentName)
        put("rollNo", rollNo)
        put("studentCode", studentCode)
        put("className", className)
        put("section", section)
        put("academicYear", academicYear)
        put("medium", medium)
        put("scannedPages", JSONArray(scannedPages))
        put("formativeMarks", JSONObject(formativeMarks))
        put("summativeMarks", JSONObject(summativeMarks))
        put("lpcdResponses", JSONObject(lpcdResponses))
        put("bcoResponses", JSONObject(bcoResponses))
        put("remarks", remarks)
        put("rawPageSignatures", JSONArray(rawPageSignatures))
    }

    companion object {
        private fun jsonToMap(obj: JSONObject?): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            if (obj == null) return out
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (v.isNotBlank()) out[k] = v
            }
            return out
        }

        fun fromJson(obj: JSONObject): HprcStudentScanRecord {
            val pagesArr = obj.optJSONArray("scannedPages") ?: JSONArray()
            val pages = mutableListOf<Int>()
            for (i in 0 until pagesArr.length()) pages.add(pagesArr.optInt(i))

            val sigArr = obj.optJSONArray("rawPageSignatures") ?: JSONArray()
            val sigs = mutableListOf<String>()
            for (i in 0 until sigArr.length()) sigs.add(sigArr.optString(i))

            return HprcStudentScanRecord(
                localId = obj.optString("localId", UUID.randomUUID().toString()),
                studentName = obj.optString("studentName", ""),
                rollNo = obj.optString("rollNo", ""),
                studentCode = obj.optString("studentCode", ""),
                className = obj.optString("className", ""),
                section = obj.optString("section", ""),
                academicYear = obj.optString("academicYear", ""),
                medium = obj.optString("medium", ""),
                scannedPages = pages,
                formativeMarks = jsonToMap(obj.optJSONObject("formativeMarks")),
                summativeMarks = jsonToMap(obj.optJSONObject("summativeMarks")),
                lpcdResponses = jsonToMap(obj.optJSONObject("lpcdResponses")),
                bcoResponses = jsonToMap(obj.optJSONObject("bcoResponses")),
                remarks = obj.optString("remarks", ""),
                rawPageSignatures = sigs
            )
        }
    }
}

data class HprcPortalContext(
    val detected: Boolean = false,
    val category: String = "",
    val academicYear: String = "",
    val medium: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val term: String = "",
    val portalUrl: String = "",
    val writableRowCount: Int = 0,
    val rawSummary: String = ""
)

data class HprcFillPreviewRow(
    val studentLocalId: String,
    val scannedName: String,
    val scannedRoll: String,
    val portalRowIndex: Int,
    val portalStudentName: String,
    val portalRoll: String,
    val portalStudentCode: String,
    val matchReason: String,
    val fieldsToFill: Map<String, String>,
    val skippedReason: String = ""
)
