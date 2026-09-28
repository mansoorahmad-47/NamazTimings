package io.frontierlabs.namaz

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.IntentCompat
import io.frontierlabs.namaz.core.Strings
import io.frontierlabs.namaz.core.UpdateAction
import io.frontierlabs.namaz.core.UpdateInfo
import java.io.File

/**
 * The app updating itself, so nobody has to visit GitHub.
 *
 * The APK is downloaded by the app and handed straight to Android's own
 * installer. What happens next depends on the phone:
 *
 *  - **Android 8 to 11:** Android always asks. The user sees its install
 *    screen and taps Install -- one tap, no browser, no hunting for a file.
 *  - **Android 12 and newer:** once the app has installed one update of
 *    itself, Android treats it as the app's installer and lets later
 *    updates go in with no prompt at all. The very first one still asks,
 *    because the copy on the phone was installed by the browser.
 *
 * On every version Android first asks, once, whether this app may install
 * apps ("Install unknown apps"). That switch belongs to the user and there
 * is no way around it, nor should there be.
 *
 * Nothing here can install anything but this app: the downloaded file must
 * carry this app's package name and a higher version, and Android itself
 * refuses an update signed with a different key.
 */
object SelfUpdate {

    /**
     * True while the app is on screen. A silent install kills the running
     * app, so the background job never installs while someone is using it;
     * the update dialog offers it instead.
     */
    @Volatile
    var appVisible = false

    private const val EXTRA_INTERACTIVE = "interactive"
    private const val EXTRA_CODE = "versionCode"
    private const val EXTRA_NAME = "versionName"

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    private fun apkFile(context: Context, versionCode: Int) =
        File(dir(context), "NamazTimings-$versionCode.apk")

    /** May this app install APKs? The user grants it once, in Android's settings. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Android's settings page where the user allows it. */
    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Download the APK for [info], or reuse one already downloaded.
     * Blocking; call it off the main thread. Returns null on any failure --
     * no connection, a broken file, the wrong app -- and never throws.
     */
    fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}): File? =
        runCatching {
            val target = apkFile(context, info.latestVersionCode)
            if (target.exists() && verify(context, target, info.latestVersionCode)) {
                onProgress(1f)
                return@runCatching target
            }

            // Only one APK is ever kept; older ones are just wasted space.
            dir(context).mkdirs()
            dir(context).listFiles()?.forEach { it.delete() }

            val part = File(dir(context), target.name + ".part")
            // GitHub answers a release link with a redirect to its file
            // host. Both are https, which HttpURLConnection follows itself.
            val conn = java.net.URL(info.downloadUrl).openConnection()
                as java.net.HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (!part.renameTo(target)) return@runCatching null
            if (!verify(context, target, info.latestVersionCode)) {
                target.delete()
                return@runCatching null
            }
            target
        }.getOrNull()

    /** The file is this app, at least [expectedCode], and newer than what is installed. */
    private fun verify(context: Context, file: File, expectedCode: Int): Boolean {
        val pi = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        val code = versionCode(pi)
        return pi.packageName == context.packageName &&
            code >= expectedCode &&
            code > UpdateChecker.installedVersion(context)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else pi.versionCode.toLong()

    /**
     * Hand the APK to Android's installer. The answer arrives later, in
     * [InstallResultReceiver]. [interactive] means the user just tapped
     * Install and is looking at the app, so any confirmation Android wants
     * can be shown straight away rather than as a notification.
     */
    fun install(context: Context, file: File, info: UpdateInfo, interactive: Boolean): Boolean =
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                // Android 12+: install without asking where Android allows
                // it. Where it does not, it simply asks, as older versions do.
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(
                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    )
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(EXTRA_INTERACTIVE, interactive)
                    .putExtra(EXTRA_CODE, info.latestVersionCode)
                    .putExtra(EXTRA_NAME, info.latestVersionName)
                // Mutable because the installer adds its status to it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val sender = PendingIntent.getBroadcast(context, id, result, flags).intentSender
                session.commit(sender)
            }
            true
        }.getOrDefault(false)

    /** After an update has gone in: the APK and its notification are done with. */
    fun cleanUp(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
        Notifications.cancelUpdate(context)
    }

    /** One update notification per version, however many ways we get there. */
    fun notifyOnce(context: Context, versionCode: Int, post: () -> Unit) {
        val prefs = Prefs.get(context)
        if (versionCode <= Prefs.lastNotifiedVersion(prefs)) return
        if (!Notifications.allowed(context)) return
        Notifications.ensureChannels(context)
        post()
        Prefs.setLastNotifiedVersion(prefs, versionCode)
    }

    internal fun codeOf(intent: Intent) = intent.getIntExtra(EXTRA_CODE, 0)
    internal fun nameOf(intent: Intent) = intent.getStringExtra(EXTRA_NAME) ?: ""
    internal fun isInteractive(intent: Intent) = intent.getBooleanExtra(EXTRA_INTERACTIVE, false)
}

