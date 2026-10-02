package org.cellularprivacy.detector.collect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Lightweight last-known-location holder, used to geo-tag observed cells and
 * detection events. Uses the framework [LocationManager] (GPS + network), not
 * Google Play Services, so the app stays GMS-free and F-Droid friendly.
 */
class LocationProvider(private val context: Context) : LocationListener {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile var last: Location? = null
        private set

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        last = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()

        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 15_000L, 25f, this, Looper.getMainLooper())
                }
            }
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        last = location
    }

    @Deprecated("Deprecated in API 29, still called on older providers")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
    override fun onProviderEnabled(provider: String) { }
    override fun onProviderDisabled(provider: String) { }
}
