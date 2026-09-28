package io.frontierlabs.namaz.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Qibla direction. Pure Kotlin, fully testable off-device.
 *
 * The Qibla is the *great-circle* bearing to the Kaaba, not the direction you
 * would follow on a flat map. Over the distance from Pakistan the two differ
 * by several degrees, so the flat-map answer is visibly wrong.
 *
 * The bearing is computed from the city you selected, so the app needs no
 * location permission -- only a compass to show which way you are facing, and
 * on a phone without one, the sun (see [sunAt]).
 */
object Qibla {

    /** The Kaaba, Masjid al-Haram, Makkah. */
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    private const val DEG = Math.PI / 180.0
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Initial great-circle bearing from a point to the Kaaba, in degrees
     * clockwise from true north (0 = north, 90 = east, 180 = south, 270 = west).
     *
     * This is a TRUE bearing. A phone's compass reads MAGNETIC north, so the
     * UI adds the local magnetic declination before drawing the needle --
     * without that correction the arrow is off by about 2 degrees in Pakistan,
     * and far more elsewhere.
     */
    fun bearing(lat: Double, lon: Double): Double {
        val phi1 = lat * DEG
        val phi2 = KAABA_LAT * DEG
        val dLon = (KAABA_LON - lon) * DEG

        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val theta = atan2(y, x) / DEG
        return (theta + 360.0) % 360.0
    }

    fun bearing(city: City): Double = bearing(city.lat, city.lon)

    /** Great-circle distance to the Kaaba in kilometres (haversine). */
    fun distanceKm(lat: Double, lon: Double): Double {
        val phi1 = lat * DEG
        val phi2 = KAABA_LAT * DEG
        val dPhi = (KAABA_LAT - lat) * DEG
        val dLon = (KAABA_LON - lon) * DEG
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(minOf(1.0, sqrt(a)))
    }

    fun distanceKm(city: City): Double = distanceKm(city.lat, city.lon)

    /** "WSW", "NW" and so on, for the compass point nearest the bearing. */
    fun compassPoint(bearing: Double): String {
        val points = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val i = Math.round(((bearing % 360.0) / 22.5)).toInt() % 16
        return points[i]
    }

    /**
     * How far to turn, in degrees, from the direction the phone is facing to
     * the Qibla. Positive means turn right, negative means turn left, and the
     * result is always within ±180 so the app never tells you to turn 350°
     * when 10° the other way would do.
     */
    fun turnFrom(headingTrue: Double, qiblaBearing: Double): Double {
        var diff = (qiblaBearing - headingTrue + 540.0) % 360.0 - 180.0
        if (abs(diff) < 0.0001) diff = 0.0
        return diff
    }

    // --- finding the Qibla without a compass -------------------------------
    //
    // Plenty of cheaper phones have no magnetometer at all. For them the sun
    // is the compass: where it stands at this moment is known exactly from
    // the date, the time and the city, with no sensor and no internet. Face
    // the sun, turn by the difference, and you face the Qibla -- the method
    // people used long before phones.

    /** Where the sun is in the sky: [azimuth] from true north, [altitude] above the horizon. */
    data class Sun(val azimuth: Double, val altitude: Double)

    /**
     * The sun's position at [epochMillis] (UTC) seen from [lat], [lon].
     *
     * Uses the same declination and equation of time as the prayer times, so
     * it is good to well under a degree -- far finer than anyone can face.
     */
    fun sunAt(epochMillis: Long, lat: Double, lon: Double): Sun {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val (decl, eqt) = PrayerTimes.sunPosition(jd)
        val utHours = ((epochMillis % 86_400_000L + 86_400_000L) % 86_400_000L) / 3_600_000.0
        val solarHours = utHours + lon / 15.0 + eqt
        val h = (solarHours - 12.0) * 15.0 * DEG   // hour angle, 0 at solar noon

        val phi = lat * DEG
        val d = decl * DEG
        val altitude = asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)) / DEG
        // Measured from south, westward positive; +180 turns it to from-north.
        val fromSouth = atan2(sin(h), cos(h) * sin(phi) - kotlin.math.tan(d) * cos(phi)) / DEG
        return Sun((fromSouth + 180.0 + 360.0) % 360.0, altitude)
    }

    fun sunAt(epochMillis: Long, city: City): Sun = sunAt(epochMillis, city.lat, city.lon)

    /**
     * Where on the horizon the sun sets on the day containing [epochMillis],
     * in degrees from true north -- about 270 at the equinoxes, further north
     * in summer and further south in winter. After Maghrib the glow in the
     * west still marks the spot, so it is a reference after dark too.
     */
    fun sunsetAzimuth(epochMillis: Long, lat: Double): Double {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val (decl, _) = PrayerTimes.sunPosition(jd)
        val c = (sin(decl * DEG) / cos(lat * DEG)).coerceIn(-1.0, 1.0)
        return 360.0 - kotlin.math.acos(c) / DEG
    }
}