/** What Android's installer decided. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = SelfUpdate.codeOf(intent)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {

            // Android wants the user to confirm: always on 8-11, and on
            // 12+ the first time. Show it now if they are in the app and
            // just asked for it; otherwise leave a notification to tap.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent, Intent.EXTRA_INTENT, Intent::class.java
                ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val shown = SelfUpdate.isInteractive(intent) && SelfUpdate.appVisible &&
                    runCatching { context.startActivity(confirm) }.isSuccess
                if (!shown) {
                    SelfUpdate.notifyOnce(context, code) {
                        Notifications.postInstallReady(context, confirm, SelfUpdate.nameOf(intent))
                    }
                }
            }

            // Nothing to do: the process is replaced by the new version, and
            // BootReceiver tidies up on MY_PACKAGE_REPLACED.
            PackageInstaller.STATUS_SUCCESS -> {}

            // The user pressed Cancel. Not a failure; ask again next time.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {}

            // A real refusal -- most often an APK signed with a different
            // key, or no space. Retrying would fail the same way every day,
            // so remember it and let the dialog offer the browser instead.
            else -> {
                Prefs.setInstallFailedVersion(Prefs.get(context), code)
                if (SelfUpdate.isInteractive(intent)) {
                    val s = Strings.of(Prefs.lang(Prefs.get(context)))
                    runCatching {
                        Toast.makeText(context, s.updateFailed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

/**
 * The background half: download the update and install it while nobody is
 * using the app. A job rather than the daily receiver itself, because a
 * download can outlast the few seconds a receiver is allowed, and because the
 * job can wait for a connection and retry on its own.
 */
class UpdateJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            var retry = false
            try {
                retry = runOnce()
            } catch (_: Throwable) {
                retry = true
            } finally {
                jobFinished(params, retry)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    /** Returns true when it is worth trying again later. */
    private fun runOnce(): Boolean {
        val context = applicationContext
        val prefs = Prefs.get(context)
        val decision = UpdateChecker.fetch(context)
        val info = decision.info ?: return false
        if (decision.action == UpdateAction.NONE) return false

        // Android refused this version before; tell them once and stop.
        if (Prefs.installFailedVersion(prefs) >= info.latestVersionCode) {
            SelfUpdate.notifyOnce(context, info.latestVersionCode) {
                Notifications.postUpdate(context, info)
            }
            return false
        }

        // Install permission never granted: a silent install is impossible,
        // so point them at the app, where the dialog walks them through it.
        if (!SelfUpdate.canInstall(context)) {
            SelfUpdate.notifyOnce(context, info.latestVersionCode) {
                Notifications.postUpdate(context, info)
            }
            return false
        }

        val file = SelfUpdate.download(context, info) ?: return true

        // Someone is using the app right now; installing would close it on
        // them. The dialog there offers the same, already-downloaded file.
        if (SelfUpdate.appVisible) return false

        if (!SelfUpdate.install(context, file, info, interactive = false)) return true
        return false
    }

    companion object {
        private const val JOB_ID = 7001

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}
