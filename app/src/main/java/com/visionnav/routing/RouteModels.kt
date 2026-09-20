package com.visionnav.routing

/**
 * High-precision coordinate along a route path.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !latitude.isNaN() &&
                !longitude.isNaN()
}

/**
 * Standardized pedestrian maneuver directions.
 */
enum class ManeuverType {
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    U_TURN,
    DESTINATION
}

/**
 * Clean internal model for turn-by-turn pedestrian maneuvers.
 */
data class ManeuverInstruction(
    val id: String,
    val type: ManeuverType,
    val coordinate: RoutePoint,
    val distanceFromRouteStartMeters: Int,
    val distanceMeters: Int,
    val streetName: String,
    val instructionText: String,
    val maneuverIndex: Int
)

/**
 * Concise summary of the calculated route for blind and visually impaired pedestrians.
 */
data class RouteSummary(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val text: String
) {
    companion object {
        fun format(distanceMeters: Int, durationSeconds: Int): RouteSummary {
            val distKm = distanceMeters / 1000.0
            val minutes = (durationSeconds / 60).coerceAtLeast(1)
            val distStr = if (distanceMeters >= 1000) {
                String.format("%.1f kilometers", distKm)
            } else {
                "$distanceMeters meters"
            }
            val text = "Your route is $distStr and approximately $minutes minutes. Say start navigation to begin."
            return RouteSummary(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                text = text
            )
        }
    }
}

/**
 * Full pedestrian navigation route model containing ordered geometry points,
 * step-by-step maneuvers, and metadata.
 */
data class NavigationRoute(
    val id: String,
    val points: List<RoutePoint>,
    val maneuvers: List<ManeuverInstruction>,
    val totalDistanceMeters: Int,
    val estimatedDurationSeconds: Int,
    val summary: RouteSummary,
    val provider: String = "Valhalla"
)

/**
 * Categorized error types for routing operations.
 */
enum class RoutingErrorType {
    NETWORK_ERROR,
    TIMEOUT,
    SERVER_ERROR,
    MALFORMED_RESPONSE,
    EMPTY_ROUTE,
    INVALID_COORDINATES
}

/**
 * Domain exception for route calculations.
 */
class RoutingException(
    val errorType: RoutingErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

