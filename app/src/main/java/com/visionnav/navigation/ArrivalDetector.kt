package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.routing.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent arrival detection for visually impaired pedestrian navigation.
 *
 * Does NOT require exact coordinate equality. Evaluates proximity to the destination,
 * considers GPS accuracy, and requires consecutive reliable fixes within the arrival zone
 * before confirming arrival, preventing false positives from momentary GPS jumps.
 */
class ArrivalDetector(
    val arrivalRadiusMeters: Float = 20.0f,
    val requiredConsecutiveFixes: Int = 2
) {
    private var consecutiveArrivalCount = 0
    private var hasAnnouncedArrival = false

    /**
     * Evaluates current user location against the destination coordinate.
     * Returns true only once when arrival is confirmed.
     */
    fun checkArrival(
        currentLocation: CurrentLocation,
        destinationPoint: RoutePoint
    ): Boolean {
        if (hasAnnouncedArrival || !currentLocation.isValid || !destinationPoint.isValid) {
            return false
        }

        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            destinationPoint.latitude,
            destinationPoint.longitude
        )

        // Factor in GPS accuracy: effective arrival radius expands slightly if accuracy is acceptable
        // but caps so uncertain fixes (>25m accuracy) don't trigger false arrivals prematurely
        val effectiveRadius = if (currentLocation.accuracyMeters <= 15f) {
            arrivalRadiusMeters
        } else {
            (arrivalRadiusMeters - 5f).coerceAtLeast(10f)
        }

        if (distance <= effectiveRadius) {
            consecutiveArrivalCount++
        } else {
            consecutiveArrivalCount = 0
        }

        if (consecutiveArrivalCount >= requiredConsecutiveFixes) {
            hasAnnouncedArrival = true
            return true
        }

        return false
    }

    /**
     * Resets detector state for a new route.
     */
    fun reset() {
        consecutiveArrivalCount = 0
        hasAnnouncedArrival = false
    }

    fun hasArrived(): Boolean = hasAnnouncedArrival

    companion object {
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val earthRadius = 6371000.0 // meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadius * c).toFloat()
        }
    }
}

