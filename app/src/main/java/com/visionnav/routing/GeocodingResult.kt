package com.visionnav.routing

/**
 * Clean data model representing a geocoded destination candidate.
 * Shields the application UI and navigation layers from vendor-specific JSON schemas.
 */
data class GeocodingResult(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val addressSnippet: String? = null,
    val provider: String // "Photon", "Nominatim", or "SIMULATOR_MOCK"
) {
    /**
     * Converts to clean Destination domain model.
     * Returns null if coordinates or display name are invalid.
     */
    fun toDestination(): Destination? {
        if (!Destination.isValidCoordinate(latitude, longitude) || displayName.isBlank()) {
            return null
        }
        val source = when (provider.lowercase()) {
            "nominatim" -> GeocodingSource.NOMINATIM
            else -> GeocodingSource.PHOTON
        }
        return Destination(
            latitude = latitude,
            longitude = longitude,
            displayName = displayName,
            source = source
        )
    }
}

