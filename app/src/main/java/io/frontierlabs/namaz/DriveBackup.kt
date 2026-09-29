package io.frontierlabs.namaz

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import io.frontierlabs.namaz.core.Backup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional backup of streaks to the user's own Google Drive.
 *
 * Off until someone chooses to sign in, and it stays their choice: the app
 * works exactly the same without it.
 *
 * **Where the data goes.** Drive's `appDataFolder` -- a hidden folder in the
 * user's own Drive that only this app can read. It does not appear among their
 * files, it counts a few kilobytes against their storage, and nobody else --
 * the developer included -- can see it. There is no server of ours in the
 * middle. The only permission asked for is `drive.appdata`, which reaches that
 * folder and nothing else in their Drive.
 *
 * **Setup (once, by whoever builds the app).** Google only hands out access
 * to an app it knows. In Google Cloud: create a project, enable the Google
 * Drive API, set up the OAuth consent screen (External, add the
 * `.../auth/drive.appdata` scope, then publish it to production -- in Testing
 * mode sign-ins expire after a week), and create an OAuth client of type
 * Android with package `io.frontierlabs.namaz` and the SHA-1 of the release
 * signing key (the build summary prints it). No key or client id goes in the
 * code; Google recognises the app by its package and signature. Until that is
 * done, sign-in simply fails with a message and nothing else is affected.
 */
object DriveBackup {

    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "streaks.json"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()

