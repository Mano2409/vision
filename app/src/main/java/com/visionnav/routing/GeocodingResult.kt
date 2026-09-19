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
)

