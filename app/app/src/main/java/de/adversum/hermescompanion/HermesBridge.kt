package de.adversum.hermescompanion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimale Hermes-Serverbrücke.
 *
 * Überträgt einen bestätigten Auftrag (ChatMessage.Confirmation) an die
 * Hermes/Server-Brücke. Human-in-the-Loop wird bereits in der UI erzwungen —
 * diese Klasse wird ausschließlich nach Bestätigung aufgerufen.
 *
 * Serveradresse ist zunächst konfigurierbar ("" = nicht verbunden). Sobald
 * eine Pairing-Lösung existiert (siehe docs/ARCHITEKTUR.md), ersetzt diese
 * die statische URL.
 */
object HermesBridge {

    // Serveradresse aus der Build-Konfiguration (siehe build.gradle.kts).
    var serverUrl: String = de.adversum.hermescompanion.BuildConfig.HERMES_SERVER_URL
        private set

    fun isConfigured(): Boolean = serverUrl.isNotBlank()

    suspend fun sendAction(
        actionLabel: String,
        targetDescription: String,
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext "Keine Serververbindung konfiguriert. (Aiden-Brücke ist noch nicht verbunden.)"
        }
        runCatching {
            val payload = JSONObject()
                .put("action", actionLabel)
                .put("target", targetDescription)

            val conn = URL(serverUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            val body = conn.inputStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            if (code in 200..299) "Aiden: $body" else "Serverfehler ($code): ${body.take(200)}"
        }.getOrElse { throwable ->
            "Übertragung fehlgeschlagen: ${throwable.message}"
        }
    }
}