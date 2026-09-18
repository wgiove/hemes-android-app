package de.adversum.hermescompanion

import android.app.ActivityManager
import android.content.Context

/**
 * Misst die lokale Leistung des Geräts, damit wir abschätzen können,
 * ob ein on-device LLM (z. B. Gemma 3 1B) hier sinnvoll flüssig läuft.
 *
 * Gemessen wird: Abruf der Medienliste, SHA-256-Hashing (als Anhaltspunkt
 *identischer Dateien) und System-RAM.
 */
object PerformanceBenchmark {

    data class Result(
        val mediaScanMs: Long,
        val hashMeasuredFiles: Int,
        val hashMsPerFile: Long,
        val availableRamMb: Long,
        val totalRamMb: Long,
    ) {
        val summary: String
            get() = buildString {
                append("Scan: ${mediaScanMs} ms · ")
                append("Hash: ${hashMsPerFile} ms/Datei (${hashMeasuredFiles} Dateien)")
                append(" · RAM frei: ${availableRamMb} MB / ${totalRamMb} MB")
            }
    }

    fun runBenchmark(context: Context): Result {

        // 1) Medienliste laden (read-only scan)
        val mediaScanStarted = System.currentTimeMillis()
        val items = MediaScanner.listMedia(context)
        val mediaScanMs = System.currentTimeMillis() - mediaScanStarted

        // 2) SHA-256 für die erste Gruppe gleich-große Datein (konservativ: max. 20)
        val hashCandidates = items
            .groupBy { it.sizeBytes }
            .filter { it.value.size > 1 }
            .values
            .flatten()
            .take(20)

        val hashStart = System.currentTimeMillis()
        val hashable = hashCandidates.count { DuplicateScanner.hashItem(context, it) != null }
        var hashMsPerFile = 0L
        if (hashable > 0) hashMsPerFile = (System.currentTimeMillis() - hashStart) / hashable

        // 3) RAM-Auskunft
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        return Result(
            mediaScanMs = mediaScanMs,
            hashMeasuredFiles = hashable,
            hashMsPerFile = hashMsPerFile,
            availableRamMb = availRamMb,
            totalRamMb = totalRamMb,
        )
    }
}