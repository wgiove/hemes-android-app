package de.adversum.hermescompanion

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ein aus einer Benachrichtigung extrahierter Briefing-Eintrag (nur Metadaten, keine Inhalte außerhalb dieser App). */
data class BriefingItem(
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
)

/**
 * Lokaler, begrenzter Speicher für Benachrichtigungen, die das Tagesbriefing nutzt.
 * Rein lokal, keine automatische Weitergabe an Aiden.
 */
object BriefingStore {
    private const val PREFS = "hermes_briefing"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 200

    fun items(context: Context): List<BriefingItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            BriefingItem(
                app = o.optString("app"),
                title = o.optString("title"),
                text = o.optString("text"),
                time = o.optLong("time"),
            )
        }.sortedByDescending { it.time }
    }

    fun add(context: Context, item: BriefingItem) {
        val current = items(context).toMutableList()
        // Duplikate nach App + Titel vermeiden
        current.removeAll { it.app == item.app && it.title == item.title }
        current.add(0, item)
        val limited = current.take(MAX_ITEMS)
        val arr = JSONArray()
        limited.forEach {
            arr.put(JSONObject().apply {
                put("app", it.app)
                put("title", it.title)
                put("text", it.text)
                put("time", it.time)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply()
    }

    data class AvailableApp(val packageName: String, val label: String)

    /** Startbare Nutzer-Apps, die für die lokale Auswahl sinnvoll sichtbar sind. */
    fun availableApps(context: Context): List<AvailableApp> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(context.packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() } ?: packageName
                AvailableApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun enabledApps(context: Context): Set<String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_APPS, null) ?: DEFAULT_APPS
    }

    fun setEnabledApps(context: Context, apps: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_APPS, apps).apply()
    }

    private const val KEY_APPS = "enabled_apps"

    val DEFAULT_APPS: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.linkedin.android",
        "com.microsoft.office.outlook",
        "com.microsoft.todos",
        "com.google.android.gm",
        "com.google.android.calendar",
    )
}
