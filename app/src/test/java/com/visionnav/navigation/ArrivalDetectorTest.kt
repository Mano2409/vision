package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.routing.RoutePoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDetectorTest {

    @Test
    fun `TEST 28 - Exact coordinate equality is not required for arrival`() {
        val detector = ArrivalDetector(arrivalRadiusMeters = 20.0f, requiredConsecutiveFixes = 2)
        val destination = RoutePoint(13.0840, 80.2730)

        // 10 meters away from destination (not equal!)
        val nearLocation = CurrentLocation(
            latitude = 13.0840 + 0.00008, // ~8.9m away
            longitude = 80.2730,
            accuracyMeters = 5.0f
        )

        detector.checkArrival(nearLocation, destination) // fix 1
        val arrived = detector.checkArrival(nearLocation, destination) // fix 2

        assertTrue("Arrival confirmed within proximity zone without exact coordinate match", arrived)
    }

    @Test
    fun `TEST 29 - Reliable destination proximity triggers arrival`() {
        val detector = ArrivalDetector(arrivalRadiusMeters = 20.0f, requiredConsecutiveFixes = 2)
        val destination = RoutePoint(13.0840, 80.2730)

        val loc = CurrentLocation(latitude = 13.0840, longitude = 80.2730, accuracyMeters = 3.0f)

        assertFalse(detector.checkArrival(loc, destination)) // 1st fix
        assertTrue(detector.checkArrival(loc, destination))  // 2nd fix
    }

    @Test
    fun `TEST 30 - GPS noise does not immediately trigger false arrival`() {
        val detector = ArrivalDetector(arrivalRadiusMeters = 20.0f, requiredConsecutiveFixes = 2)
        val destination = RoutePoint(13.0840, 80.2730)

        // 1 momentary jump into arrival zone
        val jumpLoc = CurrentLocation(latitude = 13.0840, longitude = 80.2730, accuracyMeters = 5.0f)
        assertFalse("Single fix should not trigger arrival", detector.checkArrival(jumpLoc, destination))

        // Next fix is 100m away
        val outsideLoc = CurrentLocation(latitude = 13.0830, longitude = 80.2730, accuracyMeters = 5.0f)
        assertFalse(detector.checkArrival(outsideLoc, destination))
        assertFalse(detector.hasArrived())
    }

    @Test
    fun `TEST 31 - Arrival announcement occurs once and does not repeat`() {
        val detector = ArrivalDetector(arrivalRadiusMeters = 20.0f, requiredConsecutiveFixes = 2)
        val destination = RoutePoint(13.0840, 80.2730)
        val loc = CurrentLocation(latitude = 13.0840, longitude = 80.2730, accuracyMeters = 3.0f)

        detector.checkArrival(loc, destination)
        val firstArrival = detector.checkArrival(loc, destination)
        assertTrue(firstArrival)

        // 3rd fix at destination must return false to prevent duplicate announcement
        val secondArrival = detector.checkArrival(loc, destination)
        assertFalse("Arrival must not repeat once triggered", secondArrival)
    }
}

