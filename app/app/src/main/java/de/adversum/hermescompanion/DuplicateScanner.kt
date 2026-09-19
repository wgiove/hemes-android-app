package de.adversum.hermescompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /**
     * Erkennt ähnliche Fotos über einen perceptual Hash (dHash).
     * Findet Doppel, die SHA-256 nicht sieht: re-komprimierte, erneut gespeicherte
     * oder leicht angepasste Bilder mit gleichem Motiv.
     *
     * Nur Bilder (isVideo == false). Rein lokal, keine Übertragung.
     */
    fun findSimilarImages(
        context: Context,
        items: List<MediaScanner.MediaItem>,
        threshold: Int = 10,
        onProgress: ((Int, Int) -> Boolean)? = null,
    ): List<List<MediaScanner.MediaItem>> {
        val images = items.filter { !it.isVideo }
        if (images.size < 2) return emptyList()
        val maxScan = images.take(800) // begrenzt Laufzeit auf dem Gerät

        // dHash pro Bild berechnen (nur Fotos, Größenbuckets begrenzen Vergleiche).
        data class Bucket(val item: MediaScanner.MediaItem, val hash: Long)
        val buckets = mutableListOf<Bucket>()
        maxScan.forEachIndexed { index, img ->
            if (index % 25 == 0 && onProgress != null && !onProgress(index + 1, maxScan.size)) {
                return emptyList()
            }
            dHash(context, img.uri)?.let { buckets.add(Bucket(img, it)) }
        }
        onProgress?.invoke(maxScan.size, maxScan.size)

        val groups = mutableListOf<MutableList<MediaScanner.MediaItem>>()
        val used = BooleanArray(buckets.size)
        for (i in buckets.indices) {
            if (used[i]) continue
            val group = mutableListOf(buckets[i].item)
            used[i] = true
            for (j in i + 1 until buckets.size) {
                if (used[j]) continue
                if (hamming(buckets[i].hash, buckets[j].hash) <= threshold) {
                    group.add(buckets[j].item)
                    used[j] = true
                }
            }
            if (group.size > 1) groups.add(group)
        }
        return groups.sortedByDescending { it.size }
    }

    /** dHash (difference hash): skaliert auf 9x8 Graustufen, 64-Bit-Fingerabdruck. */
    private fun dHash(context: Context, uri: android.net.Uri): Long? {
        return runCatching {
            // 1) Größe ermitteln, OHNE das Bild zu dekodieren.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2) Bild stark verkleinert dekodieren (Ziel ~ 64 px kurze Kante).
            var sample = 1
            val shorterSide = minOf(bounds.outWidth, bounds.outHeight)
            while (shorterSide / sample > 128) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val small = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            // 3) Nur noch auf 9x8 verkleinern — von einem bereits kleinen Bitmap.
            val scaled = Bitmap.createScaledBitmap(small, 9, 8, true)
            if (scaled !== small) small.recycle()

            val pixels = IntArray(9 * 8)
            scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            scaled.recycle()

            var hash = 0L
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val left = gray(pixels[row * 9 + col])
                    val right = gray(pixels[row * 9 + col + 1])
                    if (left > right) hash = hash or (1L shl (row * 8 + col))
                }
            }
            hash
        }.getOrNull()
    }

    private fun gray(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
