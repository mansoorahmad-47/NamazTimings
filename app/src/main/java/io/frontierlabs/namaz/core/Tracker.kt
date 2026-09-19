package io.frontierlabs.namaz.core

/**
 * Prayer tracking and streaks. Pure Kotlin, no storage and no Android, so the
 * rules can be tested exactly.
 */

enum class PrayerStatus {
    /** Its time has not arrived yet. */
    UPCOMING,

    /** Its window is open right now and it has not been marked. */
    DUE,

    /** Marked as prayed. */
    DONE,

    /** The window closed without it being marked. It is now qaza. */
    MISSED,
}

/**
 * Where "now" sits inside the prayer day.
 *
 * @param dayOffset 0 when the prayer day is today's date, -1 when it is
 *   yesterday's — which is the case after midnight but before Fajr.
 * @param dayMinutes minutes since the prayer day began. Runs past 1440 rather
 *   than resetting at midnight, so 00:30 on a prayer day that started
 *   yesterday reads as 1470.
 */
data class PrayerDay(val dayOffset: Int, val dayMinutes: Int)

object Tracker {

    /** The five fard prayers, in order. */
    val FARD = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")

    /**
     * Which day's prayers "now" belongs to, and how far into that day it is.
     *
     * **The day does not turn over at midnight.** Isha runs until Subh Sadiq,
     * so at one in the morning the prayer still being offered is the previous
     * day's, and the five prayers on the screen should still be that day's.
     * Rolling over at 00:00 makes the app announce a fresh, untouched day
     * while the user is in the middle of finishing the old one: it wipes the
     * visible progress, offers tomorrow's Fajr for marking, and records a day
     * with four prayers offered as incomplete.
     *
     * So the boundary is Fajr, not midnight. Before today's Fajr the prayer
     * day is yesterday and [PrayerDay.dayMinutes] keeps counting past 1440;
     * from Fajr onward it is today.
     *
     * @param nowMinutes wall-clock minutes from midnight
     * @param todayFajrMinutes today's Fajr, from today's calendar date
     */
    fun prayerDay(nowMinutes: Int, todayFajrMinutes: Int): PrayerDay =
        if (nowMinutes < todayFajrMinutes) PrayerDay(-1, nowMinutes + 1440)
        else PrayerDay(0, nowMinutes)

    /**
     * A prayer's end on the same scale as [PrayerDay.dayMinutes].
     *
     * Isha's end reads as earlier on the clock than its start because it
     * belongs to the next morning. Unwrapping it once, here, is what lets
     * every comparison below be a plain `<` instead of a special case.
     */
    fun endInDay(start: Clock, end: Clock): Int =
        if (end.minutes <= start.minutes) end.minutes + 1440 else end.minutes

    /**
     * The prayer whose window is open, with its start and end.
     *
     * Null between sunrise and Zuhr, when no fard prayer is due — the front
     * page says so rather than inventing a "current" prayer.
     */
    fun currentPrayer(dayMinutes: Int, times: DayTimes): Triple<String, Clock, Clock>? =
        times.list().firstOrNull { (_, start, end) ->
            dayMinutes >= start.minutes && dayMinutes < endInDay(start, end)
        }

    /** Minutes from [now] until [end] on the wall clock, counting across midnight. */
    fun minutesUntil(now: Clock, end: Clock): Int =
        ((end.minutes - now.minutes) + 1440) % 1440

    /** Minutes left before the window closes, measured inside the prayer day. */
    fun minutesLeft(dayMinutes: Int, start: Clock, end: Clock): Int =
        (endInDay(start, end) - dayMinutes).coerceAtLeast(0)

    /**
     * Has this prayer's time arrived?
     *
     * Nobody can offer a prayer before its azan, so the app should not offer
     * to mark one. On any earlier day every prayer has started, and callers
     * say so by passing the day's full length.
     */
    fun hasStarted(dayMinutes: Int, start: Clock): Boolean = dayMinutes >= start.minutes

    /**
     * Status of one prayer.
     *
     * @param dayMinutes minutes since the prayer day began (see [prayerDay])
     * @param done whether the user has marked it prayed
     */
    fun statusOf(dayMinutes: Int, start: Clock, end: Clock, done: Boolean): PrayerStatus = when {
        done -> PrayerStatus.DONE
        dayMinutes < start.minutes -> PrayerStatus.UPCOMING
        dayMinutes < endInDay(start, end) -> PrayerStatus.DUE
        else -> PrayerStatus.MISSED
    }

    /** All five statuses for a day, in order. */
    fun dayStatuses(
        dayMinutes: Int,
        times: DayTimes,
        done: Set<String>,
    ): List<Pair<String, PrayerStatus>> =
        times.list().map { (name, start, end) ->
            name to statusOf(dayMinutes, start, end, done.contains(name))
        }


    /** How many of the five are marked prayed. */
    fun completedCount(done: Set<String>): Int = FARD.count { done.contains(it) }

    /** True when all five are marked. */
    fun isDayComplete(done: Set<String>): Boolean = completedCount(done) == 5

    /**
     * Current streak: consecutive days, counting back from [year]-[month]-[day],
     * on which all five prayers were marked.
     *
     * Today only counts once it is complete, so an unfinished day does not
     * break a streak that is still going. Yesterday and earlier must be
     * complete or the chain ends there.
     */
    fun currentStreak(
        year: Int,
        month: Int,
        day: Int,
        isComplete: (Int, Int, Int) -> Boolean,
    ): Int {
        var y = year
        var m = month
        var d = day
        var count = 0

        if (!isComplete(y, m, d)) {
            val prev = PrayerTimes.addDays(y, m, d, -1)
            y = prev.first; m = prev.second; d = prev.third
        }

        while (isComplete(y, m, d)) {
            count++
            if (count > 3650) break   // a decade is plenty; never spin forever
            val prev = PrayerTimes.addDays(y, m, d, -1)
            y = prev.first; m = prev.second; d = prev.third
        }
        return count
    }

    /** Longest run of complete days within the last [days] days. */
    fun bestStreak(
        year: Int,
        month: Int,
        day: Int,
        days: Int = 365,
        isComplete: (Int, Int, Int) -> Boolean,
    ): Int {
        var best = 0
        var run = 0
        var y = year
        var m = month
        var d = day
        repeat(days) {
            if (isComplete(y, m, d)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            val prev = PrayerTimes.addDays(y, m, d, -1)
            y = prev.first; m = prev.second; d = prev.third
        }
        return best
    }
}
