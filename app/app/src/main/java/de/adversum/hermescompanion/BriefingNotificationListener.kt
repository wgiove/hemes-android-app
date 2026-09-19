package de.adversum.hermescompanion

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Build

/**
 * Sammelt Benachrichtigungen ausgewählter Apps für das lokale Tagesbriefing.
 *
 * Nur der Benutzername/der Betrefftitel, der kurze Text und der Zeitstempel werden
 * lokal gespeichert. Es wird nichts verschickt und nichts weiterverarbeitet. Die
 * Auswahl der Apps liegt unter Einstellungen im Menü.
 */
class BriefingNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val pkg = sbn.packageName
        val enabled = BriefingStore.enabledApps(this)
        if (pkg !in enabled) return

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = appLabel(pkg)
        BriefingStore.add(
            this,
            BriefingItem(app = appLabel, title = title, text = text, time = sbn.postTime),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    private fun appLabel(pkg: String): String = when (pkg) {
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        "com.instagram.android" -> "Instagram"
        "com.linkedin.android" -> "LinkedIn"
        "com.microsoft.office.outlook" -> "Outlook"
        "com.microsoft.todos" -> "Microsoft To Do"
        "com.google.android.gm" -> "Gmail"
        "com.google.android.calendar" -> "Kalender"
        else -> {
            val pm = packageManager
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
        }
    }

    /** Öffnet das Tagesbriefing direkt über eine Benachrichtigung. */
    companion object {
        fun briefingPendingIntent(context: android.content.Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "de.adversum.hermescompanion.OPEN_BRIEFING"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getActivity(context, 0, intent, flags)
        }
    }
}
