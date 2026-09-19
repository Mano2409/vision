package com.visionnav.simulator

import com.visionnav.location.ILocationProvider
import com.visionnav.location.LocationConfidence
import com.visionnav.location.NavLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Development LocationProvider simulator for testing walking navigation indoors
 * and without active GPS reception.
 */
class SimulatorLocationProvider(
    initialLatitude: Double = 13.0820,
    initialLongitude: Double = 80.2700
) : ILocationProvider {

    // Pre-recorded realistic walking waypoints (approx 12-15m apart along a sidewalk)
    private val simulatedWaypoints = listOf(
        Pair(13.0820, 80.2700), // Starting point: Sidewalk
        Pair(13.0821, 80.2701), // Step 1
        Pair(13.0822, 80.2702), // Step 2
        Pair(13.0823, 80.2703), // Step 3
        Pair(13.0825, 80.2704), // Step 4 (Approaching turn)
        Pair(13.0826, 80.2706), // Step 5 (Turn onto Pine Street)
        Pair(13.0827, 80.2707)  // Step 6 (Arriving at Central Station)
    )

    private var currentWaypointIndex = 0

    private val _locationFlow = MutableStateFlow(
        NavLocation(
            latitude = initialLatitude,
            longitude = initialLongitude,
            accuracyMeters = 4.5f,
            timestamp = System.currentTimeMillis(),
            speedMps = 1.3f, // Standard walking speed ~4.7 km/h
            bearingDegrees = 45f,
            confidence = LocationConfidence.HIGH,
            isOutlier = false,
            isAvailable = true
        )
    )
    override val locationFlow: StateFlow<NavLocation> = _locationFlow.asStateFlow()

    private var isStarted = false

    override fun start() {
        isStarted = true
        updateLocationState(_locationFlow.value.latitude, _locationFlow.value.longitude)
    }

    override fun stop() {
        isStarted = false
    }

    override fun setUpdateInterval(intervalMs: Long) {
        // Simulator no-op for interval
    }

    /**
     * Advances simulated user position to the next waypoint along the test route.
     */
    fun stepForward() {
        currentWaypointIndex = (currentWaypointIndex + 1) % simulatedWaypoints.size
        val (nextLat, nextLon) = simulatedWaypoints[currentWaypointIndex]
        updateLocationState(nextLat, nextLon)
    }

    /**
     * Resets simulated position back to the starting waypoint.
     */
    fun resetToStart() {
        currentWaypointIndex = 0
        val (startLat, startLon) = simulatedWaypoints[0]
        updateLocationState(startLat, startLon)
    }

    /**
     * Injects an intentional GPS multipath outlier jump to test system anomaly rejection.
     */
    fun injectOutlierJump() {
        val current = _locationFlow.value
        _locationFlow.value = current.copy(
            latitude = current.latitude + 0.05, // ~5.5 km teleport
            longitude = current.longitude + 0.05,
            isOutlier = true,
            confidence = LocationConfidence.LOW,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Injects low accuracy / uncertain GPS signal.
     */
    fun setLowAccuracy() {
        val current = _locationFlow.value
        _locationFlow.value = current.copy(
            accuracyMeters = 38.0f,
            confidence = LocationConfidence.LOW,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun updateLocationState(lat: Double, lon: Double) {
        val current = _locationFlow.value
        val now = System.currentTimeMillis()
        val distance = current.distanceTo(lat, lon)
        val timeDeltaSec = (now - current.timestamp) / 1000f

        _locationFlow.value = NavLocation(
            latitude = lat,
            longitude = lon,
            accuracyMeters = 3.5f,
            timestamp = now,
            speedMps = if (timeDeltaSec > 0) distance / timeDeltaSec else 1.3f,
            bearingDegrees = current.bearingDegrees,
            confidence = LocationConfidence.HIGH,
            isOutlier = false,
            isAvailable = true
        )
    }
}

