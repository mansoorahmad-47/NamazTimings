package io.frontierlabs.namaz

import android.content.Context
import android.content.SharedPreferences
import io.frontierlabs.namaz.core.AsrMethod
import io.frontierlabs.namaz.core.CalcMethod
import io.frontierlabs.namaz.core.Cities
import io.frontierlabs.namaz.core.City
import io.frontierlabs.namaz.core.Lang
import io.frontierlabs.namaz.core.Settings

/**
 * One place that knows the stored keys.
 *
 * This exists because the alarm receivers run when no UI is alive — there is
 * no composable to ask, and no ViewModel. They read the same city, the same
 * calculation settings and the same marked-prayer sets the screen does, so
 * those reads have to be defined once. Two copies of `"done:%04d-%02d-%02d"`
 * in two files is exactly the bug that would show up as notifications firing
 * for prayers the user already marked.
 */
object Prefs {

    private const val NAME = "namaz"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- marked prayers ----------------------------------------------------
    // One StringSet per date. SharedPreferences hands back a set that must not
    // be mutated, so every read copies it.

    fun dateKey(y: Int, m: Int, d: Int) = "done:%04d-%02d-%02d".format(y, m, d)

    fun readDone(prefs: SharedPreferences, y: Int, m: Int, d: Int): Set<String> =
        prefs.getStringSet(dateKey(y, m, d), null)?.toSet() ?: emptySet()

    fun writeDone(prefs: SharedPreferences, y: Int, m: Int, d: Int, value: Set<String>) {
        prefs.edit().putStringSet(dateKey(y, m, d), value).apply()
    }

    // --- what the times are calculated from --------------------------------

    fun city(prefs: SharedPreferences): City =
        Cities.byName(prefs.getString("city", null) ?: "") ?: Cities.default

    fun settings(prefs: SharedPreferences): Settings = Settings(
        method = runCatching {
            CalcMethod.valueOf(prefs.getString("method", "KARACHI")!!)
        }.getOrDefault(CalcMethod.KARACHI),
        asr = runCatching {
            AsrMethod.valueOf(prefs.getString("asr", "HANAFI")!!)
        }.getOrDefault(AsrMethod.HANAFI),
    )

    fun lang(prefs: SharedPreferences): Lang =
        runCatching { Lang.valueOf(prefs.getString("lang", "EN")!!) }.getOrDefault(Lang.EN)

    // --- notification switches ---------------------------------------------
    // All default to on. Someone who installs a prayer app and never opens
    // Settings should still be told when Fajr starts.

    fun prayerAlerts(prefs: SharedPreferences) = prefs.getBoolean("notifyPrayer", true)

    fun qazaAlerts(prefs: SharedPreferences) = prefs.getBoolean("notifyQaza", true)

    fun updateAlerts(prefs: SharedPreferences) = prefs.getBoolean("notifyUpdate", true)

    /** How many minutes before the window closes the qaza warning fires. */
    fun warnMinutes(prefs: SharedPreferences) = prefs.getInt("warnMinutes", 15)

    fun setPrayerAlerts(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("notifyPrayer", on).apply()

    fun setQazaAlerts(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("notifyQaza", on).apply()

    fun setUpdateAlerts(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("notifyUpdate", on).apply()

    fun setWarnMinutes(prefs: SharedPreferences, minutes: Int) =
        prefs.edit().putInt("warnMinutes", minutes).apply()

    // --- update nagging ----------------------------------------------------

    /**
     * The newest version we have already put a notification up for.
     *
     * Without this the daily check would post the same "version 1.2 is out"
     * notification every single day until they install it, which is how an
     * app gets its notifications switched off entirely.
     */
    fun lastNotifiedVersion(prefs: SharedPreferences) = prefs.getInt("updateNotified", 0)

    fun setLastNotifiedVersion(prefs: SharedPreferences, code: Int) =
        prefs.edit().putInt("updateNotified", code).apply()
}
