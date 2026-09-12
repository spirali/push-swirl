package org.kreatrix.pushswirl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Optional heads-up before the next planned session: when the countdown is on and reminders are
 * enabled, posts a notification the configured number of minutes before the session is due.
 *
 * The reminder is a single alarm re-armed whenever anything it depends on changes (a finished
 * session moves the target, as do the countdown and reminder settings). Nothing polls.
 */
object SessionReminder {

    const val ACTION_REMIND = "org.kreatrix.pushswirl.SESSION_REMINDER"
    private const val CHANNEL_ID = "pushswirl_reminder"
    private const val NOTIFICATION_ID = 2
    private const val REQUEST_CODE = 200

    /**
     * Cancels any pending reminder and arms the next one, if there is a future moment to
     * remind about. Safe to call at any time; call it whenever the schedule may have moved.
     */
    fun reschedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmPendingIntent(context)
        alarms.cancel(pending)

        val storage = SessionStorage(context)
        if (!storage.loadCountdownEnabled() || !storage.loadReminderEnabled()) return

        val lastSession = storage.loadSessions().maxByOrNull { it.timestamp }?.timestamp ?: return
        val dueAt = lastSession + storage.loadCountdownIntervalMinutes() * 60_000L
        val remindAt = dueAt - storage.loadReminderMinutesBefore() * 60_000L
        if (remindAt <= System.currentTimeMillis()) return

        // Exact alarms need a special permission on Android 12+; this one needs none and still
        // fires in Doze, at the cost of the system being allowed to shift it by a few minutes.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
    }

    /** Posts the reminder, unless a session is already under way. */
    fun notifyNow(context: Context) {
        val storage = SessionStorage(context)
        if (storage.loadActiveSessionSnapshot() != null) return
        if (!storage.loadCountdownEnabled() || !storage.loadReminderEnabled()) return

        val minutes = storage.loadReminderMinutesBefore()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Session reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Reminds you shortly before a planned session" }
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Session coming up")
            .setContentText(
                if (minutes == 1) "Your planned session starts in 1 minute"
                else "Your planned session starts in $minutes minutes"
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SessionReminderReceiver::class.java).setAction(ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

/**
 * Delivers the reminder alarm, and re-arms both the reminder and the widget's own wake-up after
 * a reboot or an app update, since pending alarms do not survive either.
 */
class SessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SessionReminder.ACTION_REMIND -> {
                SessionReminder.notifyNow(context)
                // Arm the next one (the target moves once a session is recorded).
                SessionReminder.reschedule(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SessionReminder.reschedule(context)
                SessionWidget.refresh(context)
            }
        }
    }
}
