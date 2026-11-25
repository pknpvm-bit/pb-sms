package com.bspsms.android

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream

object SafStorageHelper {
    private const val PREFS_NAME = "bsp_sms_saf_storage"
    private const val KEY_TREE_URI = "saf_tree_uri"
    private const val KEY_TREE_LABEL = "saf_tree_label"

    fun createOpenFolderIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    fun persistFolderUri(context: Context, treeUri: Uri): String {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        }
        val label = resolveFolderDisplayName(context, treeUri)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_TREE_LABEL, label)
            .apply()
        return label
    }

    fun getSavedFolderUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
            ?.trim()
            .orEmpty()
        if (raw.isBlank()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val hasPersisted = context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isWritePermission && perm.isReadPermission
        }
        return if (hasPersisted && isTreeUriWritable(context, uri)) uri else null
    }

    fun isTreeUriWritable(context: Context, treeUri: Uri): Boolean {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return false
        val parentDocUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        }.getOrNull() ?: return false
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                parentDocUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )
            cursor != null && cursor.moveToFirst()
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    fun getSavedFolderLabel(context: Context): String {
        val uri = getSavedFolderUri(context) ?: return ""
        val cached = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_LABEL, "")
            .orEmpty()
        return if (cached.isNotBlank()) cached else resolveFolderDisplayName(context, uri)
    }

    fun clearSavedFolder(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_TREE_LABEL)
            .apply()
    }

    fun resolveFolderDisplayName(context: Context, treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        val parentDocUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        }.getOrNull()

        if (parentDocUri != null) {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    parentDocUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx).orEmpty().trim()
                        if (name.isNotBlank()) {
                            return if (docId.contains(":")) "$name ($docId)" else name
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
        }
        return docId.substringAfterLast(':').ifBlank { treeUri.lastPathSegment ?: "Selected Folder" }
    }

    fun sanitizeMarksheetFileName(rollNo: String, studentName: String, ext: String = "pdf"): String {
        val rawDigits = rollNo
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "")
            .replace(Regex("[^0-9A-Za-z_-]"), "")
            .take(20)
        val normalizedRoll = when {
            rawDigits.isBlank() -> "000"
            rawDigits.all { it.isDigit() } -> rawDigits.padStart(3, '0')
            else -> rawDigits.uppercase()
        }
        val cleanName = studentName
            .trim()
            .uppercase()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .take(64)
            .trim('_')
            .ifBlank { "STUDENT" }
        val cleanExt = ext
            .trimStart('.')
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "pdf" }
        return "${normalizedRoll}_${cleanName}.$cleanExt"
    }

    fun findDocumentByDisplayName(context: Context, treeUri: Uri, displayName: String): Uri? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )
            if (cursor != null) {
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    if (name.equals(displayName, ignoreCase = true) && idIdx >= 0) {
                        val docId = cursor.getString(idIdx)
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return null
    }

    fun writeFileToFolder(
        context: Context,
        treeUri: Uri,
        mimeType: String,
        displayName: String,
        overwriteExisting: Boolean = true,
        writer: (OutputStream) -> Unit
    ): Result<Uri> {
        return runCatching {
            val safeDisplayName = displayName
                .trim()
                .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
                .take(120)
                .ifBlank { "MARKSHEET.pdf" }

            if (!isTreeUriWritable(context, treeUri)) {
                error("SAF folder permission expired or folder unavailable. Please re-select the Download Folder.")
            }

            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            val existingUri = findDocumentByDisplayName(context, treeUri, safeDisplayName)
            var createdNew = false
            val targetUri = if (existingUri != null && overwriteExisting) {
                val deleted = runCatching { DocumentsContract.deleteDocument(resolver, existingUri) }.getOrDefault(false)
                if (deleted) {
                    createdNew = true
                    DocumentsContract.createDocument(resolver, parentDocUri, mimeType, safeDisplayName)
                        ?: error("Could not create file in folder: $safeDisplayName")
                } else {
                    existingUri
                }
            } else if (existingUri != null) {
                existingUri
            } else {
                createdNew = true
                DocumentsContract.createDocument(resolver, parentDocUri, mimeType, safeDisplayName)
                    ?: error("Could not create file in selected folder: $safeDisplayName")
            }

            try {
                val opened = resolver.openOutputStream(targetUri, "wt")
                    ?: resolver.openOutputStream(targetUri)
                    ?: error("Failed to open output stream for $safeDisplayName")
                opened.use { out ->
                    writer(out)
                    out.flush()
                }
            } catch (t: Throwable) {
                if (createdNew) {
                    runCatching { DocumentsContract.deleteDocument(resolver, targetUri) }
                }
                throw t
            }

            targetUri
        }
    }

    fun writeToExplicitUri(
        context: Context,
        targetUri: Uri,
        writer: (OutputStream) -> Unit
    ): Result<Uri> {
        return runCatching {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                writer(out)
                out.flush()
            } ?: context.contentResolver.openOutputStream(targetUri)?.use { out ->
                writer(out)
                out.flush()
            } ?: error("Failed to open destination file stream")
            targetUri
        }
    }
}
