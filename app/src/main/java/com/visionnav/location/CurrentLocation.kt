package com.visionnav.location

/**
 * Clean data model representing the user's current physical location obtained via FusedLocationProviderClient.
 *
 * All coordinates must be validated:
 * - latitude between -90.0 and +90.0
 * - longitude between -180.0 and +180.0
 */
data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Checks if coordinates fall within valid geographic bounds.
     */
    val isValid: Boolean
        get() = latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !latitude.isNaN() &&
                !longitude.isNaN() &&
                accuracyMeters >= 0f

    companion object {
        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0 &&
                    !latitude.isNaN() &&
                    !longitude.isNaN()
        }
    }
}
