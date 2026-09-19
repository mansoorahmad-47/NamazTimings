package io.frontierlabs.namaz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.frontierlabs.namaz.core.Alert
import io.frontierlabs.namaz.core.AlertKind
import io.frontierlabs.namaz.core.Strings
import io.frontierlabs.namaz.core.UpdateInfo
import java.time.ZoneId

/**
 * Pakistan is UTC+5 all year with no daylight saving, so a wall-clock minute
 * always maps to the same instant. Every date and alarm calculation uses this
 * zone rather than the phone's, which means the times stay right for someone
 * checking Peshawar's timetable from abroad.
 */
val PK: ZoneId = ZoneId.of("Asia/Karachi")

/**
 * Posting notifications, and the channels they go to.
 *
 * Three channels rather than one, because they deserve different treatment
 * and Android only lets the user control them separately if they *are*
 * separate. Someone may want the qaza warning to make noise at 5:40 PM while
 * the quiet "Zuhr has begun" note stays silent, and they can only arrange
 * that if the two live in different channels.
 *
 * The text follows the language chosen inside the app, not the phone's
 * locale — a notification in English from an app the user has set to Urdu
 * would be jarring. Re-creating a channel with the same id updates its name,
 * so switching language relabels them in Android's own settings too.
 */
object Notifications {

    const val CH_PRAYER = "prayer"
    const val CH_QAZA = "qaza"
    const val CH_UPDATE = "update"

    private const val ID_UPDATE = 2000

    /** Idempotent: safe to call on every launch, boot and alarm. */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val s = Strings.of(Prefs.lang(Prefs.get(context)))

        nm.createNotificationChannel(
            NotificationChannel(
                CH_PRAYER, s.channelPrayerName, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = s.channelPrayerDesc }
        )
        nm.createNotificationChannel(
            // HIGH so it can appear as a heads-up. This is the one with a
            // deadline attached; a warning noticed an hour later is useless.
            NotificationChannel(
                CH_QAZA, s.channelQazaName, NotificationManager.IMPORTANCE_HIGH
            ).apply { description = s.channelQazaDesc }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_UPDATE, s.channelUpdateName, NotificationManager.IMPORTANCE_LOW
            ).apply { description = s.channelUpdateDesc }
        )
    }

    /** False when the user has denied or switched off notifications. */
    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Post the notification for one fired alert. */
    fun post(context: Context, alert: Alert, minutesLeft: Int) {
        val lang = Prefs.lang(Prefs.get(context))
        val s = Strings.of(lang)
        val name = Strings.prayerName(lang, alert.prayer)

        val (channel, title, body) = when (alert.kind) {
            AlertKind.PRAYER_START -> Triple(
                CH_PRAYER, "$name ${s.hasBegun}", s.timeToPray,
            )
            AlertKind.QAZA_WARNING -> Triple(
                CH_QAZA, "$name ${s.aboutToBecomeQaza}",
                "$minutesLeft ${s.minutesLeft} · ${s.prayItNow}",
            )
        }

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (alert.kind == AlertKind.QAZA_WARNING) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

        // POST_NOTIFICATIONS can be revoked at any time; notify() then throws.
        runCatching {
            NotificationManagerCompat.from(context).notify(alert.requestCode, n)
        }
    }

    /**
     * "A new version is ready", with the download link on the tap.
     *
     * This is the whole point of the update mechanism for a sideloaded app:
     * the family does not check anything, so the phone has to tell them, and
     * tapping has to land directly on the APK.
     */
    fun postUpdate(context: Context, info: UpdateInfo) {
        val s = Strings.of(Prefs.lang(Prefs.get(context)))

        val open = PendingIntent.getActivity(
            context, 1,
            Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = buildString {
            append(s.tapToDownload)
            if (info.notes.isNotBlank()) append("\n\n").append(info.notes)
        }

        val n = NotificationCompat.Builder(context, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${s.newVersionReady} — ${info.latestVersionName}")
            .setContentText(s.tapToDownload)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(ID_UPDATE, n) }
    }
}
