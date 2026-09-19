package de.adversum.hermescompanion

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Kleiner lokaler Crash-Handler: schreibt Stacktraces in eine Datei im App-Speicher,
 * damit ein Absturz nicht „in den Leeren läuft“, sondern reproduzierbar gelesen werden kann.
 * Rein lokal, keine Netzwerkübertragung.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val MAX_BYTES = 300_000L

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            append(app, throwable)
            prev?.uncaughtException(thread, throwable) ?: Runtime.getRuntime().exit(2)
        }
    }

    fun append(context: Context, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val line = "--- ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} [${threadName()}]\n${sw}\n\n"
            val f = File(context.filesDir, FILE)
            val existing = if (f.exists()) f.readText() else ""
            f.writeText((line + existing).take(MAX_BYTES.toInt()))
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()
            ?.ifBlank { null }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun threadName(): String = Thread.currentThread().name
}
