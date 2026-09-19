package io.frontierlabs.namaz

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.frontierlabs.namaz.core.Alert
import io.frontierlabs.namaz.core.AlertKind
import io.frontierlabs.namaz.core.Alerts
import io.frontierlabs.namaz.core.PrayerTimes
import java.time.LocalDate
import java.time.LocalTime

/**
 * Turning [Alerts] decisions into real Android alarms.
 *
 * The scheduling model is a rolling window rather than a repeating alarm.
 * Prayer times move a minute or two every day, so a `setRepeating` at a fixed
 * time of day would drift out of step within a fortnight. Instead the app
 * arms the next ten one-shot alarms — roughly the coming day — and re-arms
 * them whenever anything might have changed:
 *
 *   - the app is opened
 *   - the city or calculation method is changed
 *   - an alarm fires (so the chain refills itself)
 *   - the phone reboots, or its clock or time zone is changed
 *   - the app is updated
 *
 * Any one of those on its own keeps it alive. All of them together mean it
 * would take a phone that is off for a day, then opened without any prayer
 * passing, to miss anything — and opening the app fixes it immediately.
 */
object Scheduler {

    private const val ACTION_ALERT = "io.frontierlabs.namaz.ALERT"
    private const val ACTION_UPDATE = "io.frontierlabs.namaz.UPDATE_CHECK"
    private const val UPDATE_REQUEST_CODE = 900

    /** Cancel everything and arm the next window. */
    fun armAll(context: Context) {
        val prefs = Prefs.get(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return

        Notifications.ensureChannels(context)

        val prayerOn = Prefs.prayerAlerts(prefs)
        val qazaOn = Prefs.qazaAlerts(prefs)

        val city = Prefs.city(prefs)
        val settings = Prefs.settings(prefs)

        val today = LocalDate.now(PK)
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)
        fun timesFor(d: LocalDate) =
            PrayerTimes.forDate(d.year, d.monthValue, d.dayOfMonth, city, settings)

        val now = LocalTime.now(PK)
        val nowMinutes = now.hour * 60 + now.minute

        // Yesterday matters between midnight and Fajr: the Isha running then
        // is yesterday's, and its qaza warning is the next alert due.
        val wanted = Alerts
            .toArm(
                timesFor(yesterday), timesFor(today), timesFor(tomorrow),
                nowMinutes, Prefs.warnMinutes(prefs),
            )
            .filter {
                when (it.kind) {
                    AlertKind.PRAYER_START -> prayerOn
                    AlertKind.QAZA_WARNING -> qazaOn
                }
            }

        // Clear every slot first. A switch turned off, or a city moved west,
        // must not leave yesterday's alarm still armed in an unused slot.
        cancelAll(context, am)

        if (!Notifications.allowed(context)) return

        val midnight = today.atStartOfDay(PK).toInstant().toEpochMilli()
        for (alert in wanted) {
            val at = midnight + alert.at * 60_000L
            // The day the PRAYER belongs to, which is not the day the alarm
            // rings on: Isha's warning rings tomorrow about today's Isha.
            // The receiver needs this to read the right set of marked
            // prayers. See Alert.fromDayOffset.
            set(context, am, alert, at, today.plusDays(alert.fromDayOffset.toLong()))
        }

        armUpdateCheck(context, am)
    }

    private fun set(
        context: Context,
        am: AlarmManager,
        alert: Alert,
        atMillis: Long,
        baseDay: LocalDate,
    ) {
        val pi = alertIntent(context, alert, baseDay)
        // setExactAndAllowWhileIdle punches through Doze, which matters most
        // for Fajr — the one alarm that always lands while the phone is idle.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            // Without the exact-alarm permission Android still delivers it,
            // just with slack. Late is better than silent.
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun alertIntent(
        context: Context,
        alert: Alert,
        baseDay: LocalDate,
    ): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = ACTION_ALERT
            // Extras are ignored when Android decides whether two
            // PendingIntents are "the same", so the distinguishing detail has
            // to live in the data Uri. Without this, ten alarms would collapse
            // into one and only the last would ever fire.
            data = Uri.parse("namaz://alert/${alert.kind.name}/${alert.prayer}")
            putExtra("kind", alert.kind.name)
            putExtra("prayer", alert.prayer)
            putExtra("at", alert.at)
            putExtra("y", baseDay.year)
            putExtra("m", baseDay.monthValue)
            putExtra("d", baseDay.dayOfMonth)
        }
        return PendingIntent.getBroadcast(
            context, alert.requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        val today = LocalDate.now(PK)
        for (kind in AlertKind.entries) {
            for (prayer in io.frontierlabs.namaz.core.Tracker.FARD) {
                runCatching {
                    am.cancel(alertIntent(context, Alert(kind, prayer, 0), today))
                }
            }
        }
    }

    // --- the daily "is there a new version?" check -------------------------

    /**
     * One inexact alarm a day. Inexact on purpose: nothing depends on the
     * minute, and letting Android batch it with other wakeups costs the
     * battery almost nothing. Repeating alarms are dropped on reboot, which
     * is one of the reasons [BootReceiver] exists.
     */
    private fun armUpdateCheck(context: Context, am: AlarmManager) {
        val pi = PendingIntent.getBroadcast(
            context, UPDATE_REQUEST_CODE,
            Intent(context, UpdateReceiver::class.java).setAction(ACTION_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
                AlarmManager.INTERVAL_DAY,
                pi,
            )
        }
    }
}
