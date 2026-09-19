package io.frontierlabs.namaz.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer time calculation. Pure Kotlin -- no network, no Android, no stored
 * timetable. Times are computed from the sun's position for a given date and
 * location, so the app works offline, forever, for any date.
 *
 * The method is the standard one: solar declination and the equation of time
 * give solar noon, and each prayer is an hour angle away from it at a defined
 * sun altitude.
 *
 * IMPORTANT for correctness, not just style:
 *   - Pakistan's standard is University of Islamic Sciences, Karachi (18°/18°).
 *   - Asr in Pakistan is normally Hanafi (shadow factor 2), which differs from
 *     the Shafi'i factor of 1 by roughly 30-60 minutes. Getting this wrong is
 *     the single most common error in prayer apps.
 */

// --- angle helpers, degrees in and out -------------------------------------

private const val DEG = Math.PI / 180.0
private fun dSin(d: Double) = sin(d * DEG)
private fun dCos(d: Double) = cos(d * DEG)
private fun dTan(d: Double) = tan(d * DEG)
private fun dArcSin(x: Double) = asin(x) / DEG
private fun dArcCos(x: Double) = acos(x) / DEG
private fun dArcTan(x: Double) = atan(x) / DEG
private fun dArcTan2(y: Double, x: Double) = atan2(y, x) / DEG

private fun fixAngle(a: Double): Double {
    var x = a - 360.0 * floor(a / 360.0)
    if (x < 0) x += 360.0
    return x
}

private fun fixHour(a: Double): Double {
    var x = a - 24.0 * floor(a / 24.0)
    if (x < 0) x += 24.0
    return x
}

// --- configuration ----------------------------------------------------------

/**
 * Asr shadow factor. Hanafi = 2, everyone else = 1.
 * Most of Pakistan follows Hanafi, so that is the default.
 */
enum class AsrMethod(val label: String, val factor: Double) {
    HANAFI("Hanafi", 2.0),
    STANDARD("Shafi'i / Hanbali / Maliki", 1.0),
}

/**
 * Fajr and Isha are defined by how far the sun is below the horizon. Different
 * authorities use different angles, which is why two apps can disagree by
 * 10-20 minutes and both be "right".
 */
enum class CalcMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    /** Some methods use a fixed interval after Maghrib instead of an angle. */
    val ishaMinutes: Int? = null,
) {
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("Islamic Society of North America", 15.0, 15.0),
    EGYPT("Egyptian General Authority of Survey", 19.5, 17.5),
    MAKKAH("Umm al-Qura, Makkah", 18.5, 0.0, ishaMinutes = 90),
    TEHRAN("Institute of Geophysics, Tehran", 17.7, 14.0),
}

data class Settings(
    val method: CalcMethod = CalcMethod.KARACHI,
    val asr: AsrMethod = AsrMethod.HANAFI,
    /** Minutes of caution subtracted from Sehri end. 0 = exact Subh Sadiq. */
    val sehriPrecautionMinutes: Int = 3,
    /** Minutes added to sunset before declaring Iftar. 0 = exact sunset. */
    val iftarPrecautionMinutes: Int = 2,
)

