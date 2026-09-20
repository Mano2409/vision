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

    companion object {
        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0 &&
                    !latitude.isNaN() &&
                    !longitude.isNaN()
        }
    }
}
