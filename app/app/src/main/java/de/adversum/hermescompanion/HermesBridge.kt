package de.adversum.hermescompanion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hermes-Serverbrücke.
 *
 * Verdrahtet die App mit der Django-Serverbrücke (`server/`) und setzt die
 * echten Endpunkte um:
 *   - `POST /api/pair`            Pairing-Code anfordern (Human-in-the-Loop:
 *                                  Aiden/Werner müssen bestätigen, bevor die App
 *                                  den Token holt)
 *   - `POST /api/pair/authorize`  (nur Server-seitig, via Aiden/Telegram)
 *   - `POST /api/pair/confirm`    gültiger Code nach authorize → Bearer-Token
 *   - `POST /api/command`         bestätigten Auftrag senden (Bearer-Auth)
 *
 * Der Token wird lokal in SharedPreferences gehalten; die Server-URL ist per
 * Einstellung überschreibbar (Default: BuildConfig).
 */
object HermesBridge {

    private const val PREFS = "hermes_bridge"
    private const val KEY_URL = "server_url"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_PAIR_CODE = "pairing_code"

    var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun serverUrl(): String {
        val sp = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp?.getString(KEY_URL, de.adversum.hermescompanion.BuildConfig.HERMES_SERVER_URL)
            ?: de.adversum.hermescompanion.BuildConfig.HERMES_SERVER_URL
    }

    fun setServerUrl(url: String) {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_URL, url.trim().removeSuffix("/"))
            ?.apply()
    }

    fun token(): String? {
        val ctx = context ?: return null
        // Migration älterer Builds: Klartext-Token einmalig verschlüsseln und löschen.
        SecureStore.get(ctx, KEY_TOKEN)?.let { return it }
        val legacy = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
        if (!legacy.isNullOrBlank()) {
            SecureStore.put(ctx, KEY_TOKEN, legacy)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_TOKEN).apply()
        }
        return legacy
    }

    fun saveToken(token: String) {
        runCatching {
            context?.let { ctx ->
                SecureStore.put(ctx, KEY_TOKEN, token)
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_TOKEN).apply()
            }
        }
    }

    /** Letzten Pairing-Code merken, damit der Token später erneut abgeholt werden kann. */
    fun savePairingCode(code: String) {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_PAIR_CODE, code)?.apply()
    }

    fun lastPairingCode(): String? =
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_PAIR_CODE, null)

    fun isPaired(): Boolean = !token().isNullOrBlank()

    fun isConfigured(): Boolean = serverUrl().isNotBlank()

    /** Fordert einen Pairing-Code an; gibt den Code zurück. */
    suspend fun startPairing(deviceName: String): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext "ERROR_NO_SERVER"
        runCatching {
            val payload = JSONObject().put("name", deviceName)
            val (code, body) = post("/api/pair", payload.toString(), null)
            if (code in 200..299) {
                JSONObject(body).optString("pairing_code").takeIf { it.isNotBlank() }
                    ?: "ERROR_NO_CODE"
            } else {
                "ERROR_HTTP_$code"
            }
        }.getOrElse { "ERROR_${it.message?.take(40) ?: "uknown"}" }
    }

    /** Bestätigt den Pairing-Code (wird von Aiden/Telegram serverseitig gemacht; App nicht nötig). */

    /** Holt den Bearer-Token, nachdem Aiden/Werner per `/api/pair/authorize` freigegeben hat. */
    suspend fun confirmPairing(code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        runCatching {
            val payload = JSONObject().put("code", code)
            val (respCode, body) = post("/api/pair/confirm", payload.toString(), null)
            if (respCode in 200..299) {
                val token = JSONObject(body).optString("token")
                if (token.isNotBlank()) {
                    saveToken(token)
                    true
                } else false
            } else false
        }.getOrElse { false }
    }

    /** Sendet einen bestätigten Auftrag mit Bearer-Token. */
    suspend fun sendAction(actionLabel: String, targetDescription: String): String =
        withContext(Dispatchers.IO) {
            val tok = token()
            if (!isConfigured()) return@withContext "Keine Serververbindung konfiguriert (Aiden-Brücke nicht verbunden)."
            if (tok.isNullOrBlank()) return@withContext "Gerät noch nicht gekoppelt — bitte paarung abschließen."
            runCatching {
                val payload = JSONObject()
                    .put("action", actionLabel)
                    .put("target", targetDescription)
                val (code, body) = post("/api/command", payload.toString(), tok)
                if (code in 200..299) "Aiden: ${JSONObject(body).optString("status")}" else "Serverfehler ($code): ${body.take(200)}"
            }.getOrElse { "Übertragung fehlgeschlagen: ${it.message}" }
        }

    private fun post(path: String, body: String, token: String?): Pair<Int, String> {
        val conn = URL(serverUrl() + path).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val respCode = conn.responseCode
        val resp = (
            try {
                conn.inputStream?.bufferedReader()?.readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText()
            }
            ) ?: ""
        conn.disconnect()
        return respCode to resp
    }
}