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
 * location permission -- only a compass to show which way you are facing.
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
}