    /**
     * The SHA-1 of the certificate this installed copy is signed with, as
     * Google Cloud wants it ("AB:CD:..."). Shown when sign-in fails with
     * "unregistered", because that error means exactly this value (with the
     * package name) is not on an Android OAuth client -- and reading it off
     * the phone beats guessing which key signed which build.
     */
    @Suppress("DEPRECATION")
    fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val cert: ByteArray = if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(
                context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo!!.apkContentsSigners.first().toByteArray()
        } else {
            val info = pm.getPackageInfo(
                context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            info.signatures!!.first().toByteArray()
        }
        java.security.MessageDigest.getInstance("SHA-1").digest(cert)
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** Phones without Google Play services cannot sign in with Google at all. */
    fun available(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * Is Android's own Google backup switched on? Null when the phone will
     * not say -- newer Android versions keep this setting private -- so the
     * caller must treat "don't know" as its own answer rather than as yes.
     */
    fun androidBackupOn(context: Context): Boolean? = runCatching {
        Settings.Secure.getInt(context.contentResolver, "backup_enabled") == 1
    }.getOrNull()

    /**
     * A fresh access token without any screen, for the background job.
     * Null when Google needs the user to sign in again, or cannot be reached.
     * Blocking; call it off the main thread.
     */
    fun silentToken(context: Context): String? = runCatching {
        val result: AuthorizationResult =
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
        if (result.hasResolution()) {
            Prefs.setBackupNeedsSignIn(Prefs.get(context), true)
            null
        } else result.accessToken
    }.getOrNull()

    /** What a sync did, for the screen. */
    data class Outcome(val ok: Boolean, val restoredDays: Int = 0, val detail: String? = null)

    /**
     * Bring the phone and the backup into line: download, merge (see
     * core.Backup.merge), write the merge to the phone, upload it.
     * Blocking; never throws.
     */
    fun sync(context: Context, token: String): Outcome = runCatching {
        val prefs = Prefs.get(context)
        val local = Prefs.allDone(prefs)
        val dirty = Prefs.backupDirty(prefs)

        val fileId = findFile(token)
        // A backup that exists but cannot be read -- dropped connection,
        // damaged file -- stops the sync. Carrying on would upload this
        // phone's days over it, and on a freshly reinstalled phone that is
        // exactly the history the backup exists to protect.
        val remote = if (fileId == null) emptyMap()
            else Backup.parse(download(token, fileId)) ?: error("backup unreadable")
        val merged = Backup.merge(local, remote, dirty)

        // Days the phone did not have, or had differently, and now takes
        // from the backup -- the ones a reinstall gets back.
        val restored = merged.count { (day, set) -> local[day] != set && set.isNotEmpty() }
        Prefs.replaceAllDone(prefs, merged)

        upload(token, fileId, Backup.serialize(merged))
        Prefs.clearBackupDirty(prefs, dirty)
        Prefs.setLastBackup(prefs, System.currentTimeMillis())
        if (Prefs.backupAccount(prefs) == null) Prefs.setBackupAccount(prefs, email(token))
        if (restored > 0) runCatching { NamazWidget.refresh(context) }
        Outcome(true, restored)
    }.getOrElse { Outcome(false, detail = it.message) }

    /** Remove the backup from Drive entirely, for someone switching it off. */
    fun deleteBackup(token: String): Boolean = runCatching {
        val id = findFile(token) ?: return@runCatching true
        val c = open("$API/files/$id", token, "DELETE")
        try { c.responseCode in 200..299 } finally { c.disconnect() }
    }.getOrDefault(false)

    // --- Drive REST, by hand: four calls do not justify a client library --

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.body(): String? =
        if (responseCode in 200..299) inputStream.bufferedReader().use { it.readText() }
        else null

    private fun findFile(token: String): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val c = open("$API/files?spaces=appDataFolder&q=$q&fields=files(id)", token)
        val json = try {
            c.body() ?: error("list failed: ${c.responseCode}")
        } finally { c.disconnect() }
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    }

    private fun download(token: String, id: String): String? {
        val c = open("$API/files/$id?alt=media", token)
        return try { c.body() } finally { c.disconnect() }
    }

    private fun upload(token: String, id: String?, content: String) {
        val c = if (id != null) {
            // HttpURLConnection has no PATCH; Google accepts it as an override.
            open("$UPLOAD/files/$id?uploadType=media", token, "POST").apply {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        } else {
            open("$UPLOAD/files?uploadType=multipart", token, "POST")
        }
        val body = if (id != null) content else {
            val b = "namaz-backup-boundary"
            c.setRequestProperty("Content-Type", "multipart/related; boundary=$b")
            "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "{\"name\":\"$FILE_NAME\",\"parents\":[\"appDataFolder\"]}\r\n" +
                "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$content\r\n--$b--"
        }
        c.doOutput = true
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) error("upload failed: ${c.responseCode}")
        } finally { c.disconnect() }
    }

    /** Which account it went to, so Settings can say. Null is fine. */
    private fun email(token: String): String? = runCatching {
        val c = open("$API/about?fields=user(emailAddress)", token)
        val body = try { c.body() } finally { c.disconnect() }
        val json = body ?: return@runCatching null
        Regex("\"emailAddress\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    }.getOrNull()
}

/**
 * Backs up in the background after prayers are marked, once there is a
 * connection. Scheduled when the app goes to the background and from the
 * widget, so a morning of ticks becomes one upload rather than five.
 */
class BackupJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            var retry = false
            try {
                val prefs = Prefs.get(applicationContext)
                if (Prefs.driveBackupOn(prefs)) {
                    val token = DriveBackup.silentToken(applicationContext)
                    // No token because Google wants a fresh sign-in: nothing a
                    // retry can fix, Settings asks. Otherwise try again later.
                    retry = if (token == null) !Prefs.backupNeedsSignIn(prefs)
                            else !DriveBackup.sync(applicationContext, token).ok
                }
            } catch (_: Throwable) {
                retry = true
            } finally {
                jobFinished(params, retry)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 7002

        /** Cheap to call often: does nothing unless backup is on and something changed. */
        fun scheduleIfNeeded(context: Context) {
            val prefs = Prefs.get(context)
            if (!Prefs.driveBackupOn(prefs) || Prefs.backupDirty(prefs).isEmpty()) return
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, BackupJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(15 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}
