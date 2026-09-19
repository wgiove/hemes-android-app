package de.adversum.hermescompanion

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Stößt einmal am Tag (Morgen) die lokale Briefing-Erstellung an und zeigt eine
 * System-Benachrichtigung an, die zur Briefing-Ansicht führt.
 */
class BriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val items = BriefingStore.items(context)
        val summary = BriefingBuilder.defaultSummary(items)
        val title = "Guten Morgen, Werner"
        val text = summary.firstOrNull()?.let { "📋 $it" } ?: "Keine neuen Benachrichtigungen. Tippe zum Öffnen des Tagesbriefings."

        val channelId = "hermes_briefing"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Tagesbriefing",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Morgendliche Zusammenfassung lokaler Benachrichtigungen" }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (text.length > 80) text.take(77) + "…" else text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(BriefingNotificationListener.briefingPendingIntent(context))
            .setAutoCancel(true)
            .build()
        nm.notify(101, notification)
    }

    companion object {
        const val ACTION = "de.adversum.hermescompanion.DAILY_BRIEFING"
        private const val REQUEST = 9001
        private const val PREFS = "hermes_briefing"
        private const val KEY_TIME_MIN = "briefing_time_min"

        fun schedule(context: Context, hour: Int, minute: Int) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            val cal = Calendar.getInstance().apply {
                val now = System.currentTimeMillis()
                val today = Calendar.getInstance()
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.MONTH, today.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_TIME_MIN, hour * 60 + minute).apply()
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context))
        }

        fun configuredTime(context: Context): Pair<Int, Int>? {
            val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TIME_MIN, -1)
            return if (v >= 0) Pair(v / 60, v % 60) else null
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, BriefingAlarmReceiver::class.java).apply { action = ACTION }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
        }
    }
}