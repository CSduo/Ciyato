package com.ciyato.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * LocationHelper — returns the device's current foreground location.
 *
 * The weather screen requests FINE and COARSE together, as Android requires on
 * Android 12+. The person may still choose approximate location; this helper
 * works in either case and uses GPS only when precise access was granted.
 *
 * Strategy (no Play Services required):
 *   1. Prefer a recent GPS fix when precise access is available.
 *   2. Fall back to the network provider and choose the best recent fix.
 *   3. If still nothing, request a fresh single update and wait up to 8 s.
 *   4. Return null only if all three steps fail.
 */
object LocationHelper {

    data class LatLon(val lat: Double, val lon: Double, val accuracyMeters: Float? = null)

    /**
     * Returns a [LatLon] or null.
     * Must be called from a coroutine; suspends for at most ~8 seconds.
     */
    suspend fun getLocation(context: Context): LatLon? {
        if (!hasPermission(context)) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // Prefer GPS only when the user opted in to precise location.
        bestOf(
            if (hasPrecisePermission(context)) lm.getLastKnownSafe(LocationManager.GPS_PROVIDER) else null,
            lm.getLastKnownSafe(LocationManager.NETWORK_PROVIDER),
        )?.let { return it.toLatLon() }

        // Request a fresh fix with an 8-second timeout.
        val gpsLoc = withTimeoutOrNull(8_000L) {
            requestSingleUpdate(lm, hasPrecisePermission(context))
        }?.toLatLon()

        if (gpsLoc != null) return gpsLoc

        return getIpLocationFallback()
    }

    suspend fun getIpLocationFallback(): LatLon? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val conn = java.net.URL("http://ip-api.com/json/").openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val text = conn.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(text)
            val lat = json.optDouble("lat")
            val lon = json.optDouble("lon")
            if (!lat.isNaN() && !lon.isNaN()) LatLon(lat, lon) else null
        }.getOrNull()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPrecisePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun LocationManager.getLastKnownSafe(provider: String): Location? =
        runCatching { getLastKnownLocation(provider) }.getOrNull()

    private fun bestOf(vararg locations: Location?): Location? =
        locations.filterNotNull().minWithOrNull(
            compareBy<Location> { agePenalty(it) }.thenBy { it.accuracy }
        )

    private fun agePenalty(location: Location): Long =
        (System.currentTimeMillis() - location.time).coerceAtLeast(0L) / 60_000L

    private fun Location.toLatLon() = LatLon(latitude, longitude, accuracy)

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(lm: LocationManager, preferGps: Boolean): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String)  = Unit
                override fun onProviderDisabled(provider: String) {
                    if (cont.isActive) cont.resume(null)
                }
            }

            runCatching {
                lm.requestLocationUpdates(
                    if (preferGps && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    },
                    0L,    // minTimeMs
                    0f,    // minDistanceM
                    listener,
                )
            }.onFailure {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
}
