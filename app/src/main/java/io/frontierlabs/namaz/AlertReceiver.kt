package io.frontierlabs.namaz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.frontierlabs.namaz.core.Alert
import io.frontierlabs.namaz.core.AlertKind
import io.frontierlabs.namaz.core.Alerts
import io.frontierlabs.namaz.core.PrayerTimes
import io.frontierlabs.namaz.core.Tracker
import java.time.LocalTime

/**
 * One prayer alarm has gone off.
 *
 * The check that matters is [Alerts.stillRelevant]. An alarm set hours ago
 * can arrive after the prayer was already marked, or — if the phone sat in
 * Doze — long after the window closed. Telling someone at 9 PM that "Asr is
 * about to become qaza" is worse than saying nothing, so both cases stay
 * silent.
 *
 * Either way the receiver re-arms the chain before it returns, which is what
 * keeps the rolling window rolling.
 */
class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { handle(context, intent) }
        // Re-arm even if handling threw: a broken notification is a nuisance,
        // a dead alarm chain is the whole feature gone.
        runCatching { Scheduler.armAll(context) }
    }

    private fun handle(context: Context, intent: Intent) {
        val kindName = intent.getStringExtra("kind") ?: return
        val prayer = intent.getStringExtra("prayer") ?: return
        if (prayer !in Tracker.FARD) return

        val kind = runCatching { AlertKind.valueOf(kindName) }.getOrNull() ?: return
        val at = intent.getIntExtra("at", -1)
        if (at < 0) return

        val y = intent.getIntExtra("y", 0)
        val m = intent.getIntExtra("m", 0)
        val d = intent.getIntExtra("d", 0)
        if (y == 0) return

        val prefs = Prefs.get(context)
        val on = when (kind) {
            AlertKind.PRAYER_START -> Prefs.prayerAlerts(prefs)
            AlertKind.QAZA_WARNING -> Prefs.qazaAlerts(prefs)
        }
        if (!on || !Notifications.allowed(context)) return

        val alert = Alert(kind, prayer, at)
        // The marked set belongs to the day the PRAYER is from, not the day
        // the alarm rang on. For Isha's warning those are different days.
        val done = Prefs.readDone(prefs, y, m, d)

        val now = LocalTime.now(PK)
        val nowMinutes = now.hour * 60 + now.minute
        if (!Alerts.stillRelevant(alert, nowMinutes, done)) return

        Notifications.ensureChannels(context)

        // How long is actually left, recomputed now rather than trusted from
        // the alarm — if it fired two minutes late, say eleven minutes, not
        // thirteen.
        val minutesLeft = if (kind == AlertKind.QAZA_WARNING) {
            val times = PrayerTimes.forDate(
                y, m, d, Prefs.city(prefs), Prefs.settings(prefs))
            val end = times.list().firstOrNull { it.first == prayer }?.third
            if (end == null) Prefs.warnMinutes(prefs)
            else Tracker.minutesUntil(io.frontierlabs.namaz.core.Clock(nowMinutes), end)
        } else 0

        Notifications.post(context, alert, minutesLeft)
    }
}
