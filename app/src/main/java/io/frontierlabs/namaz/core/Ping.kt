package io.frontierlabs.namaz.core

/**
 * The daily check-in that lets the developer count how many phones are still
 * using the app.
 *
 * Kept deliberately small, and worth being clear about what it can and cannot
 * tell you:
 *
 *  - **Installs cannot be counted.** Nothing runs after an uninstall, so a
 *    phone that removed the app is indistinguishable from one that is merely
 *    switched off. The honest figure is "checked in within the last N days".
 *  - **"Running right now" cannot be known either.** Android does not report
 *    when an app is opened or closed. "Opened in the last day" is the nearest
 *    real number, and it is the one this sends.
 *
 * What travels is one random number and the app's version. The random number
 * is made up by the phone on first run and is not derived from anything — not
 * the hardware, not the account, not the phone number — so it identifies an
 * install and nothing else. No city, no language, no location, no times.
 *
 * All of this is inert until [io.frontierlabs.namaz.Stats.URL] is filled in.
 * An empty URL means nothing is ever sent, which is how the app ships.
 */
object Ping {

    /** Characters allowed through a URL query value untouched. */
    private const val SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /**
     * Percent-encode a query value.
     *
     * Hand-rolled rather than `URLEncoder` so this file stays pure Kotlin and
     * testable on any JVM without pulling in java.net, and so the rule is
     * visible: anything not plainly safe is escaped, including the space,
     * which `URLEncoder` famously turns into `+` rather than `%20`.
     */
    fun encode(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (SAFE.indexOf(c) >= 0) {
                out.append(c)
            } else {
                out.append('%')
                out.append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0x0F])
                out.append("0123456789ABCDEF"[byte.toInt() and 0x0F])
            }
        }
        return out.toString()
    }

    /**
     * Has a day rolled over since the last check-in?
     *
     * Days, not hours: one ping per calendar day is all "opened in the last
     * day" needs, and anything more often is traffic for its own sake. A
     * [lastSent] in the future — a phone whose clock was wrong and has been
     * corrected — also counts as due rather than locking the app out of
     * pinging until the date catches up.
     */
    fun isDue(lastSent: Long, today: Long): Boolean = lastSent != today

    /** A well-formed install id: what [io.frontierlabs.namaz.Prefs] generates. */
    fun isValidId(id: String): Boolean =
        id.length in 8..64 && id.all { it.isLetterOrDigit() || it == '-' }

    /**
     * Build the check-in URL, or null when it should not be sent at all.
     *
     * Null rather than an exception, because every caller's correct response
     * to "cannot build this" is "do nothing quietly". Plain http is refused
     * outright: a prayer app should not be making unencrypted requests, even
     * ones this trivial.
     */
    fun url(base: String, id: String, versionCode: Int, versionName: String): String? {
        if (base.isBlank()) return null
        if (!base.startsWith("https://")) return null
        if (!isValidId(id)) return null

        val separator = if (base.contains('?')) "&" else "?"
        return base + separator +
            "id=" + encode(id) +
            "&vc=" + versionCode +
            "&v=" + encode(versionName)
    }
}
