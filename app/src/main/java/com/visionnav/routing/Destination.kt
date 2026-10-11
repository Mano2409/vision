package com.visionnav.routing

/**
 * Provenance source of the geocoding resolution.
 */
enum class GeocodingSource {
    PHOTON,
    NOMINATIM
}

/**
 * Clean domain model for resolved destinations.
 * Shields application layers from vendor-specific JSON formats.
 */
data class Destination(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val source: GeocodingSource
) {
    /**
     * Validates that coordinates and name are non-empty and within geographic bounds.
     */
    val isValid: Boolean
        get() = latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !latitude.isNaN() &&
                !longitude.isNaN() &&
                displayName.isNotBlank()

    /**
     * Calculates distance from an origin coordinate to this destination in meters.
     */
    fun distanceTo(originLat: Double, originLon: Double): Double {
        return GeocodingResult.calculateDistance(originLat, originLon, latitude, longitude)
    }

    /**
     * Checks if this destination is reasonably close to the origin.
     * Prevents wild cross-continental routing mistakes (e.g. Geneva when user is in India).
     */
    fun isReasonableForOrigin(originLat: Double, originLon: Double, maxDistanceMeters: Double = 1_000_000.0): Boolean {
        if (!isValid || !isValidCoordinate(originLat, originLon)) return false
        return distanceTo(originLat, originLon) <= maxDistanceMeters
    }

    companion object {
        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0 &&
                    !latitude.isNaN() &&
                    !longitude.isNaN()
        }
    }
}
