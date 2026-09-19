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

object Tracker {

    /** The five fard prayers, in order. */
    val FARD = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")

    /**
     * Is [now] inside the window [start]..[end]?
     *
     * Isha's window runs past midnight into the next day's Subh Sadiq, so when
     * `end` is earlier on the clock than `start` the window wraps. Treating it
     * as a simple `start <= now < end` marks Isha missed the moment midnight
     * passes, which is wrong and would ruin a streak every single night.
     */
    fun inWindow(now: Clock, start: Clock, end: Clock): Boolean {
        val s = start.minutes
        val e = end.minutes
        val n = now.minutes
        return if (e > s) n in s until e else n >= s || n < e
    }

    /**
     * The prayer whose window is open right now, with its start and end.
     *
     * Null between sunrise and Zuhr, when no fard prayer is due — the front
     * page says so rather than inventing a "current" prayer.
     */
    fun currentPrayer(now: Clock, times: DayTimes): Triple<String, Clock, Clock>? =
        times.list().firstOrNull { (_, start, end) -> inWindow(now, start, end) }

    /** Minutes from [now] until [end], counting across midnight. */
    fun minutesUntil(now: Clock, end: Clock): Int =
        ((end.minutes - now.minutes) + 1440) % 1440

    /**
     * Status of one prayer.
     *
     * @param done whether the user has marked it prayed
     */
    fun statusOf(now: Clock, start: Clock, end: Clock, done: Boolean): PrayerStatus = when {
        done -> PrayerStatus.DONE
        inWindow(now, start, end) -> PrayerStatus.DUE
        // A wrapping window (Isha) is never "missed" during the same day: it
        // only lapses once the next Fajr arrives, which is the next day's row.
        end.minutes <= start.minutes -> PrayerStatus.UPCOMING
        now.minutes >= end.minutes -> PrayerStatus.MISSED
        else -> PrayerStatus.UPCOMING
    }

    /** All five statuses for a day, in order. */
    fun dayStatuses(
        now: Clock,
        times: DayTimes,
        done: Set<String>,
    ): List<Pair<String, PrayerStatus>> =
        times.list().map { (name, start, end) ->
            name to statusOf(now, start, end, done.contains(name))
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
