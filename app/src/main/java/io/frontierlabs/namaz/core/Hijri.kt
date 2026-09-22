package io.frontierlabs.namaz.core

import kotlin.math.ceil

/**
 * The Islamic calendar.
 *
 * This is the **tabular** Islamic calendar: an arithmetical one that follows a
 * fixed 30-year cycle rather than the sighting of the moon. It is what every
 * offline app uses, because it is the only version that can be computed at all
 * — a sighted calendar is, by definition, not known in advance.
 *
 * **It will sometimes disagree with the date announced in Pakistan, usually by
 * a day, occasionally by two.** The Ruet-e-Hilal Committee declares the month
 * on the evening the crescent is actually seen, and weather and geography move
 * that around. This is not a bug to be fixed; it is the difference between a
 * calculation and an observation. So the app carries an adjustment of a day or
 * two either way, and the honest instruction is to follow the announcement and
 * nudge the setting to match.
 *
 * The conversion itself is the standard Kuwaiti algorithm, going through the
 * Julian Day Number. It round-trips exactly for every date from 1900 to 2100,
 * which is the property the tests check rather than any single lookup.
 */

data class HijriDate(val year: Int, val month: Int, val day: Int) {
    /** Sortable, and cheap to compare. */
    val ordinal: Int get() = year * 10000 + month * 100 + day
}

/** The occasions worth putting on a screen, with their fixed Hijri date. */
enum class IslamicEvent(val month: Int, val day: Int) {
    NEW_YEAR(1, 1),
    ASHURA(1, 10),
    MILAD(3, 12),
    MIRAJ(7, 27),
    BARAT(8, 15),
    RAMADAN_BEGINS(9, 1),
    QADR(9, 27),
    EID_FITR(10, 1),
    ARAFAH(12, 9),
    EID_ADHA(12, 10),
}

object Hijri {

    /**
     * 1 Muharram 1 AH in the civil variant of the tabular calendar, as a
     * Julian Day Number. The astronomical variant sits one day earlier; the
     * civil one is what almost every implementation and every published
     * conversion table uses.
     */
    private const val EPOCH = 1948440L

    /** How far the displayed date may be nudged, in days, either way. */
    const val MAX_ADJUST = 2

    // --- Julian Day Number, the common ground between the two calendars ----

    fun gregorianToJdn(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
    }

    fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = 100 * b + d - 4800 + m / 10
        return Triple(year.toInt(), month.toInt(), day.toInt())
    }

    /** Day of the week for a Julian Day Number, 0 = Sunday. */
    fun weekdayIndex(jdn: Long): Int = ((jdn + 1) % 7).toInt()

    // --- the conversion ----------------------------------------------------

    /**
     * @param adjust days to shift the result by, to match a local moon
     *   sighting. Positive moves the Hijri date forward.
     */
    fun fromGregorian(year: Int, month: Int, day: Int, adjust: Int = 0): HijriDate =
        fromJdn(gregorianToJdn(year, month, day) + adjust)

    fun fromJdn(jdn: Long): HijriDate {
        var l = jdn - EPOCH + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) +
            (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) -
            (j / 16) * ((15238 * j) / 43) + 29
        val month = (24 * l) / 709
        val day = l - (709 * month) / 24
        val year = 30 * n + j - 30
        return HijriDate(year.toInt(), month.toInt(), day.toInt())
    }

    fun toJdn(year: Int, month: Int, day: Int): Long =
        day + ceil(29.5 * (month - 1)).toLong() + (year - 1) * 354L +
            (3 + 11L * year) / 30 + EPOCH - 1

    /**
     * The Gregorian date a Hijri date falls on.
     *
     * [adjust] is subtracted here, the mirror of [fromGregorian], so that
     * converting one way and back returns where you started whatever the
     * adjustment is set to.
     */
    fun toGregorian(year: Int, month: Int, day: Int, adjust: Int = 0): Triple<Int, Int, Int> =
        jdnToGregorian(toJdn(year, month, day) - adjust)

    // --- shape of the year -------------------------------------------------

    /**
     * Leap years in the 30-year cycle, the variant used by the Kuwaiti
     * algorithm. A leap year gives Dhul Hijjah a thirtieth day.
     */
    fun isLeapYear(year: Int): Boolean = (11 * year + 14) % 30 < 11

    fun monthLength(year: Int, month: Int): Int = when {
        month % 2 == 1 -> 30                       // odd months have 30
        month == 12 && isLeapYear(year) -> 30      // and Dhul Hijjah in a leap year
        else -> 29
    }

    fun yearLength(year: Int): Int = if (isLeapYear(year)) 355 else 354

    // --- occasions ---------------------------------------------------------

    data class Upcoming(
        val event: IslamicEvent,
        val hijri: HijriDate,
        /** Gregorian year, month, day. */
        val gregorian: Triple<Int, Int, Int>,
        /** Whole days from the reference date. 0 means today. */
        val daysAway: Int,
    )

    /**
     * The next [count] occasions from a Gregorian date, soonest first.
     *
     * Both this Hijri year and the next are considered, because in Dhul Hijjah
     * everything left is in the following year and a list that stopped at the
     * year end would come up empty exactly when it is most wanted.
     */
    fun upcoming(
        year: Int,
        month: Int,
        day: Int,
        count: Int = 5,
        adjust: Int = 0,
    ): List<Upcoming> {
        val todayJdn = gregorianToJdn(year, month, day)
        val here = fromJdn(todayJdn + adjust)

        val found = mutableListOf<Upcoming>()
        for (hy in here.year..(here.year + 1)) {
            for (e in IslamicEvent.entries) {
                val jdn = toJdn(hy, e.month, e.day) - adjust
                val away = (jdn - todayJdn).toInt()
                if (away >= 0) {
                    found += Upcoming(
                        event = e,
                        hijri = HijriDate(hy, e.month, e.day),
                        gregorian = jdnToGregorian(jdn),
                        daysAway = away,
                    )
                }
            }
        }
        return found.sortedBy { it.daysAway }.take(count)
    }
}
