package com.resqmesh.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ── GPS location wrapper ──────────────────────────────────────────────────────

/**
 * Wraps FusedLocationProviderClient and exposes:
 *  - [locationText] — human-readable "lat, lon" string for the UI
 *  - [currentLocation] — one-shot suspend getter for the latest fix
 *
 * Call [start] after BLE/Location permissions are granted.
 * Call [stop] in ViewModel.onCleared().
 */
class ResQLocationManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _locationText = MutableStateFlow("Acquiring…")
    val locationText: StateFlow<String> = _locationText

    private var _lastLocation: Location? = null

    private val locationRequest = LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        10_000L   // update interval: 10 seconds
    )
        .setMinUpdateIntervalMillis(5_000L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _lastLocation  = loc
            _locationText.value = "${"%.5f".format(loc.latitude)}° N, ${"%.5f".format(loc.longitude)}° E"
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")   // caller must have granted permissions
    fun start() {
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    /** Returns the most recently known location (may be null before first fix). */
    fun currentLocation(): Location? = _lastLocation
}
