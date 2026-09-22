package io.frontierlabs.namaz

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.frontierlabs.namaz.core.Clock
import io.frontierlabs.namaz.core.DayTimes
import io.frontierlabs.namaz.core.Hijri
import io.frontierlabs.namaz.core.PrayerStatus
import io.frontierlabs.namaz.core.PrayerTimes
import io.frontierlabs.namaz.core.Strings
import io.frontierlabs.namaz.core.Tracker
import io.frontierlabs.namaz.core.streakSummary
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The home-screen widget.
 *
 * Shows the prayer you are in, when it started, when it becomes qaza, a live
 * countdown to that moment, whether you have marked it, how many of today's
 * five are done, and your current streak. It can be resized freely, and the
 * rows that no longer fit are hidden rather than clipped.
 *
 * **On keeping it current without draining the battery.** The obvious
 * implementation refreshes the widget every minute so the countdown stays
 * honest, which means waking this app 1,440 times a day to redraw something
 * nobody is looking at. Instead the countdown is a [android.widget.Chronometer],
 * which Android ticks itself inside the launcher's process; this app is not
 * involved at all. That leaves only the things which genuinely change at
 * known moments — the prayer name, the times, the marked state — and those
 * are pushed at exactly those moments:
 *
 *   - one alarm at the next prayer boundary, rescheduled as each one passes
 *   - whenever a prayer is marked, in the app or on the widget itself
 *   - when the city or calculation method changes
 *   - on boot, and when the alarms are re-armed
 *
 * The result is a handful of wakeups a day rather than one a minute.
 */
class NamazWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) render(context, manager, id)
        scheduleNextRefresh(context)
    }

    /** Resized: re-render so the right rows are shown for the new size. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        render(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MARK -> {
                runCatching { markCurrent(context) }
                refresh(context)
            }
            ACTION_REFRESH -> {
                refresh(context)
                scheduleNextRefresh(context)
            }
        }
    }

    /** The last widget was removed: stop the refresh alarm entirely. */
    override fun onDisabled(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(refreshIntent(context)) }
    }

    override fun onEnabled(context: Context) {
        scheduleNextRefresh(context)
    }

    companion object {

        private const val ACTION_MARK = "io.frontierlabs.namaz.WIDGET_MARK"
        private const val ACTION_REFRESH = "io.frontierlabs.namaz.WIDGET_REFRESH"
        private const val REFRESH_REQUEST_CODE = 950
        private const val MARK_REQUEST_CODE = 951
        private const val OPEN_REQUEST_CODE = 952

        /** Below this height the streak row and the button are hidden. */
        private const val MIN_HEIGHT_FOR_STATS = 100
        private const val MIN_HEIGHT_FOR_BUTTON = 150

        /** Redraw every placed widget. Safe to call from anywhere, any thread. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                for (id in ids(context, manager)) render(context, manager, id)
            }
        }

        private fun ids(context: Context, manager: AppWidgetManager): IntArray =
            runCatching {
                manager.getAppWidgetIds(ComponentName(context, NamazWidget::class.java))
            }.getOrDefault(IntArray(0))

        /** True when at least one widget is on a home screen. */
        fun anyPlaced(context: Context): Boolean = runCatching {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            ids(context, manager).isNotEmpty()
        }.getOrDefault(false)

        // --- drawing -------------------------------------------------------

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_namaz)
            val prefs = Prefs.get(context)
            val lang = Prefs.lang(prefs)
            val s = Strings.of(lang)
            val snap = snapshot(context)

            // How much room this particular widget has right now.
            val height = runCatching {
                manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            }.getOrDefault(110)

            if (snap == null) {
                // No city yet, or something unreadable. Say so rather than
                // drawing a confident but empty widget.
                views.setTextViewText(R.id.w_prayer, s.appName)
                views.setTextViewText(R.id.w_window, s.welcomeTitle)
                views.setViewVisibility(R.id.w_left, View.GONE)
                views.setViewVisibility(R.id.w_left_label, View.GONE)
                views.setViewVisibility(R.id.w_stats, View.GONE)
                views.setViewVisibility(R.id.w_mark, View.GONE)
                views.setOnClickPendingIntent(R.id.w_root, openApp(context))
                manager.updateAppWidget(id, views)
                return
            }

            val name = Strings.prayerName(lang, snap.prayer)
            views.setTextViewText(R.id.w_prayer, name)
            views.setTextColor(
                R.id.w_prayer,
                context.getColor(if (snap.urgent) R.color.w_red else R.color.w_gold),
            )

            views.setTextViewText(
                R.id.w_window,
                if (snap.current) "${snap.start.format12()}  →  ${snap.end.format12()}"
                else "${s.upNext}  ·  ${snap.start.format12()}",
            )
            views.setTextViewText(R.id.w_hijri, snap.hijri)

            // Marked / not marked, in the corner.
            views.setTextViewText(R.id.w_status, if (snap.done) "✓" else "○")
            views.setTextColor(
                R.id.w_status,
                context.getColor(if (snap.done) R.color.w_green else R.color.w_muted),
            )

            // The countdown. Android ticks this itself, once a second, without
            // waking this app -- see the note at the top of the class.
            views.setChronometerCountDown(R.id.w_left, true)
            views.setChronometer(
                R.id.w_left,
                SystemClock.elapsedRealtime() + snap.remainingMs,
                null,
                true,
            )
            views.setTextColor(
                R.id.w_left,
                context.getColor(
                    when {
                        snap.done -> R.color.w_green
                        snap.urgent -> R.color.w_red
                        snap.current -> R.color.w_green
                        else -> R.color.w_muted
                    }
                ),
            )
            views.setTextViewText(
                R.id.w_left_label,
                if (snap.current) s.left else s.beginsIn,
            )

            // Today's five, at a glance.
            val dots = intArrayOf(
                R.id.w_dot1, R.id.w_dot2, R.id.w_dot3, R.id.w_dot4, R.id.w_dot5)
            snap.statuses.forEachIndexed { i, status ->
                views.setImageViewResource(
                    dots[i],
                    when (status) {
                        PrayerStatus.DONE -> R.drawable.widget_dot_on
                        PrayerStatus.MISSED -> R.drawable.widget_dot_missed
                        else -> R.drawable.widget_dot_off
                    },
                )
            }
            views.setTextViewText(R.id.w_count, "${snap.doneCount} ${s.ofFiveToday}")
            views.setTextViewText(
                R.id.w_streak,
                if (snap.streak > 0)
                    "${snap.streak} ${if (snap.streak == 1) s.day else s.days}"
                else "",
            )

            // The button only makes sense for a prayer whose time has come.
            val canMark = snap.current
            views.setTextViewText(
                R.id.w_mark,
                if (snap.done) "✓  ${s.markedAsPrayed}" else s.markPrayed,
            )
            views.setInt(
                R.id.w_mark, "setBackgroundResource",
                if (snap.done) R.drawable.widget_button_done else R.drawable.widget_button,
            )
            views.setTextColor(
                R.id.w_mark,
                context.getColor(if (snap.done) R.color.w_green else R.color.w_ink),
            )
            views.setOnClickPendingIntent(R.id.w_mark, markIntent(context))

            // Hide what will not fit, smallest first.
            views.setViewVisibility(
                R.id.w_stats,
                if (height >= MIN_HEIGHT_FOR_STATS) View.VISIBLE
                else View.GONE,
            )
            views.setViewVisibility(
                R.id.w_mark,
                if (height >= MIN_HEIGHT_FOR_BUTTON && canMark) View.VISIBLE
                else View.GONE,
            )

            views.setOnClickPendingIntent(R.id.w_root, openApp(context))
            manager.updateAppWidget(id, views)
        }

        // --- what to draw --------------------------------------------------

        private class Snapshot(
            val prayer: String,
            val start: Clock,
            val end: Clock,
            val hijri: String,
            /** False when nothing is due and this is the *next* prayer instead. */
            val current: Boolean,
            val done: Boolean,
            val doneCount: Int,
            val streak: Int,
            val remainingMs: Long,
            val urgent: Boolean,
            val statuses: List<PrayerStatus>,
        )

        /**
         * Everything the widget needs, read straight from the same storage and
         * the same rules the app uses. Null when there is nothing sensible to
         * show yet.
         */
        private fun snapshot(context: Context): Snapshot? = runCatching {
            val prefs = Prefs.get(context)
            if (!Prefs.setupDone(prefs)) return null

            val city = Prefs.city(prefs)
            val settings = Prefs.settings(prefs)

            val now = LocalDateTime.now(PK)
            val nowMinutes = now.hour * 60 + now.minute
            val calendarDate = now.toLocalDate()

            // The prayer day runs Fajr to Fajr, exactly as in the app: after
            // midnight the widget must still show yesterday's Isha, not a
            // fresh empty day.
            val todayFajr = PrayerTimes.forDate(
                calendarDate.year, calendarDate.monthValue, calendarDate.dayOfMonth,
                city, settings,
            ).fajr
            val pd = Tracker.prayerDay(nowMinutes, todayFajr.minutes)
            val day = calendarDate.plusDays(pd.dayOffset.toLong())
            val t: DayTimes = PrayerTimes.forDate(
                day.year, day.monthValue, day.dayOfMonth, city, settings)

            val done = Prefs.readDone(prefs, day.year, day.monthValue, day.dayOfMonth)
            val statuses = Tracker.dayStatuses(pd.dayMinutes, t, done).map { it.second }
            val streak = streakSummary(day.year, day.monthValue, day.dayOfMonth) { y, m, d ->
                Tracker.isDayComplete(Prefs.readDone(prefs, y, m, d))
            }.current
            val hijriDate = Hijri.fromGregorian(
                day.year, day.monthValue, day.dayOfMonth, Prefs.hijriAdjust(prefs),
            )
            val hijri = "${hijriDate.day} ${Strings.hijriMonth(lang, hijriDate.month)} ${hijriDate.year} AH"

            val current = Tracker.currentPrayer(pd.dayMinutes, t)
            if (current != null) {
                val (name, start, end) = current
                val left = Tracker.minutesLeft(pd.dayMinutes, start, end)
                return@runCatching Snapshot(
                    prayer = name,
                    start = start,
                    end = end,
                    hijri = hijri,
                    current = true,
                    done = done.contains(name),
                    doneCount = Tracker.completedCount(done),
                    streak = streak,
                    // Seconds are not tracked anywhere in this app, so the
                    // countdown is anchored to the start of the current
                    // minute. It will read up to 59 seconds high, never low.
                    remainingMs = left * 60_000L,
                    urgent = left <= 30,
                    statuses = statuses,
                )
            }

            // Between sunrise and Zuhr nothing is due, so show what is next.
            val (_, nextName, minsToNext) = PrayerTimes.nextPrayer(Clock(nowMinutes), t)
            val nextStart = t.list().firstOrNull { it.first == nextName }?.second
                ?: return null
            val nextEnd = t.list().firstOrNull { it.first == nextName }?.third ?: nextStart
            Snapshot(
                prayer = nextName,
                start = nextStart,
                end = nextEnd,
                hijri = hijri,
                current = false,
                done = done.contains(nextName),
                doneCount = Tracker.completedCount(done),
                streak = streak,
                remainingMs = minsToNext * 60_000L,
                urgent = false,
                statuses = statuses,
            )
        }.getOrNull()

        // --- marking from the widget ---------------------------------------

        /**
         * Toggle the prayer currently open, using the same prayer-day rules as
         * the app so the two can never disagree about which day it belongs to.
         */
        private fun markCurrent(context: Context) {
            val prefs = Prefs.get(context)
            val city = Prefs.city(prefs)
            val settings = Prefs.settings(prefs)

            val now = LocalDateTime.now(PK)
            val nowMinutes = now.hour * 60 + now.minute
            val calendarDate: LocalDate = now.toLocalDate()

            val todayFajr = PrayerTimes.forDate(
                calendarDate.year, calendarDate.monthValue, calendarDate.dayOfMonth,
                city, settings,
            ).fajr
            val pd = Tracker.prayerDay(nowMinutes, todayFajr.minutes)
            val day = calendarDate.plusDays(pd.dayOffset.toLong())
            val t = PrayerTimes.forDate(
                day.year, day.monthValue, day.dayOfMonth, city, settings)

            // Only a prayer whose time has actually come can be marked, which
            // is the same rule the app's own list follows.
            val (name, _, _) = Tracker.currentPrayer(pd.dayMinutes, t) ?: return

            val done = Prefs.readDone(prefs, day.year, day.monthValue, day.dayOfMonth)
            val next = if (done.contains(name)) done - name else done + name
            Prefs.writeDone(prefs, day.year, day.monthValue, day.dayOfMonth, next)
        }

        // --- staying current ------------------------------------------------

        /**
         * One alarm, at the next moment the *text* changes: the end of the
         * current prayer's window, or the start of the next one. The countdown
         * between now and then looks after itself.
         */
        fun scheduleNextRefresh(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = refreshIntent(context)

            if (!anyPlaced(context)) {
                runCatching { am.cancel(pi) }
                return
            }

            val minutes = runCatching { minutesToNextChange(context) }.getOrNull() ?: 30
            val at = System.currentTimeMillis() + (minutes.coerceIn(1, 60) + 1) * 60_000L

            runCatching {
                // Inexact and not allowed to wake a dozing phone: a home-screen
                // widget only has to be right when someone might be looking at
                // it, and a screen that is on is a phone that is not dozing.
                am.set(AlarmManager.RTC, at, pi)
            }
        }

        private fun minutesToNextChange(context: Context): Int {
            val prefs = Prefs.get(context)
            val city = Prefs.city(prefs)
            val settings = Prefs.settings(prefs)

            val now = LocalDateTime.now(PK)
            val nowMinutes = now.hour * 60 + now.minute
            val date = now.toLocalDate()

            val todayFajr = PrayerTimes.forDate(
                date.year, date.monthValue, date.dayOfMonth, city, settings).fajr
            val pd = Tracker.prayerDay(nowMinutes, todayFajr.minutes)
            val day = date.plusDays(pd.dayOffset.toLong())
            val t = PrayerTimes.forDate(
                day.year, day.monthValue, day.dayOfMonth, city, settings)

            val current = Tracker.currentPrayer(pd.dayMinutes, t)
            return if (current != null) {
                Tracker.minutesLeft(pd.dayMinutes, current.second, current.third)
            } else {
                PrayerTimes.nextPrayer(Clock(nowMinutes), t).third
            }
        }

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, REFRESH_REQUEST_CODE,
                Intent(context, NamazWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun markIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, MARK_REQUEST_CODE,
                Intent(context, NamazWidget::class.java).setAction(ACTION_MARK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, OPEN_REQUEST_CODE,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
