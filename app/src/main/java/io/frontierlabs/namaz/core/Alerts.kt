package io.frontierlabs.namaz.core

/**
 * What to notify, and when. Pure Kotlin so the timing rules can be tested on a
 * JVM — the Android side (AlarmManager, notification channels) only carries
 * out what this file decides.
 *
 * Two kinds of alert:
 *
 *  - **Prayer start.** Fired at the azan time, once per prayer.
 *  - **Qaza warning.** Fired [DEFAULT_WARN_MINUTES] before the window closes,
 *    and only if the prayer has not been marked. This is the one that has to
 *    be right: a warning that arrives after the prayer is already qaza is
 *    worse than no warning at all.
 *
 * The hard part is midnight. Isha's window ends at the next day's Subh Sadiq,
 * so its qaza warning lands on the *following* calendar day, typically around
 * 4 AM. Minutes here are therefore counted from midnight of the base day and
 * are allowed to exceed 1440; the Android side turns that into a wall-clock
 * instant by adding to the base day's midnight.
 */

enum class AlertKind {
    /** The prayer's time has just begun. */
    PRAYER_START,

    /** The window is about to close and nothing has been marked. */
    QAZA_WARNING,
}

data class Alert(
    val kind: AlertKind,
    /** English prayer name, as used by [Tracker.FARD]. */
    val prayer: String,
    /**
     * Minutes from midnight of the base day. May be 1440 or more when the
     * alert falls on the day after the one it was computed from — Isha's qaza
     * warning always does.
     */
    val at: Int,

    /**
     * Which day's timetable this prayer belongs to, counted from the base
     * day: -1 yesterday, 0 today, 1 tomorrow.
     *
     * This is **not** the same as [dayOffset], and conflating the two is a
     * real bug rather than a nicety. Isha's qaza warning rings tomorrow
     * morning about a prayer that belongs to *today* (dayOffset 1,
     * fromDayOffset 0), while tomorrow's Fajr alert rings at a similar hour
     * about a prayer that belongs to *tomorrow* (both 1). Whoever fires the
     * alert has to look up the right day's marked prayers, or it will
     * announce a Fajr the user already prayed — or stay silent about one they
     * have not.
     */
    val fromDayOffset: Int = 0,
) {
    /** Whole days past the base day that [at] falls on. */
    val dayOffset: Int get() = at / 1440

    /** Minutes from midnight of the day the alert actually lands on. */
    val minuteOfDay: Int get() = at % 1440

    /**
     * A stable, collision-free id for this alert, used as the PendingIntent
     * request code so re-arming replaces an alarm instead of stacking a
     * duplicate on top of it.
     */
    val requestCode: Int
        get() = 1000 + Tracker.FARD.indexOf(prayer) * 2 + if (kind == AlertKind.QAZA_WARNING) 1 else 0
}

object Alerts {

    /** How long before the window closes the qaza warning fires. */
    const val DEFAULT_WARN_MINUTES = 15

    /**
     * Every alert a single day generates, sorted by time.
     *
     * A qaza warning is dropped when it would land before the prayer has even
     * started — that only happens for a pathologically short window (high
     * latitude, or a hand-edited method), but firing "this is about to become
     * qaza" before the azan would be nonsense.
     */
    fun forDay(t: DayTimes, warnMinutes: Int = DEFAULT_WARN_MINUTES): List<Alert> {
        val out = mutableListOf<Alert>()
        for ((name, start, end) in t.list()) {
            out += Alert(AlertKind.PRAYER_START, name, start.minutes)

            // Isha's end is tomorrow's Subh Sadiq, so on the clock it reads
            // *earlier* than its start. Unwrap it before subtracting, or the
            // warning lands around 4 AM the same morning — 19 hours early.
            val endAbsolute =
                if (end.minutes <= start.minutes) end.minutes + 1440 else end.minutes
            val warnAt = endAbsolute - warnMinutes
            if (warnAt > start.minutes) {
                out += Alert(AlertKind.QAZA_WARNING, name, warnAt)
            }
        }
        return out.sortedBy { it.at }
    }

    /**
     * The alerts to actually arm right now: everything still ahead, taking the
     * first occurrence of each prayer-and-kind.
     *
     * **Yesterday is in the list for a reason.** Arm the alarms at half past
     * midnight and the Isha still running belongs to *yesterday* — its qaza
     * warning is three hours away and is the most useful alert on the list.
     * Looking only at today and tomorrow puts that warning twenty-four hours
     * late, so the one alert the user actually needed never arrives.
     * Yesterday's other alerts are all in the past and drop out on their own.
     *
     * Keeping one alert per [Alert.requestCode] is deliberate. Ten alarms
     * cover a full day and a bit, each with its own slot, so nothing
     * overwrites anything else. The app re-arms on launch, on boot and every
     * time an alarm fires, so the chain refills long before it runs out.
     *
     * @param nowMinutes minutes from midnight right now
     */
    fun toArm(
        yesterday: DayTimes,
        today: DayTimes,
        tomorrow: DayTimes,
        nowMinutes: Int,
        warnMinutes: Int = DEFAULT_WARN_MINUTES,
    ): List<Alert> {
        val yesterdays = forDay(yesterday, warnMinutes)
            .map { it.copy(at = it.at - 1440, fromDayOffset = -1) }
        val todays = forDay(today, warnMinutes)
        val tomorrows = forDay(tomorrow, warnMinutes)
            .map { it.copy(at = it.at + 1440, fromDayOffset = 1) }

        val seen = mutableSetOf<Int>()
        return (yesterdays + todays + tomorrows)
            .filter { it.at > nowMinutes }
            .sortedBy { it.at }
            .filter { seen.add(it.requestCode) }
    }

    /**
     * Should this alert still be shown when its alarm goes off?
     *
     * An alarm set hours ago can arrive after the user has already prayed and
     * marked it, or — if the phone was asleep or in Doze — after the window
     * has closed entirely. Both cases should stay silent rather than nag.
     *
     * @param nowMinutes minutes from midnight on the day the alert landed
     * @param done the prayers marked for the day the *prayer* belongs to
     */
    fun stillRelevant(
        alert: Alert,
        nowMinutes: Int,
        done: Set<String>,
        graceMinutes: Int = 5,
    ): Boolean {
        if (done.contains(alert.prayer)) return false
        val due = alert.minuteOfDay
        // Late by more than the grace period: the moment has passed, say nothing.
        val late = ((nowMinutes - due) + 1440) % 1440
        return late <= graceMinutes || late >= 1440 - graceMinutes
    }
}

/**
 * Current and best streak together, because the app shows them side by side
 * and the "is the current one also the best one?" question should be answered
 * in one place rather than in the UI.
 */
data class StreakSummary(
    val current: Int,
    val best: Int,
    /** True when the run going on right now is the longest there has been. */
    val currentIsBest: Boolean,
)

/** Current and best streak in a single pass over the history. */
fun streakSummary(
    year: Int,
    month: Int,
    day: Int,
    days: Int = 400,
    isComplete: (Int, Int, Int) -> Boolean,
): StreakSummary {
    val current = Tracker.currentStreak(year, month, day, isComplete)
    // A streak longer than the look-back window would otherwise report a
    // "best" shorter than the current one, which reads as a bug.
    val best = maxOf(Tracker.bestStreak(year, month, day, days, isComplete), current)
    return StreakSummary(
        current = current,
        best = best,
        currentIsBest = current > 0 && current >= best,
    )
}
