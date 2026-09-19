package io.frontierlabs.namaz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.frontierlabs.namaz.core.Cities
import io.frontierlabs.namaz.core.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Working out which city someone is in.
 *
 * Deliberately built on the plain Android [LocationManager] rather than Google
 * Play Services. Play Services gives a better fix, but it is a large
 * dependency, it is missing from some phones, and here the extra accuracy is
 * worthless: the answer is one of 66 cities that sit tens of kilometres apart.
 * Coarse location, roughly cell-tower accurate, is already far more precise
 * than the question needs.
 *
 * That is also why the app asks for ACCESS_COARSE_LOCATION and not the precise
 * permission. Asking for someone's exact position in order to choose between
 * Peshawar and Lahore would be asking for more than the feature uses.
 */
object LocationFinder {

    private const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /** What came of trying to find the city. */
    sealed interface Result {
        data class Found(val city: City, val km: Double) : Result

        /** A fix arrived, but it is nowhere near any city in the list. */
        data class TooFar(val city: City, val km: Double) : Result

        /** No permission, no provider, or no fix in time. */
        data object Unavailable : Result
    }

    /**
     * Find the nearest city, or say why not.
     *
     * The last known position is tried first: it is instant, and for choosing
     * a city an hour-old fix is as good as a fresh one. Only when nothing is
     * cached does it wait for a live fix, and then not for long. Someone
     * indoors may never get one, and a setup screen that hangs forever is
     * worse than one that offers the list.
     */
    suspend fun findCity(context: Context, timeoutMs: Long = 12_000): Result {
        if (!hasPermission(context)) return Result.Unavailable

        val location = lastKnown(context)
            ?: freshFix(context, timeoutMs)
            ?: return Result.Unavailable

        val (city, km) = Cities.nearest(location.latitude, location.longitude)
        return if (km <= Cities.MAX_TRUSTED_KM) Result.Found(city, km)
        else Result.TooFar(city, km)
    }

    /** The newest cached position from any enabled provider. */
    private fun lastKnown(context: Context): Location? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    /**
     * Ask for one live fix, giving up after [timeoutMs].
     *
     * Registered on the main thread because LocationManager needs a Looper,
     * and unregistered on every path out — first fix, timeout, cancellation —
     * so nothing is left listening to the radio after the screen has moved on.
     */
    private suspend fun freshFix(context: Context, timeoutMs: Long): Location? =
        withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE)
                    as? LocationManager ?: return@withContext null

                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                        LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                        LocationManager.GPS_PROVIDER
                    else -> return@withContext null
                }

                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { lm.removeUpdates(this) }
                            if (cont.isActive) cont.resume(location)
                        }
                        override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            runCatching { lm.removeUpdates(this) }
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }

                    val started = runCatching {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener)
                    }.isSuccess
                    if (!started && cont.isActive) cont.resume(null)
                }
            }
        }
}
