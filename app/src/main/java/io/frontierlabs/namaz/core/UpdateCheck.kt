package io.frontierlabs.namaz.core

/**
 * Forced and optional update checks for a sideloaded app.
 *
 * The app is not on the Play Store, so nothing tells it an update exists. It
 * has to ask. It fetches a small JSON file from a URL you control and compares
 * version codes.
 *
 * Two rules matter more than the code:
 *
 *  1. **It must fail open.** No internet, a typo'd URL, a deleted file, a
 *     GitHub outage — every one of those must leave the app fully usable. An
 *     offline prayer-times app that refuses to open because it could not
 *     reach a server is worse than one that never checked.
 *
 *  2. **A block is a strong nudge, not enforcement.** Someone can decline the
 *     update, stay offline, or keep the old APK. Use [minSupportedVersionCode]
 *     only when an old version is genuinely broken.
 */

/** What the app should do about the installed version. */
enum class UpdateAction {
    /** Up to date, or the check failed. Carry on. */
    NONE,

    /** Newer version exists. Offer it, let it be dismissed. */
    OPTIONAL,

    /** Installed version is below the supported floor. Block until updated. */
    REQUIRED,
}

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val downloadUrl: String,
    val notes: String,
)

data class UpdateDecision(
    val action: UpdateAction,
    val info: UpdateInfo?,
)

object UpdateCheck {

    /**
     * Parse the hosted JSON.
     *
     * Hand-rolled rather than pulling in a JSON library: the document is five
     * flat fields, and this keeps the app dependency-free. Any malformed or
     * missing field returns null, which the caller treats as "no update" —
     * see the fail-open rule above.
     */
    fun parse(json: String?): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        val latest = intField(json, "latestVersionCode") ?: return null
        val url = stringField(json, "downloadUrl") ?: return null
        if (!url.startsWith("https://")) return null   // never send users to plain http
        return UpdateInfo(
            latestVersionCode = latest,
            latestVersionName = stringField(json, "latestVersionName") ?: "$latest",
            // Absent means "nothing is forced", which is the safe default.
            minSupportedVersionCode = intField(json, "minSupportedVersionCode") ?: 0,
            downloadUrl = url,
            notes = stringField(json, "notes") ?: "",
        )
    }

    private fun stringField(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }

    private fun intField(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    /** What to do, given the installed version code and the fetched info. */
    fun decide(installedVersionCode: Int, info: UpdateInfo?): UpdateDecision {
        if (info == null) return UpdateDecision(UpdateAction.NONE, null)
        return when {
            installedVersionCode < info.minSupportedVersionCode ->
                UpdateDecision(UpdateAction.REQUIRED, info)
            installedVersionCode < info.latestVersionCode ->
                UpdateDecision(UpdateAction.OPTIONAL, info)
            else -> UpdateDecision(UpdateAction.NONE, info)
        }
    }

    /** Convenience: parse and decide in one step. */
    fun check(installedVersionCode: Int, json: String?): UpdateDecision =
        decide(installedVersionCode, parse(json))

    /**
     * The settings riding along in the same file. See [RemoteConfig].
     *
     * Null when the document is not ours at all -- a 404 page, a captive
     * Wi-Fi portal, an empty body. That distinction matters: a missing
     * `announcement` in a real file means "take the banner down", but a
     * failed fetch must leave everything on the phone exactly as it was.
     */
    fun parseConfig(json: String?): RemoteConfig? {
        if (json.isNullOrBlank() || intField(json, "latestVersionCode") == null) return null
        return RemoteConfig(
            // Out of range is treated as a typo and ignored, not clamped:
            // a clamped typo would still move everyone's date.
            hijriAdjust = signedIntField(json, "hijriAdjust")
                ?.takeIf { it in -Hijri.MAX_ADJUST..Hijri.MAX_ADJUST },
            announcement = stringField(json, "announcement"),
            announcementUr = stringField(json, "announcementUr"),
        )
    }

    private fun signedIntField(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * Fixes that need no new version: edit update.json on GitHub, and every phone
 * picks the change up the next time it checks -- on opening the app, or in
 * the daily background check.
 *
 * Only data can travel this way, never code. Every field is optional.
 *
 * @param hijriAdjust the national moon-sighting correction, in days. When it
 *   changes it replaces each phone's own adjustment (the announcement is the
 *   deliberate, informed one); people can still nudge it afterwards. Absent
 *   means "leave everyone's setting alone".
 * @param announcement a banner at the top of the app, e.g. "Eid ul Adha is on
 *   Friday". Absent takes it down. Plain text, no double quotes.
 * @param announcementUr the same banner for people using the app in Urdu.
 *   Absent falls back to [announcement].
 */
data class RemoteConfig(
    val hijriAdjust: Int?,
    val announcement: String?,
    val announcementUr: String?,
)