/** A time of day held as minutes from local midnight, so it can be compared. */
@JvmInline
value class Clock(val minutes: Int) {
    val hour: Int get() = (minutes / 60) % 24
    val minute: Int get() = minutes % 60

    /** "5:12 AM" */
    fun format12(): String {
        val h24 = hour
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        val suffix = if (h24 < 12) "AM" else "PM"
        return "$h:${minute.toString().padStart(2, '0')} $suffix"
    }

    fun format24(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    operator fun plus(mins: Int) = Clock(((minutes + mins) % 1440 + 1440) % 1440)
    operator fun minus(mins: Int) = plus(-mins)
}

/**
 * One day's times.
 *
 * Each prayer has a start and an end. The end matters: praying after it means
 * the prayer is qaza (missed and owed). Most apps only show starts, which is
 * exactly the information you need to *avoid* a qaza.
 */
data class DayTimes(
    val fajr: Clock,
    val sunrise: Clock,
    val zuhr: Clock,
    val asr: Clock,
    val maghrib: Clock,
    val isha: Clock,
    val nextFajr: Clock,
    val settings: Settings,
) {
    /** Fajr's window closes at sunrise. */
    val fajrEnd get() = sunrise

    /** Zuhr runs until Asr begins. */
    val zuhrEnd get() = asr

    /** Asr runs until the sun sets. */
    val asrEnd get() = maghrib

    /** Maghrib runs until Isha begins. */
    val maghribEnd get() = isha

    /** Isha runs until Subh Sadiq, i.e. the next day's Fajr. */
    val ishaEnd get() = nextFajr

    /** Iftar is sunset, with a small caution margin. */
    val iftar get() = maghrib + settings.iftarPrecautionMinutes

    /** Sehri must stop by Subh Sadiq; the margin is caution, not doctrine. */
    val sehriEnd get() = fajr - settings.sehriPrecautionMinutes

    /**
     * Forbidden times, when prayer should not be offered: sunrise, solar noon
     * and sunset. Approximate windows, deliberately generous.
     */
    val makruhAfterSunrise get() = sunrise to (sunrise + 20)
    val makruhBeforeZuhr get() = (zuhr - 10) to zuhr
    val makruhBeforeMaghrib get() = (maghrib - 15) to maghrib

    /** Length of the night, Maghrib to the next Subh Sadiq, in minutes. */
    val nightLength: Int
        get() = ((nextFajr.minutes + 1440) - maghrib.minutes) % 1440

    /** Islamic midnight: the midpoint of the night, not 12:00 AM. */
    val islamicMidnight: Clock get() = maghrib + (nightLength / 2)

    /** Tahajjud is best in the last third of the night. */
    val lastThirdOfNight: Clock get() = maghrib + (nightLength * 2 / 3)

    /** Tahajjud window: the last third of the night, ending at Subh Sadiq. */
    val tahajjudStart get() = lastThirdOfNight
    val tahajjudEnd get() = fajr

    /**
     * Sehri may be eaten through the night; the marker that matters is when it
     * must stop. Islamic midnight is used as the practical start, since that
     * is when the final portion of the night begins.
     */
    val sehriStart get() = islamicMidnight

    /**
     * Ishraq: prayed once the sun has fully risen, conventionally about 20
     * minutes after sunrise, since prayer is not offered during the sunrise
     * itself.
     */
    val ishraqStart get() = sunrise + 20
    val ishraqEnd get() = sunrise + 45

    /** Chasht (Duha): from the end of Ishraq until shortly before Zawal. */
    val chashtStart get() = sunrise + 45
    val chashtEnd get() = zuhr - 15

    /** Zawal: the sun at its zenith, when prayer is not offered. */
    val zawal get() = zuhr - 1

    fun list(): List<Triple<String, Clock, Clock>> = listOf(
        Triple("Fajr", fajr, fajrEnd),
        Triple("Zuhr", zuhr, zuhrEnd),
        Triple("Asr", asr, asrEnd),
        Triple("Maghrib", maghrib, maghribEnd),
        Triple("Isha", isha, ishaEnd),
    )

    /**
     * Everything for one day, in the order it happens through the night and
     * day. `end` is null where only a single moment applies.
     */
    fun fullDay(): List<Row> = listOf(
        Row("Sehri starts", sehriStart, null, "Islamic midnight"),
        Row("Tahajjud", tahajjudStart, tahajjudEnd, "Last third of the night"),
        Row("Sehri ends", sehriEnd, null, "Stop eating"),
        Row("Fajr", fajr, fajrEnd, "Qaza after sunrise"),
        Row("Sunrise", sunrise, null, null),
        Row("Avoid prayer", makruhAfterSunrise.first, makruhAfterSunrise.second,
            "Sun still rising"),
        Row("Ishraq", ishraqStart, ishraqEnd, null),
        Row("Chasht (Duha)", chashtStart, chashtEnd, null),
        Row("Avoid prayer", makruhBeforeZuhr.first, makruhBeforeZuhr.second, "Zawal"),
        Row("Zuhr", zuhr, zuhrEnd, "Qaza after Asr"),
        Row("Asr", asr, asrEnd, "Qaza after sunset"),
        Row("Avoid prayer", makruhBeforeMaghrib.first, makruhBeforeMaghrib.second,
            "Sun setting"),
        Row("Iftar", iftar, null, "Open the fast"),
        Row("Maghrib", maghrib, maghribEnd, "Qaza after Isha"),
        Row("Isha", isha, ishaEnd, "Qaza after Subh Sadiq"),
    )

    data class Row(
        val label: String,
        val start: Clock,
        val end: Clock?,
        val note: String?,
    )
}

object PrayerTimes {

    /** Julian day for a civil date at 00:00 UT. */
    fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Sun's declination (degrees) and equation of time (hours) for a Julian day. */
    fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)      // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)      // mean longitude
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g)) // ecliptic longitude
        val e = 23.439 - 0.00000036 * d                 // obliquity of the ecliptic

        val declination = dArcSin(dSin(e) * dSin(l))
        val rightAscension = fixHour(dArcTan2(dCos(e) * dSin(l), dCos(l)) / 15.0)
        val equationOfTime = q / 15.0 - rightAscension
        return declination to equationOfTime
    }

    /**
     * Hour angle, in hours, between solar noon and the moment the sun sits at
     * [altitude] degrees (negative = below the horizon).
     *
     * Returns null in polar conditions where the sun never reaches that
     * altitude -- irrelevant for Pakistan, but the caller must not crash.
     */
    fun hourAngle(altitude: Double, latitude: Double, declination: Double): Double? {
        // cos H = (sin alt − sin decl · sin lat) / (cos decl · cos lat)
        //
        // [altitude] is signed: negative below the horizon. An earlier version
        // negated sin(altitude) as well, which flipped Fajr and Isha and put
        // Asr after sunset. The equinox day-length test is what catches this.
        val numerator = dSin(altitude) - dSin(declination) * dSin(latitude)
        val denominator = dCos(declination) * dCos(latitude)
        if (denominator == 0.0) return null
        val cosH = numerator / denominator
        if (cosH > 1.0 || cosH < -1.0) return null
        return dArcCos(cosH) / 15.0
    }

    /** Hour angle for Asr, where the shadow equals [factor] times the object. */
    fun asrHourAngle(factor: Double, latitude: Double, declination: Double): Double? {
        val altitude = dArcTan(1.0 / (factor + dTan(abs(latitude - declination))))
        return hourAngle(altitude, latitude, declination)
    }

    /** Sunrise/sunset altitude allowing for refraction and the sun's radius. */
    private const val HORIZON = -0.833

    private fun toClock(hours: Double): Clock {
        val m = Math.round(fixHour(hours) * 60.0).toInt()
        return Clock(m % 1440)
    }

    /**
     * Compute one day's times.
     *
     * @param timezone hours offset from UTC. Pakistan is +5 all year (no DST).
     */
    fun forDate(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timezone: Double = 5.0,
        settings: Settings = Settings(),
    ): DayTimes {
        fun times(y: Int, mo: Int, d: Int): Triple<Double, Double, Double> {
            val jd = julianDay(y, mo, d)
            val (decl, eqt) = sunPosition(jd)
            val noon = 12.0 + timezone - longitude / 15.0 - eqt
            return Triple(noon, decl, eqt)
        }

        val (noon, decl, _) = times(year, month, day)
        val m = settings.method

        val fajrH = hourAngle(-m.fajrAngle, latitude, decl)
        val riseH = hourAngle(HORIZON, latitude, decl)
        val asrH = asrHourAngle(settings.asr.factor, latitude, decl)

        val sunriseT = riseH?.let { noon - it } ?: (noon - 6.0)
        val sunsetT = riseH?.let { noon + it } ?: (noon + 6.0)
        val fajrT = fajrH?.let { noon - it } ?: (sunriseT - 1.5)
        val asrT = asrH?.let { noon + it } ?: (noon + 3.5)

        val ishaT = if (m.ishaMinutes != null) {
            sunsetT + m.ishaMinutes / 60.0
        } else {
            hourAngle(-m.ishaAngle, latitude, decl)?.let { noon + it } ?: (sunsetT + 1.5)
        }

        // Next day's Fajr closes Isha's window, so it is computed properly
        // rather than reusing today's -- near the solstices the two differ by
        // enough to matter.
        val nextDay = addDays(year, month, day, 1)
        val (noon2, decl2, _) = times(nextDay.first, nextDay.second, nextDay.third)
        val nextFajrT = hourAngle(-m.fajrAngle, latitude, decl2)?.let { noon2 - it }
            ?: (fajrT)

        return DayTimes(
            fajr = toClock(fajrT),
            sunrise = toClock(sunriseT),
            // A minute is added to solar noon: at exact noon the sun is at its
            // zenith, when prayer is not offered.
            zuhr = toClock(noon) + 1,
            asr = toClock(asrT),
            maghrib = toClock(sunsetT),
            isha = toClock(ishaT),
            nextFajr = toClock(nextFajrT),
            settings = settings,
        )
    }

    fun forDate(
        year: Int, month: Int, day: Int, city: City, settings: Settings = Settings(),
    ): DayTimes = forDate(year, month, day, city.lat, city.lon, city.timezone, settings)

    /** Every day of a month, for the timetable view. */
    fun forMonth(
        year: Int, month: Int, city: City, settings: Settings = Settings(),
    ): List<Pair<Int, DayTimes>> =
        (1..daysInMonth(year, month)).map { d ->
            d to forDate(year, month, d, city, settings)
        }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun addDays(year: Int, month: Int, day: Int, n: Int): Triple<Int, Int, Int> {
        var y = year
        var mo = month
        var d = day + n
        while (d > daysInMonth(y, mo)) {
            d -= daysInMonth(y, mo)
            mo++
            if (mo > 12) { mo = 1; y++ }
        }
        while (d < 1) {
            mo--
            if (mo < 1) { mo = 12; y-- }
            d += daysInMonth(y, mo)
        }
        return Triple(y, mo, d)
    }

    /**
     * Which prayer is current, and how long until the next one.
     * Returns the label, the next prayer's label, and minutes remaining.
     */
    fun nextPrayer(now: Clock, t: DayTimes): Triple<String, String, Int> {
        val marks = listOf(
            "Fajr" to t.fajr,
            "Sunrise" to t.sunrise,
            "Zuhr" to t.zuhr,
            "Asr" to t.asr,
            "Maghrib" to t.maghrib,
            "Isha" to t.isha,
        )
        for (i in marks.indices) {
            if (now.minutes < marks[i].second.minutes) {
                val current = if (i == 0) "Isha" else marks[i - 1].first
                return Triple(current, marks[i].first,
                    marks[i].second.minutes - now.minutes)
            }
        }
        // After Isha: next is tomorrow's Fajr.
        val mins = (1440 - now.minutes) + t.nextFajr.minutes
        return Triple("Isha", "Fajr", mins)
    }
}
