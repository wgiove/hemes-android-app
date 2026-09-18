package de.adversum.hermescompanion

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.roundToLong

/**
 * Lokaler Medien-Zugriff (Read-only).
 *
 * Listet Fotos und Videos über den MediaStore auf — mit Metadaten für
 * Duplikaterkennung und spätere Übertragung an Hermes.
 *
 * Designhinweis (Datenschutz): Es werden nur Metadaten gelesen, keine
 * Originalinhalte dauerhaft gespeichert. Zugriff nur bei Freigabe.
 */
object MediaScanner {

    data class MediaStats(val images: Long, val videos: Long)

    data class MediaItem(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val durationMs: Long,
        val dateTakenMs: Long,
        val isVideo: Boolean,
    ) {
        val sizeHuman: String
            get() = when {
                sizeBytes >= 1_048_576 -> "%.1f MB".format(sizeBytes / 1_048_576.0)
                sizeBytes >= 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
                else -> "$sizeBytes B"
            }

        val durationHuman: String
            get() = if (durationMs <= 0) ""
            else {
                val s = (durationMs / 1000.0).roundToLong()
                "%d:%02d".format(s / 60, s % 60)
            }
    }

    fun countMedia(context: Context): MediaStats {
        val images = count(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videos = count(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return MediaStats(images, videos)
    }

    /** Liefert Fotos (danach Videos) mit Metadaten, neueste zuerst. */
    fun listMedia(context: Context): List<MediaItem> {
        return buildList {
            addAll(query(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false,
                projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_TAKEN,
                ),
            ))
            addAll(query(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true,
                projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_TAKEN,
                ),
            ))
        }
    }

    private fun count(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                c.count.toLong()
            } ?: 0L
        }.getOrElse { 0L }
    }

    private fun query(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        projection: Array<String>,
    ): List<MediaItem> {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_TAKEN} DESC")
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val durCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                    buildList {
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            var mime: String? = null
                            if (mimeCol >= 0) mime = cursor.getString(mimeCol)
                            add(
                                MediaItem(
                                    id = id,
                                    uri = Uri.withAppendedPath(uri, id.toString()),
                                    displayName = cursor.getString(nameCol) ?: "Unbenannt",
                                    mimeType = mime,
                                    sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                                    durationMs = if (durCol >= 0) cursor.getLong(durCol) else 0L,
                                    dateTakenMs = if (dateCol >= 0) cursor.getLong(dateCol) else 0L,
                                    isVideo = isVideo,
                                )
                            )
                        }
                    }
                } ?: emptyList()
        }.getOrElse { emptyList() }
    }
}