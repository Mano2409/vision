package com.visionnav.location

import android.location.Location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quality rating of the GPS fix.
 * Helps the decision layer distinguish between trustworthy and uncertain readings.
 */
enum class LocationConfidence {
    HIGH,       // <= 10m: Excellent, safe for turn maneuvers
    MODERATE,   // 10m - 25m: Usable for street-level guidance, caution on turns
    LOW         // > 25m: Uncertain, high multipath or indoors
}

/**
 * Core spatial coordinate model representing the user's real-time position.
 */
data class NavLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val confidence: LocationConfidence = LocationConfidence.LOW,
    val isOutlier: Boolean = false,
    val isAvailable: Boolean = false
) {
    /**
     * Calculates the great-circle distance in meters between this location and another coordinate.
     * Uses the Haversine formula for lightweight on-device computation without Android framework dependencies.
     */
    fun distanceTo(otherLatitude: Double, otherLongitude: Double): Float {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(otherLatitude - latitude)
        val dLon = Math.toRadians(otherLongitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(otherLatitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    fun distanceTo(other: NavLocation): Float = distanceTo(other.latitude, other.longitude)

    /**
     * Checks if coordinates fall within valid latitude [-90, +90] and longitude [-180, +180] bounds.
     */
    val isValidCoordinates: Boolean
        get() = latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !latitude.isNaN() &&
                !longitude.isNaN()

    /**
     * Converts to clean CurrentLocation domain model if location is available and valid.
     */
    fun toCurrentLocation(): CurrentLocation? {
        if (!isAvailable || !isValidCoordinates) return null
        return CurrentLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters ?: 0f,
            speedMetersPerSecond = speedMps,
            timestamp = timestamp
        )
    }

    companion object {
        val UNAVAILABLE = NavLocation(isAvailable = false)

        /**
         * Creates a NavLocation from an Android framework Location, classifying accuracy confidence
         * and detecting impossible walking speed jumps.
         */
        fun fromAndroidLocation(
            location: Location,
            previousLocation: NavLocation? = null
        ): NavLocation {
            val accuracy = if (location.hasAccuracy()) location.accuracy else null
            val speed = if (location.hasSpeed()) location.speed else null
            val bearing = if (location.hasBearing()) location.bearing else null

            // Confidence based on accuracy
            val confidence = when {
                accuracy == null -> LocationConfidence.LOW
                accuracy <= 10f -> LocationConfidence.HIGH
                accuracy <= 25f -> LocationConfidence.MODERATE
                else -> LocationConfidence.LOW
            }

            // Outlier / Impossible jump detection:
            // Typical pedestrian walking speed is 1.2 - 1.5 m/s. Running is 4 - 6 m/s.
            // If the position shifts > 60m in < 2 seconds without a reported vehicle speed,
            // flag as multipath GPS reflection anomaly.
            var isOutlier = false
            if (previousLocation != null && previousLocation.isAvailable) {
                val timeDeltaSec = (location.time - previousLocation.timestamp) / 1000.0
                if (timeDeltaSec in 0.1..5.0) {
                    val distance = previousLocation.distanceTo(location.latitude, location.longitude)
                    val impliedSpeed = distance / timeDeltaSec
                    if (impliedSpeed > 35.0) { // > 126 km/h jump while pedestrian
                        isOutlier = true
                    }
                }
            }

            return NavLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = accuracy,
                timestamp = location.time,
                speedMps = speed,
                bearingDegrees = bearing,
                confidence = confidence,
                isOutlier = isOutlier,
                isAvailable = true
            )
        }
    }
}

