package io.frontierlabs.namaz

import android.content.Context
import io.frontierlabs.namaz.core.Ping
import java.time.LocalDate

/**
 * The once-a-day check-in that produces the "how many phones" number.
 *
 * **This does nothing until [URL] is filled in.** Shipped empty, the app
 * sends absolutely nothing and the switch in Settings is not even shown.
 * That is deliberate: the app should be silent by default, and reporting
 * should start only when the person who built it has decided to set it up.
 *
 * What the count can honestly say, from [Ping]'s documentation: nothing can
 * detect an uninstall, and nothing can see whether an app is open. So the
 * numbers on the other end are "distinct phones that checked in within the
 * last day" and "within the last 30 days" — opened recently, and still around.
 */
object Stats {

    /**
     * Where the check-in goes. Empty by default; nothing is sent while it is.
     *
     * To switch counting on, make a Google Sheet, paste in the script from
     * `tools/stats.gs` (Extensions → Apps Script), deploy it as a web app,
     * and put the resulting `https://script.google.com/macros/s/.../exec`
     * address here. The full steps are in the README.
     *
     * Must be https. [Ping.url] refuses anything else rather than sending a
     * request in the clear.
     */
    const val URL = "https://script.google.com/macros/s/AKfycbx7EKR4kUfE6IN8XtLkw-U8-qIoQBn34rfivEjmprqdtQd-585jEqRkl5t6bRF28Fu6sQ/exec"

    /** False when counting has not been set up, so the UI can hide the switch. */
    fun configured(): Boolean = URL.isNotBlank()

    /**
     * Send today's check-in, if one is due and the user has not opted out.
     *
     * Blocking; call it from a background thread. Never throws, and a failure
     * is not recorded as a success, so a phone that was offline all day tries
     * again tomorrow rather than being skipped.
     */
    fun pingIfDue(context: Context) {
        if (!configured()) return

        val prefs = Prefs.get(context)
        if (!Prefs.countMe(prefs)) return

        val today = LocalDate.now(PK).toEpochDay()
        if (!Ping.isDue(Prefs.lastPingDay(prefs), today)) return

        val installed = UpdateChecker.installedVersion(context)
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""

        val url = Ping.url(URL, Prefs.installId(prefs), installed, versionName) ?: return

        val sent = runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true   // Apps Script redirects once
            try {
                conn.responseCode in 200..399
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

        // Only mark the day done on success. Recording a failed attempt would
        // silently drop a phone from the count for a whole day whenever it
        // happened to be offline at the wrong moment.
        if (sent) Prefs.setLastPingDay(prefs, today)
    }
}
