package com.androidautobrowser.browser.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.androidautobrowser.browser.R
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Best-effort place label + coordinates for the start page / weather.
 */
class LocationStatusHelper(
    private val context: Context,
    private val onPlace: (label: String, location: Location?) -> Unit,
) : LocationListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val geoExecutor = Executors.newSingleThreadExecutor()
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var started = false

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            publish(context.getString(R.string.location_permission_needed), null)
            return
        }
        if (!isLocationEnabled()) {
            publish(context.getString(R.string.location_disabled), null)
            return
        }

        publish(context.getString(R.string.location_loading), null)

        val last = bestLastKnownLocation()
        if (last != null) {
            resolveLabel(last)
        }

        if (started) return
        started = true

        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    this,
                    Looper.getMainLooper(),
                )
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { locationManager.removeUpdates(this) }
    }

    fun destroy() {
        stop()
        geoExecutor.shutdownNow()
    }

    override fun onLocationChanged(location: Location) {
        resolveLabel(location)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        if (!isLocationEnabled()) {
            publish(context.getString(R.string.location_disabled), null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(): Location? {
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        return candidates.maxByOrNull { it.time }
    }

    private fun resolveLabel(location: Location) {
        geoExecutor.execute {
            val label = reverseGeocode(location)
                ?: context.getString(
                    R.string.location_coords,
                    location.latitude,
                    location.longitude,
                )
            publish(label, location)
        }
    }

    private fun reverseGeocode(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val address = results?.firstOrNull() ?: return null
            listOfNotNull(
                address.subLocality,
                address.locality,
                address.subAdminArea,
                address.adminArea,
            ).distinct().take(2).joinToString(", ").ifBlank { null }
        }.getOrNull()
    }

    private fun publish(label: String, location: Location?) {
        mainHandler.post { onPlace(label, location) }
    }

    companion object {
        private const val MIN_TIME_MS = 30_000L
        private const val MIN_DISTANCE_M = 50f
    }
}
