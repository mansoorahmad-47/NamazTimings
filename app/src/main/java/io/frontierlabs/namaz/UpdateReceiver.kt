package io.frontierlabs.namaz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.frontierlabs.namaz.core.UpdateAction
import io.frontierlabs.namaz.core.UpdateCheck

/**
 * The daily "is there a new version?" check, for an app that will never be on
 * the Play Store.
 *
 * Nothing pushes anything to these phones. There is no server and no Firebase
 * account behind this — the app simply asks, once a day, for one small JSON
 * file at a URL you control, and puts a notification up if the number in it is
 * higher than the number it was built with. Tapping the notification opens the
 * APK link directly.
 *
 * Practically: to ship a new version you publish the APK somewhere and edit
 * two numbers in that JSON file. Within a day every phone has been told.
 *
 * Two things this deliberately does not do. It does not nag: a version is
 * announced once and then stays quiet until the next one. And it never gets
 * in the way — a failed fetch, no signal, a deleted file, all pass silently,
 * because a prayer timetable that works offline must not depend on a server.
 */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)

        // Network on the main thread would crash; goAsync buys a short window
        // on a background thread, which is plenty for one small file.
        val pending = goAsync()
        Thread {
            try {
                // The check-in rides along here rather than waking the phone a
                // second time. It sits above the notification guards on
                // purpose: someone who has denied notifications still has the
                // app installed, and leaving them out would quietly understate
                // the count.
                runCatching { Stats.pingIfDue(context) }

                if (Prefs.updateAlerts(prefs) && Notifications.allowed(context)) {
                    val decision = UpdateChecker.fetch(context)
                    val info = decision.info
                    if (info != null &&
                        decision.action != UpdateAction.NONE &&
                        info.latestVersionCode > Prefs.lastNotifiedVersion(prefs)
                    ) {
                        Notifications.ensureChannels(context)
                        Notifications.postUpdate(context, info)
                        Prefs.setLastNotifiedVersion(prefs, info.latestVersionCode)
                    }
                }
            } catch (_: Throwable) {
                // Fail open, always.
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * Where the update file lives, and how the installed version is read.
 *
 * Point [URL] at a file you control. A GitHub raw URL on the repo that builds
 * the APK is the simplest option and costs nothing:
 *
 *   https://raw.githubusercontent.com/<user>/<repo>/main/update.json
 *
 *   {
 *     "latestVersionCode": 2,
 *     "latestVersionName": "1.1",
 *     "minSupportedVersionCode": 0,
 *     "downloadUrl": "https://github.com/<user>/<repo>/releases/download/v1.1/app-release.apk",
 *     "notes": "Adds notifications and the calendar"
 *   }
 *
 * `latestVersionCode` must match the `versionCode` in app/build.gradle.kts of
 * the build you are announcing. Raise `minSupportedVersionCode` only when the
 * old build is genuinely unusable — that is what turns the friendly prompt
 * into one that cannot be dismissed.
 */
object UpdateChecker {

    const val URL =
        "https://raw.githubusercontent.com/mansoorahmad-47/NamazTimings/main/update.json"

    fun installedVersion(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        info.versionCode
    }.getOrDefault(0)

    /** Blocking. Call it off the main thread. Never throws. */
    fun fetch(context: Context): io.frontierlabs.namaz.core.UpdateDecision {
        val body = runCatching {
            val conn = java.net.URL(URL).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            try {
                if (conn.responseCode in 200..299)
                    conn.inputStream.bufferedReader().use { it.readText() }
                else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

        return UpdateCheck.check(installedVersion(context), body)
    }
}
