package de.adversum.hermescompanion

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Lokaler Medien-Zugriff (Read-only).
 *
 * Zählt Fotos und Videos über den MediaStore. Dient als verifizierbare Grundlage
 * für Duplikaterkennung und Übertragung an Hermes.
 *
 * Designhinweis (Datenschutz): Es wird nichts gespeichert — nur gezählt.
 * Der echte Scan für Duplikate liefert Hashes + Metadaten, keine Inhalte,
 * und nur bei Freigabe.
 */
object MediaScanner {

    data class MediaStats(val images: Long, val videos: Long)

    fun countMedia(context: Context): MediaStats {
        val images = count(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videos = count(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return MediaStats(images, videos)
    }

    private fun count(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                c.count.toLong()
            } ?: 0L
        }.getOrElse { 0L }
    }
}