package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.routing.NavigationRoute
import com.visionnav.routing.RoutePoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Status of the user's adherence to the active pedestrian route.
 */
enum class OffRouteStatus {
    ON_ROUTE,
    POSSIBLY_OFF_ROUTE,
    CONFIRMED_OFF_ROUTE
}

/**
 * Monitors pedestrian position along the active route and triggers
 * debounced off-route alerts and recalculation.
 *
 * Prevents false rerouting from single noisy GPS points by factoring in
 * reported GPS accuracy and requiring consecutive persistence.
 */
class ReroutingEngine(
    val possiblyOffRouteThresholdMeters: Float = 20.0f,
    val offRouteThresholdMeters: Float = 30.0f,
    val onRouteRecoveryThresholdMeters: Float = 12.0f,
    val requiredConsecutiveOffRouteFixes: Int = 3
) {
    private var consecutiveOffRouteCount = 0
    private var consecutivePossibleCount = 0

    /**
     * Evaluates current user location against the active route geometry.
     * Considers GPS accuracy to prevent triggering during high multipath or poor signals.
     */
    fun evaluate(currentLocation: CurrentLocation, route: NavigationRoute): OffRouteStatus {
        if (!currentLocation.isValid || route.points.size < 2) {
            return OffRouteStatus.ON_ROUTE
        }

        val minDistance = calculateMinDistanceToRoute(currentLocation, route.points)

        // If GPS accuracy is worse than the observed distance, the location is too noisy to confirm divergence
        val isAccuracyReliable = currentLocation.accuracyMeters <= 25.0f

        if (minDistance > offRouteThresholdMeters && isAccuracyReliable) {
            consecutiveOffRouteCount++
            consecutivePossibleCount++
            if (consecutiveOffRouteCount >= requiredConsecutiveOffRouteFixes) {
                return OffRouteStatus.CONFIRMED_OFF_ROUTE
            }
            return OffRouteStatus.POSSIBLY_OFF_ROUTE
        } else if (minDistance > possiblyOffRouteThresholdMeters) {
            consecutivePossibleCount++
            consecutiveOffRouteCount = 0
            return OffRouteStatus.POSSIBLY_OFF_ROUTE
        } else if (minDistance <= onRouteRecoveryThresholdMeters) {
            consecutiveOffRouteCount = 0
            consecutivePossibleCount = 0
            return OffRouteStatus.ON_ROUTE
        }

        return if (consecutiveOffRouteCount >= requiredConsecutiveOffRouteFixes) {
            OffRouteStatus.CONFIRMED_OFF_ROUTE
        } else if (consecutivePossibleCount > 0) {
            OffRouteStatus.POSSIBLY_OFF_ROUTE
        } else {
            OffRouteStatus.ON_ROUTE
        }
    }

    /**
     * Resets the debounce counter after rerouting or route update.
     */
    fun reset() {
        consecutiveOffRouteCount = 0
        consecutivePossibleCount = 0
    }

    /**
     * Calculates the minimum perpendicular distance in meters from the user's location
     * to any line segment in the route polyline.
     */
    fun calculateMinDistanceToRoute(location: CurrentLocation, points: List<RoutePoint>): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) {
            return ArrivalDetector.calculateDistance(
                location.latitude,
                location.longitude,
                points[0].latitude,
                points[0].longitude
            )
        }

        var minDistance = Float.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val dist = distanceToSegment(location, points[i], points[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    /**
     * Calculates the distance from point P to line segment AB in meters
     * using equirectangular planar projection for fast on-device pedestrian math.
     */
    fun distanceToSegment(p: CurrentLocation, a: RoutePoint, b: RoutePoint): Float {
        val latMid = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val kx = 111320.0 * cos(latMid) // meters per degree lon
        val ky = 110540.0              // meters per degree lat

        val px = p.longitude * kx
        val py = p.latitude * ky
        val ax = a.longitude * kx
        val ay = a.latitude * ky
        val bx = b.longitude * kx
        val by = b.latitude * ky

        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0.0) {
            return ArrivalDetector.calculateDistance(p.latitude, p.longitude, a.latitude, a.longitude)
        }

        // Project point P onto line segment AB clamped to [0, 1]
        val t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / lengthSq))
        val projX = ax + t * dx
        val projY = ay + t * dy

        val distX = px - projX
        val distY = py - projY
        return sqrt(distX * distX + distY * distY).toFloat()
    }
}

