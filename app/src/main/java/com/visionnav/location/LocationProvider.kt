package com.visionnav.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native Android LocationProvider utilizing Google Play Services FusedLocationProviderClient.
 *
 * Implements high-accuracy positioning, adaptive update rates (1-2s during walking),
 * outlier jump detection, and permission-safe lifecycle management.
 */
class LocationProvider(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) : ILocationProvider {

    companion object {
        private const val TAG = "LocationProvider"
        const val DEFAULT_WALKING_INTERVAL_MS = 1500L
        const val MIN_UPDATE_INTERVAL_MS = 1000L
        const val STATIONARY_INTERVAL_MS = 10000L
    }

    private val _locationFlow = MutableStateFlow(NavLocation.UNAVAILABLE)
    override val locationFlow: StateFlow<NavLocation> = _locationFlow.asStateFlow()

    private var currentIntervalMs = DEFAULT_WALKING_INTERVAL_MS
    private var isRunning = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val lastLocation = result.lastLocation ?: return
            val previous = _locationFlow.value

            val navLoc = NavLocation.fromAndroidLocation(
                location = lastLocation,
                previousLocation = if (previous.isAvailable) previous else null
            )

            if (navLoc.isOutlier) {
                Log.w(TAG, "GPS Outlier jump detected: lat=${navLoc.latitude}, lon=${navLoc.longitude}")
            }

            _locationFlow.value = navLoc
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                Log.w(TAG, "Location services temporarily unavailable")
                if (_locationFlow.value.isAvailable) {
                    _locationFlow.value = _locationFlow.value.copy(isAvailable = false)
                }
            }
        }
    }

    override fun start() {
        if (isRunning) return

        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start LocationProvider: ACCESS_FINE_LOCATION not granted")
            _locationFlow.value = NavLocation.UNAVAILABLE
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentIntervalMs)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            isRunning = true
            Log.i(TAG, "LocationProvider started with interval ${currentIntervalMs}ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            _locationFlow.value = NavLocation.UNAVAILABLE
        }
    }

    override fun stop() {
        if (!isRunning) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            isRunning = false
            Log.i(TAG, "LocationProvider stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    override fun setUpdateInterval(intervalMs: Long) {
        if (currentIntervalMs == intervalMs) return
        currentIntervalMs = intervalMs
        if (isRunning) {
            stop()
            start()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }
}

