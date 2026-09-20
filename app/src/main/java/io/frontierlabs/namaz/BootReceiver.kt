package io.frontierlabs.namaz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms do not survive a reboot, a clock change or an app update. Android
 * silently drops every one of them, and the only way the user finds out is
 * that Fajr stops waking them.
 *
 * So all four events re-arm:
 *
 *  - `BOOT_COMPLETED` — the obvious one.
 *  - `TIME_SET` / `TIMEZONE_CHANGED` — the alarms were computed as instants
 *    from Pakistan wall-clock times; if the phone's idea of "now" moves, the
 *    already-armed instants are wrong.
 *  - `MY_PACKAGE_REPLACED` — installing the next APK over the top wipes them,
 *    which for a sideloaded app that gets updated by hand is the most likely
 *    of the four to actually happen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" ->
                runCatching {
                    Scheduler.armAll(context)
                    NamazWidget.refresh(context)
                }
        }
    }
}
