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
    val provider: String, // "Photon", "Nominatim", or "SIMULATOR_MOCK"
    val state: String? = null,
    val country: String? = null,
    val distanceMeters: Double? = null
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

    /**
     * Returns a copy of this result with distance calculated relative to user's location.
     */
    fun withDistanceTo(userLat: Double, userLon: Double): GeocodingResult {
        val dist = calculateDistance(userLat, userLon, latitude, longitude)
        return copy(distanceMeters = dist)
    }

    companion object {
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadius = 6371000.0 // meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return earthRadius * c
        }
    }
}

