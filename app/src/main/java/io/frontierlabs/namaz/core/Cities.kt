package io.frontierlabs.namaz.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pakistani cities with coordinates.
 *
 * Longitude is what shifts the clock: 1° of longitude is 4 minutes of solar
 * time, so Peshawar's Maghrib is about 11 minutes later than Lahore's. Using
 * a nearby big city instead of your own town is the main source of error in
 * practice, which is why the list is long rather than tidy.
 *
 * Pakistan is UTC+5 year round and does not observe daylight saving.
 */
data class City(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double,
    val timezone: Double = 5.0,
)

object Cities {

    val all: List<City> = listOf(
        // Khyber Pakhtunkhwa
        City("Peshawar", "KP", 34.0151, 71.5249),
        City("Mardan", "KP", 34.1989, 72.0231),
        City("Mingora (Swat)", "KP", 34.7717, 72.3600),
        City("Kohat", "KP", 33.5869, 71.4414),
        City("Abbottabad", "KP", 34.1688, 73.2215),
        City("Mansehra", "KP", 34.3300, 73.1968),
        City("Haripur", "KP", 33.9942, 72.9333),
        City("Nowshera", "KP", 34.0153, 71.9747),
        City("Charsadda", "KP", 34.1453, 71.7308),
        City("Swabi", "KP", 34.1201, 72.4700),
        City("Bannu", "KP", 32.9889, 70.6056),
        City("Dera Ismail Khan", "KP", 31.8313, 70.9019),
        City("Hangu", "KP", 33.5326, 71.0555),
        City("Timergara", "KP", 34.8281, 71.8417),
        City("Chitral", "KP", 35.8518, 71.7864),
        City("Batkhela", "KP", 34.6167, 72.0000),

        // Punjab
        City("Lahore", "Punjab", 31.5204, 74.3587),
        City("Rawalpindi", "Punjab", 33.5651, 73.0169),
        City("Faisalabad", "Punjab", 31.4504, 73.1350),
        City("Multan", "Punjab", 30.1575, 71.5249),
        City("Gujranwala", "Punjab", 32.1877, 74.1945),
        City("Sialkot", "Punjab", 32.4945, 74.5229),
        City("Sargodha", "Punjab", 32.0836, 72.6711),
        City("Bahawalpur", "Punjab", 29.3956, 71.6836),
        City("Sahiwal", "Punjab", 30.6682, 73.1114),
        City("Sheikhupura", "Punjab", 31.7131, 73.9783),
        City("Jhang", "Punjab", 31.2781, 72.3317),
        City("Rahim Yar Khan", "Punjab", 28.4202, 70.2952),
        City("Kasur", "Punjab", 31.1187, 74.4500),
        City("Okara", "Punjab", 30.8100, 73.4597),
        City("Jhelum", "Punjab", 32.9425, 73.7257),
        City("Chakwal", "Punjab", 32.9328, 72.8630),
        City("Attock", "Punjab", 33.7660, 72.3600),
        City("Khanewal", "Punjab", 30.3017, 71.9321),
        City("Vehari", "Punjab", 30.0333, 72.3500),
        City("Dera Ghazi Khan", "Punjab", 30.0489, 70.6455),
        City("Gujrat", "Punjab", 32.5731, 74.0789),
        City("Mianwali", "Punjab", 32.5839, 71.5370),
        City("Bhakkar", "Punjab", 31.6082, 71.0854),

        // Islamabad Capital Territory
        City("Islamabad", "ICT", 33.6844, 73.0479),

        // Sindh
        City("Karachi", "Sindh", 24.8607, 67.0011),
        City("Hyderabad", "Sindh", 25.3960, 68.3578),
        City("Sukkur", "Sindh", 27.7052, 68.8574),
        City("Larkana", "Sindh", 27.5590, 68.2120),
        City("Nawabshah", "Sindh", 26.2442, 68.4100),
        City("Mirpur Khas", "Sindh", 25.5276, 69.0122),
        City("Dadu", "Sindh", 26.7319, 67.7767),
        City("Thatta", "Sindh", 24.7461, 67.9236),
        City("Shikarpur", "Sindh", 27.9556, 68.6382),
        City("Jacobabad", "Sindh", 28.2769, 68.4514),

        // Balochistan
        City("Quetta", "Balochistan", 30.1798, 66.9750),
        City("Gwadar", "Balochistan", 25.1264, 62.3225),
        City("Turbat", "Balochistan", 26.0031, 63.0544),
        City("Khuzdar", "Balochistan", 27.8120, 66.6100),
        City("Zhob", "Balochistan", 31.3417, 69.4486),
        City("Chaman", "Balochistan", 30.9179, 66.4459),
        City("Sibi", "Balochistan", 29.5430, 67.8773),
        City("Loralai", "Balochistan", 30.3705, 68.5972),

        // Azad Jammu & Kashmir, Gilgit-Baltistan
        City("Muzaffarabad", "AJK", 34.3700, 73.4711),
        City("Mirpur", "AJK", 33.1478, 73.7519),
        City("Rawalakot", "AJK", 33.8578, 73.7604),
        City("Gilgit", "GB", 35.9208, 74.3080),
        City("Skardu", "GB", 35.2971, 75.6333),
        City("Hunza", "GB", 36.3167, 74.6500),
    )

    val default: City = all.first { it.name == "Islamabad" }

    fun byName(name: String): City? = all.firstOrNull { it.name == name }

    fun search(query: String): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) || it.province.lowercase().contains(q)
        }
    }

    val provinces: List<String> = all.map { it.province }.distinct()

    /**
     * Straight-line distance in kilometres between a point and a city.
     *
     * Great-circle rather than flat, because Pakistan spans about 1,400 km and
     * treating latitude and longitude as a flat grid would be out by tens of
     * kilometres at the corners -- enough to pick the wrong city.
     */
    fun distanceKm(lat: Double, lon: Double, city: City): Double {
        val rad = Math.PI / 180.0
        val phi1 = lat * rad
        val phi2 = city.lat * rad
        val dPhi = (city.lat - lat) * rad
        val dLon = (city.lon - lon) * rad
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * 6371.0 * asin(minOf(1.0, sqrt(a)))
    }

    /**
     * The closest city in the list to a set of coordinates, and how far away
     * it is.
     *
     * The distance comes back with it on purpose. A phone that reports a
     * location in Dubai would otherwise be silently told it is in Gwadar, and
     * every prayer time would be wrong with nothing on screen to explain it.
     * The caller is expected to check the distance and fall back to asking.
     */
    fun nearest(lat: Double, lon: Double): Pair<City, Double> {
        var best = all.first()
        var bestKm = Double.MAX_VALUE
        for (c in all) {
            val km = distanceKm(lat, lon, c)
            if (km < bestKm) { bestKm = km; best = c }
        }
        return best to bestKm
    }

    /**
     * How far a detected location may be from the nearest listed city before
     * the app stops trusting it.
     *
     * Roughly the gap between two neighbouring cities in the thinnest part of
     * Balochistan, so anywhere inside Pakistan resolves, while somewhere
     * genuinely abroad does not.
     */
    const val MAX_TRUSTED_KM = 200.0
}
