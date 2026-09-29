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
        val day = "%04d-%02d-%02d".format(y, m, d)
        // Every change is remembered as "not backed up yet", whether or not
        // backup is on: switching it on later then knows which days are the
        // phone's newest word. See core.Backup.merge.
        val dirty = (prefs.getStringSet("backupDirty", null) ?: emptySet()) + day
        prefs.edit()
            .putStringSet(dateKey(y, m, d), value)
            .putStringSet("backupDirty", dirty)
            .apply()
    }

    /** Every day with a record, as "yyyy-mm-dd" to the prayers marked. */
    fun allDone(prefs: SharedPreferences): Map<String, Set<String>> =
        prefs.all.entries
            .filter { it.key.startsWith("done:") }
            .associate { (k, v) ->
                k.removePrefix("done:") to
                    ((v as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet())
            }

    /** Write a merged backup back. Does not mark anything dirty. */
    fun replaceAllDone(prefs: SharedPreferences, days: Map<String, Set<String>>) {
        val e = prefs.edit()
        days.forEach { (day, set) -> e.putStringSet("done:$day", set) }
        e.apply()
    }

    // --- Google Drive backup (see DriveBackup.kt) ----------------------------

    fun backupDirty(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet("backupDirty", null)?.toSet() ?: emptySet()

    /** Forget only the days that were uploaded; a tick made mid-upload stays dirty. */
    fun clearBackupDirty(prefs: SharedPreferences, uploaded: Set<String>) {
        prefs.edit().putStringSet("backupDirty", backupDirty(prefs) - uploaded).apply()
    }

    fun driveBackupOn(prefs: SharedPreferences) = prefs.getBoolean("driveBackup", false)

    fun setDriveBackupOn(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("driveBackup", on).apply()

    /** Epoch millis of the last good backup, 0 for never. */
    fun lastBackup(prefs: SharedPreferences) = prefs.getLong("lastBackup", 0L)

    fun setLastBackup(prefs: SharedPreferences, at: Long) =
        prefs.edit().putLong("lastBackup", at).putBoolean("backupNeedsSignIn", false).apply()

    fun backupAccount(prefs: SharedPreferences): String? = prefs.getString("backupAccount", null)

    fun setBackupAccount(prefs: SharedPreferences, email: String?) =
        prefs.edit().putString("backupAccount", email).apply()

    /**
     * Google wants the user to sign in again (they removed the app's access,
     * or changed their password). A background job cannot ask, so it says so
     * here and Settings offers the button.
     */
    fun backupNeedsSignIn(prefs: SharedPreferences) = prefs.getBoolean("backupNeedsSignIn", false)

    fun setBackupNeedsSignIn(prefs: SharedPreferences, needs: Boolean) =
        prefs.edit().putBoolean("backupNeedsSignIn", needs).apply()

    /**
     * The "keep your streaks safe" card. Snoozed rather than gone for good
     * when someone taps Not now; "No thanks" in the dialog is for good.
     */
    fun backupNudgeHiddenUntil(prefs: SharedPreferences) = prefs.getLong("backupNudgeUntil", 0L)

    fun hideBackupNudge(prefs: SharedPreferences, untilMillis: Long) =
        prefs.edit().putLong("backupNudgeUntil", untilMillis).apply()

    // --- first run ---------------------------------------------------------

    /**
     * Has the city ever been chosen?
     *
     * The `contains("city")` half is what stops the welcome screen appearing
     * for people upgrading from an earlier version. They already picked a
     * city, so being asked again would look like the update had lost it.
     */
    fun setupDone(prefs: SharedPreferences): Boolean =
        prefs.getBoolean("setupDone", false) || prefs.contains("city")

    fun markSetupDone(prefs: SharedPreferences) =
        prefs.edit().putBoolean("setupDone", true).apply()

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

    // --- Islamic calendar ---------------------------------------------------

    /**
     * Days to shift the calculated Islamic date by, to match the local moon
     * sighting. Clamped, because beyond a couple of days it stops being a
     * correction and becomes a different calendar.
     */
    fun hijriAdjust(prefs: SharedPreferences): Int =
        prefs.getInt("hijriAdjust", 0)
            .coerceIn(-io.frontierlabs.namaz.core.Hijri.MAX_ADJUST,
                      io.frontierlabs.namaz.core.Hijri.MAX_ADJUST)

    fun setHijriAdjust(prefs: SharedPreferences, days: Int) =
        prefs.edit().putInt(
            "hijriAdjust",
            days.coerceIn(-io.frontierlabs.namaz.core.Hijri.MAX_ADJUST,
                          io.frontierlabs.namaz.core.Hijri.MAX_ADJUST),
        ).apply()

    // --- notification switches ---------------------------------------------
    // All default to on. Someone who installs a prayer app and never opens
    // Settings should still be told when Fajr starts.

    fun prayerAlerts(prefs: SharedPreferences) = prefs.getBoolean("notifyPrayer", true)

    fun qazaAlerts(prefs: SharedPreferences) = prefs.getBoolean("notifyQaza", true)

    /**
     * Always on, and deliberately not a setting.
     *
     * This is the only channel that can tell the family a new build exists —
     * the app is not on the Play Store, so nothing else will. A switch here
     * would only ever be turned off by accident, and the phone would then sit
     * on an old version indefinitely with no way for anyone to find out. If
     * someone really does not want them, Android's own per-channel controls
     * still work.
     */
    fun updateAlerts(@Suppress("UNUSED_PARAMETER") prefs: SharedPreferences) = true

    /** How many minutes before the window closes the qaza warning fires. */
    fun warnMinutes(prefs: SharedPreferences) = prefs.getInt("warnMinutes", 15)

    fun setPrayerAlerts(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("notifyPrayer", on).apply()

    fun setQazaAlerts(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("notifyQaza", on).apply()

    fun setWarnMinutes(prefs: SharedPreferences, minutes: Int) =
        prefs.edit().putInt("warnMinutes", minutes).apply()

    // --- the daily check-in -------------------------------------------------

    /**
     * A random number this install made up for itself, so the developer can
     * count how many phones are still using the app.
     *
     * Generated with [java.util.UUID.randomUUID], which means it is derived
     * from nothing: not the hardware, not the account, not the phone number.
     * It identifies this installation and nothing else, and reinstalling
     * produces a new one. Clearing the app's data does too, which is the
     * point -- there is deliberately no way to tie it back to a person.
     */
    fun installId(prefs: SharedPreferences): String {
        prefs.getString("installId", null)?.let { if (it.isNotBlank()) return it }
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("installId", fresh).apply()
        return fresh
    }

    /** Epoch day of the last check-in, or 0 if it has never happened. */
    fun lastPingDay(prefs: SharedPreferences) = prefs.getLong("lastPingDay", 0L)

    fun setLastPingDay(prefs: SharedPreferences, day: Long) =
        prefs.edit().putLong("lastPingDay", day).apply()

    /**
     * Whether to take part in the count. On by default, off in one tap.
     *
     * Default-on because a count that most people have opted out of is worse
     * than no count, and because what travels is a random number and a
     * version string. It is disclosed on the welcome screen and switchable in
     * Settings; nothing is hidden.
     */
    fun countMe(prefs: SharedPreferences) = prefs.getBoolean("countMe", true)

    fun setCountMe(prefs: SharedPreferences, on: Boolean) =
        prefs.edit().putBoolean("countMe", on).apply()

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

    /**
     * A version Android refused to install -- usually an APK signed with a
     * different key. Remembered so the app stops retrying it every day and
     * offers the browser download instead.
     */
    fun installFailedVersion(prefs: SharedPreferences) = prefs.getInt("installFailed", 0)

    fun setInstallFailedVersion(prefs: SharedPreferences, code: Int) =
        prefs.edit().putInt("installFailed", code).apply()

    // --- remote settings (see core.RemoteConfig) ---------------------------

    /**
     * Store what update.json says. A new Hijri correction replaces the
     * user's own adjustment once; the same value arriving again every day
     * does not, or nobody could ever change it back by hand.
     */
    fun applyRemote(prefs: SharedPreferences, cfg: io.frontierlabs.namaz.core.RemoteConfig) {
        val e = prefs.edit()
        val seen = if (prefs.contains("hijriRemote")) prefs.getInt("hijriRemote", 0) else null
        if (cfg.hijriAdjust != null && cfg.hijriAdjust != seen) {
            e.putInt("hijriRemote", cfg.hijriAdjust)
            e.putInt("hijriAdjust", cfg.hijriAdjust)
        }
        if (cfg.announcement != null) e.putString("announcement", cfg.announcement)
        else e.remove("announcement")
        if (cfg.announcementUr != null) e.putString("announcementUr", cfg.announcementUr)
        else e.remove("announcementUr")
        e.apply()
    }

    /** Identifies the current banner, whichever languages it was given in. */
    private fun announcementKey(prefs: SharedPreferences): String? =
        prefs.getString("announcement", null) ?: prefs.getString("announcementUr", null)

    /** The banner to show in [lang], or null when there is none or it was closed. */
    fun announcement(prefs: SharedPreferences, lang: io.frontierlabs.namaz.core.Lang): String? {
        val key = announcementKey(prefs) ?: return null
        // Closing hides this banner only; a new announcement shows again.
        if (key == prefs.getString("announcementClosed", null)) return null
        return if (lang == io.frontierlabs.namaz.core.Lang.UR)
            prefs.getString("announcementUr", null) ?: key
        else prefs.getString("announcement", null) ?: key
    }

    fun closeAnnouncement(prefs: SharedPreferences) {
        val key = announcementKey(prefs) ?: return
        prefs.edit().putString("announcementClosed", key).apply()
    }
}
