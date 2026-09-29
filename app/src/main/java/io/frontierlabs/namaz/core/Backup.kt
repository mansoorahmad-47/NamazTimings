package io.frontierlabs.namaz.core

/**
 * The streak backup: what goes into the file in the user's Google Drive, and
 * how it is merged back. Pure Kotlin, so the merge rules can be tested.
 *
 * Only the marked prayers travel. City, language and settings are one tap to
 * set again; a year of streaks is not.
 *
 * The file is small, flat JSON, one entry per day:
 *
 *     {"app":"NamazTimings","format":1,
 *      "days":{"2026-09-28":"Fajr,Zuhr,Asr,Maghrib,Isha","2026-09-29":"Fajr"}}
 *
 * A day with an empty string is kept on purpose: it is how "I unmarked that"
 * reaches the backup.
 */
object Backup {

    /** Days are "yyyy-mm-dd"; values are the prayers marked that day. */
    fun serialize(days: Map<String, Set<String>>): String = buildString {
        append("{\"app\":\"NamazTimings\",\"format\":1,\"days\":{")
        days.toSortedMap().entries.forEachIndexed { i, (day, done) ->
            if (i > 0) append(',')
            // In the fixed prayer order, so the file reads naturally.
            val names = Tracker.FARD.filter { it in done }.joinToString(",")
            append('"').append(day).append("\":\"").append(names).append('"')
        }
        append("}}")
    }

    /**
     * Read a backup file. Null when it is not one of ours, so a damaged or
     * foreign file can never wipe anything. Unknown prayer names are dropped.
     */
    fun parse(json: String?): Map<String, Set<String>>? {
        if (json.isNullOrBlank() || !json.contains("\"app\":\"NamazTimings\"")) return null
        val out = mutableMapOf<String, Set<String>>()
        Regex("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\"([^\"]*)\"").findAll(json).forEach { m ->
            val names = m.groupValues[2].split(',').map { it.trim() }
                .filter { it in Tracker.FARD }.toSet()
            out[m.groupValues[1]] = names
        }
        return out
    }

    /**
     * Combine the phone's days with the backup's.
     *
     * A day changed on this phone since the last successful backup ([dirty])
     * is taken from the phone -- that is the newest word on it, including an
     * unmarking. Every other day is taken from the backup when it has one,
     * which is what brings a reinstalled phone's history back, and from the
     * phone when it does not, so nothing from before backup was switched on
     * is ever lost.
     */
    fun merge(
        local: Map<String, Set<String>>,
        remote: Map<String, Set<String>>,
        dirty: Set<String>,
    ): Map<String, Set<String>> {
        val out = mutableMapOf<String, Set<String>>()
        for (day in local.keys + remote.keys) {
            out[day] = when {
                day in dirty -> local[day] ?: emptySet()
                day in remote -> remote.getValue(day)
                else -> local.getValue(day)
            }
        }
        return out
    }

    /** How many days in [days] have at least one prayer marked. */
    fun markedDays(days: Map<String, Set<String>>): Int = days.values.count { it.isNotEmpty() }
}
