package de.adversum.hermescompanion

import android.content.Context
import java.io.BufferedInputStream
import java.security.MessageDigest

/**
 * Tokenfreie, lokale Erkennung exakter Datei-Duplikate.
 *
 * Es werden keine Medien übertragen und kein Modell aufgerufen. Zuerst wird
 * nach Dateigröße gruppiert; nur gleich große Dateien werden vollständig
 * gehasht. Dadurch werden unnötige Lesevorgänge vermieden.
 */
object DuplicateScanner {

    data class DuplicateGroup(
        val hash: String,
        val items: List<MediaScanner.MediaItem>,
        val totalBytes: Long,
    )

    fun findExactDuplicates(
        context: Context,
        items: List<MediaScanner.MediaItem>,
    ): List<DuplicateGroup> {
        val candidates = items.groupBy { it.sizeBytes }
            .filter { (_, sameSize) -> sameSize.size > 1 }
            .values
            .flatten()

        val byHash = candidates.mapNotNull { item ->
            hashItem(context, item)?.let { hash -> hash to item }
        }.groupBy({ it.first }, { it.second })

        return byHash.values
            .filter { it.size > 1 }
            .map { group ->
                val hash = hashItem(context, group.first()) ?: ""
                DuplicateGroup(hash, group, group.sumOf { it.sizeBytes })
            }
            .sortedByDescending { it.totalBytes }
    }

    fun hashItem(context: Context, item: MediaScanner.MediaItem): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(item.uri)?.use { input ->
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
}
