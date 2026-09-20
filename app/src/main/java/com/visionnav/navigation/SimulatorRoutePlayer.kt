package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.routing.NavigationRoute
import com.visionnav.routing.RoutePoint

/**
 * High-fidelity pedestrian route simulator for testing navigation logic without walking outdoors.
 *
 * Supports generating realistic waypoint progressions, simulating turns, injecting GPS jitter,
 * deviating off-route, simulating wrong heading orientations, and testing arrival triggers.
 */
class SimulatorRoutePlayer(
    private val controller: NavigationController
) {
    private var currentStepIndex = 0
    private var simulatedHeading = 0f

    /**
     * Simulates walking along a route in discrete steps.
     */
    fun simulateWalkTo(latitude: Double, longitude: Double, heading: Float? = null, accuracy: Float = 4.0f) {
        val loc = CurrentLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            speedMetersPerSecond = 1.3f, // Typical pedestrian pace
            timestamp = System.currentTimeMillis()
        )
        controller.onLocationUpdate(loc, heading)
    }

    /**
     * Advances simulation along the active route's geometry points.
     */
    fun stepForward(route: NavigationRoute): CurrentLocation? {
        if (route.points.isEmpty() || currentStepIndex >= route.points.size) return null

        val point = route.points[currentStepIndex]
        val nextPoint = route.points.getOrNull(currentStepIndex + 1)

        simulatedHeading = if (nextPoint != null) {
            NavigationController.calculateBearing(
                point.latitude,
                point.longitude,
                nextPoint.latitude,
                nextPoint.longitude
            )
        } else {
            simulatedHeading
        }

        val location = CurrentLocation(
            latitude = point.latitude,
            longitude = point.longitude,
            accuracyMeters = 3.5f,
            speedMetersPerSecond = 1.3f,
            timestamp = System.currentTimeMillis()
        )

        controller.onLocationUpdate(location, simulatedHeading)
        currentStepIndex++
        return location
    }

    /**
     * Injects noisy GPS reading that deviates temporarily without persisting.
     */
    fun injectGpsJitter(anchorPoint: RoutePoint, jitterDistanceMeters: Double = 35.0) {
        // Approximate 35m shift in degrees (~0.000315 deg)
        val offsetDeg = jitterDistanceMeters / 111320.0
        val noisyLoc = CurrentLocation(
            latitude = anchorPoint.latitude + offsetDeg,
            longitude = anchorPoint.longitude + offsetDeg,
            accuracyMeters = 28.0f, // Noisy GPS has poor accuracy
            timestamp = System.currentTimeMillis()
        )
        controller.onLocationUpdate(noisyLoc, simulatedHeading)
    }

    /**
     * Simulates walking off-route to trigger rerouting.
     */
    fun simulateOffRoute(anchorPoint: RoutePoint, deviationMeters: Double = 40.0) {
        val offsetDeg = deviationMeters / 111320.0
        val offRouteLoc = CurrentLocation(
            latitude = anchorPoint.latitude + offsetDeg,
            longitude = anchorPoint.longitude + offsetDeg,
            accuracyMeters = 5.0f, // High accuracy confirms user is actually off route
            timestamp = System.currentTimeMillis()
        )
        controller.onLocationUpdate(offRouteLoc, simulatedHeading)
    }

    /**
     * Simulates facing backwards / wrong direction.
     */
    fun simulateWrongDirection(currentPoint: RoutePoint, trueCourse: Float) {
        val backwardsHeading = (trueCourse + 180f) % 360f
        val loc = CurrentLocation(
            latitude = currentPoint.latitude,
            longitude = currentPoint.longitude,
            accuracyMeters = 3.0f,
            timestamp = System.currentTimeMillis()
        )
        controller.onLocationUpdate(loc, backwardsHeading)
    }

    fun reset() {
        currentStepIndex = 0
        simulatedHeading = 0f
    }
}

