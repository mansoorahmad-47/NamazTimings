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
}
