package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.routing.MockRouteProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReroutingEngineTest {

    @Test
    fun `TEST 23 - One noisy GPS point does not immediately trigger rerouting`() {
        val engine = ReroutingEngine(
            possiblyOffRouteThresholdMeters = 20.0f,
            offRouteThresholdMeters = 30.0f,
            requiredConsecutiveOffRouteFixes = 3
        )
        val route = MockRouteProvider.createMockPedestrianRoute()

        // Deviate 44 meters perpendicular to the north-south path
        val noisyPoint = CurrentLocation(
            latitude = route.points[0].latitude,
            longitude = route.points[0].longitude + 0.0004, // ~44m perpendicular offset
            accuracyMeters = 5.0f
        )

        val status = engine.evaluate(noisyPoint, route)
        // Must NOT confirm off route on 1st fix!
        assertNotEquals(OffRouteStatus.CONFIRMED_OFF_ROUTE, status)
        assertEquals(OffRouteStatus.POSSIBLY_OFF_ROUTE, status)
    }

    @Test
    fun `TEST 24 - Persistent off-route movement triggers confirmed rerouting`() {
        val engine = ReroutingEngine(
            possiblyOffRouteThresholdMeters = 20.0f,
            offRouteThresholdMeters = 30.0f,
            requiredConsecutiveOffRouteFixes = 3
        )
        val route = MockRouteProvider.createMockPedestrianRoute()

        val offRoutePoint = CurrentLocation(
            latitude = route.points[0].latitude,
            longitude = route.points[0].longitude + 0.00045, // ~50m perpendicular offset
            accuracyMeters = 5.0f
        )

        // Fix 1
        assertEquals(OffRouteStatus.POSSIBLY_OFF_ROUTE, engine.evaluate(offRoutePoint, route))
        // Fix 2
        assertEquals(OffRouteStatus.POSSIBLY_OFF_ROUTE, engine.evaluate(offRoutePoint, route))
        // Fix 3: Confirmation!
        assertEquals(OffRouteStatus.CONFIRMED_OFF_ROUTE, engine.evaluate(offRoutePoint, route))
    }

    @Test
    fun `TEST 25 - Poor GPS accuracy prevents false off-route declarations`() {
        val engine = ReroutingEngine(
            possiblyOffRouteThresholdMeters = 20.0f,
            offRouteThresholdMeters = 30.0f,
            requiredConsecutiveOffRouteFixes = 3
        )
        val route = MockRouteProvider.createMockPedestrianRoute()

        // 45m away but accuracy is 35m (very poor/multipath)
        val uncertainPoint = CurrentLocation(
            latitude = route.points[0].latitude + 0.0004,
            longitude = route.points[0].longitude,
            accuracyMeters = 35.0f
        )

        // Even with 3 fixes, poor accuracy must NOT confirm off route
        repeat(3) {
            val status = engine.evaluate(uncertainPoint, route)
            assertNotEquals(OffRouteStatus.CONFIRMED_OFF_ROUTE, status)
        }
    }
}

