package org.kreatrix.pushswirl

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Home-screen widget showing the day number, when the next session is due and when the last
 * one was — or a "Running" badge while a session is going.
 *
 * It reads straight from [SessionStorage], so it renders correctly with no app process alive.
 * Times are absolute ("Tomorrow 22:00") rather than counting down, so they do not go stale
 * between refreshes; only the relative day word does, which is why the widget refreshes
 * hourly and schedules an exact-ish wake-up for the two moments that change what it says:
 * the next local midnight, and the instant the next session becomes overdue.
 */
class SessionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views) }
        scheduleNextRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        // Last widget removed: stop waking up for it.
        alarmManager(context)?.cancel(refreshPendingIntent(context))
    }

    companion object {
        private const val ACTION_REFRESH = "org.kreatrix.pushswirl.WIDGET_REFRESH"

        /** Redraws every placed widget and re-arms the next wake-up. Safe when none exist. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SessionWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
            scheduleNextRefresh(context)
        }

        /**
         * Arms a single alarm for the next moment the text changes meaning — midnight (the day
         * words shift) or the countdown target (the next session turns overdue), whichever comes
         * first. One-shot and re-armed on each redraw, so there is no periodic polling. Inexact
         * and non-waking: it lands the next time the device is up, which is when it can be seen.
         */
        private fun scheduleNextRefresh(context: Context) {
            val storage = SessionStorage(context)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()

            val nextMidnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val overdueAt = countdownTarget(storage)?.takeIf { it > now }

            val triggerAt = listOfNotNull(nextMidnight, overdueAt).min()
            alarmManager(context)?.set(AlarmManager.RTC, triggerAt, refreshPendingIntent(context))
        }

        private fun alarmManager(context: Context): AlarmManager? =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, SessionWidget::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context, 100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /** When the next session is due, or null when the countdown is off / there is no history. */
        private fun countdownTarget(storage: SessionStorage): Long? {
            if (!storage.loadCountdownEnabled()) return null
            val last = storage.loadSessions().maxByOrNull { it.timestamp }?.timestamp ?: return null
            return last + storage.loadCountdownIntervalMinutes() * 60_000L
        }

        private fun buildViews(context: Context): RemoteViews {
            val storage = SessionStorage(context)
            val views = RemoteViews(context.packageName, R.layout.widget_session_status)

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))

            val now = System.currentTimeMillis()
            val day0 = storage.loadDay0Date()
            views.setTextViewText(
                R.id.widget_day,
                if (day0 == null) "Push & Swirl"
                else "Day ${((now - day0) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)}"
            )

            // A saved snapshot means a session is in progress; it outlives the app process.
            val running = storage.loadActiveSessionSnapshot() != null
            views.setViewVisibility(R.id.widget_running, if (running) View.VISIBLE else View.GONE)

            val lastSession = storage.loadSessions().maxByOrNull { it.timestamp }?.timestamp
            views.setTextViewText(
                R.id.widget_last,
                if (lastSession == null) "No sessions yet"
                else "Last  ${formatMoment(context, lastSession, now)}"
            )

            // While a session runs the countdown still points at the previous one, so hide it.
            val target = if (running) null else countdownTarget(storage)
            if (target == null) {
                views.setViewVisibility(R.id.widget_next, View.GONE)
            } else {
                val overdue = target <= now
                views.setViewVisibility(R.id.widget_next, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_next,
                    (if (overdue) "Overdue  " else "Next  ") + formatMoment(context, target, now)
                )
                views.setTextColor(
                    R.id.widget_next,
                    context.getColor(if (overdue) R.color.widget_overdue else R.color.widget_accent)
                )
            }

            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * A calendar-relative day plus the absolute clock time, e.g. "Today 10:30",
         * "Yesterday 17:30", "Tomorrow 22:00", "-2 day 10:11", "+2 day 10:11".
         * The time follows the device's 12/24-hour setting.
         */
        private fun formatMoment(context: Context, timestamp: Long, now: Long): String {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val label = when (val days = ChronoUnit.DAYS.between(today, day)) {
                0L -> "Today"
                -1L -> "Yesterday"
                1L -> "Tomorrow"
                else -> if (days < 0) "$days day" else "+$days day"
            }
            return "$label ${DateFormat.getTimeFormat(context).format(Date(timestamp))}"
        }
    }
}
