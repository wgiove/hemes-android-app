package de.adversum.hermescompanion

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.security.MessageDigest

/**
 * Analysiert einen vom Nutzer AUSDRÜCKLICH ausgewählten Ordner (z. B. „Download“)
 * über Android Storage Access Framework — KEIN Vollzugriff auf den gesamten Speicher.
 *
 * Das Gerät gewährt dafür nur die persistable Lese-/Schreib-Freigabe für diesen
 * einen Baum. Alles läuft lokal; Inhalte gehen nie an Aiden.
 */
object DownloadCleaner {

    data class DocItem(
        val uri: Uri,
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val isDirectory: Boolean,
    )

    /** Rekursive Auflistung aller Dateien im gewählten Baum (Unterordner folgen). */
    fun listFolder(context: Context, treeUri: Uri): List<DocItem> {
        val result = mutableListOf<DocItem>()
        fun walk(dirUri: Uri, dirId: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, dirId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "Unbenannt"
                    val mime = cursor.getString(2) ?: "application/octet-stream"
                    val size = if (!cursor.isNull(3)) cursor.getLong(3) else 0L
                    if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        walk(treeUri, id)
                    } else {
                        result.add(
                            DocItem(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                documentId = id,
                                displayName = name,
                                mimeType = mime,
                                sizeBytes = size,
                                isDirectory = false,
                            )
                        )
                    }
                }
            }
        }
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return result
    }

    fun hash(context: Context, uri: Uri): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (buffered.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
            } ?: return null
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()
    }

    /** Exakte Duplikate: gruppiert Dateien gleicher Größe, dann gleichen SHA-256. */
    fun findExactDuplicates(context: Context, items: List<DocItem>): List<List<DocItem>> {
        val bySize = items.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }.values.flatten()
        val byHash = bySize.mapNotNull { item ->
            hash(context, item.uri)?.let { it to item }
        }.groupBy({ it.first }, { it.second })
        return byHash.values
            .filter { it.size > 1 }
            .sortedByDescending { it.sumOf { i -> i.sizeBytes } }
    }

    /**
     * Verschiebt Dateien in einen wiederherstellbaren „Hermes-Papierkorb“-Unterordner
     * im selben Speicherbaum. Nichts wird endgültig gelöscht.
     */
    fun moveToHermesTrash(context: Context, treeUri: Uri, items: List<DocItem>): Int {
        val resolver = context.contentResolver
        // Papierkorb-Ordner erzeugen (falls vorhanden, wiederverwenden).
        val trashDirId = ensureTrashDir(resolver, treeUri) ?: return 0
        val trashDir = DocumentsContract.buildDocumentUriUsingTree(treeUri, trashDirId)
        var moved = 0
        for (item in items) {
            runCatching {
                val newDoc = DocumentsContract.createDocument(
                    resolver, trashDir, item.mimeType, item.displayName,
                ) ?: return@runCatching
                // In den Papierkorb-Ordner kopieren, dann Original entfernen.
                val okCopy = resolver.openOutputStream(newDoc)?.use { out ->
                    resolver.openInputStream(item.uri)?.use { input -> input.copyTo(out) >= 0 }
                } ?: false
                if (okCopy) {
                    DocumentsContract.deleteDocument(resolver, item.uri)
                    moved++
                } else {
                    runCatching { DocumentsContract.deleteDocument(resolver, newDoc) }
                }
            }
        }
        return moved
    }

    private fun ensureTrashDir(resolver: android.content.ContentResolver, treeUri: Uri): String? {
        // Prüfen, ob bereits vorhanden; sonst erzeugen.
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        resolver.query(
            root,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "Hermes-Papierkorb") {
                    return cursor.getString(0)
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, "Hermes-Papierkorb",
        )?.let { DocumentsContract.getDocumentId(it) }
    }
}