package com.visionnav.location

/**
 * Structured domain errors for location tracking and sensor operations.
 */
sealed class LocationError(val message: String) {
    object LocationPermissionDenied : LocationError("Location permissions (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) not granted.")
    object LocationUnavailable : LocationError("Location services are disabled or temporarily unavailable.")
    object InvalidCoordinates : LocationError("Reported coordinates are out of valid bounds [-90, +90] / [-180, +180].")
}
