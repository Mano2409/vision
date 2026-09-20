package com.visionnav.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLocationTest {

    @Test
    fun `TEST 1 - Valid latitude accepted within bounds`() {
        val loc = CurrentLocation(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracyMeters = 8.5f,
            timestamp = 1000L
        )
        assertTrue("Valid latitude must be accepted", loc.isValid)
        assertTrue(CurrentLocation.isValidCoordinate(loc.latitude, loc.longitude))
    }

    @Test
    fun `TEST 2 - Valid longitude accepted within bounds`() {
        val loc = CurrentLocation(
            latitude = -33.8688,
            longitude = 151.2093,
            accuracyMeters = 5.0f,
            timestamp = 1000L
        )
        assertTrue("Valid longitude must be accepted", loc.isValid)
        assertTrue(CurrentLocation.isValidCoordinate(loc.latitude, loc.longitude))
    }

    @Test
    fun `TEST 3 - Invalid latitude rejected outside bounds`() {
        // Latitude < -90 or > 90 must be rejected
        val tooHigh = CurrentLocation(latitude = 91.0, longitude = 0.0, accuracyMeters = 5f)
        val tooLow = CurrentLocation(latitude = -90.001, longitude = 0.0, accuracyMeters = 5f)
        val nanLat = CurrentLocation(latitude = Double.NaN, longitude = 0.0, accuracyMeters = 5f)

        assertFalse(tooHigh.isValid)
        assertFalse(tooLow.isValid)
        assertFalse(nanLat.isValid)
        assertFalse(CurrentLocation.isValidCoordinate(91.0, 0.0))
        assertFalse(CurrentLocation.isValidCoordinate(-90.001, 0.0))
    }

    @Test
    fun `TEST 4 - Invalid longitude rejected outside bounds`() {
        // Longitude < -180 or > 180 must be rejected
        val tooHigh = CurrentLocation(latitude = 0.0, longitude = 180.1, accuracyMeters = 5f)
        val tooLow = CurrentLocation(latitude = 0.0, longitude = -181.0, accuracyMeters = 5f)
        val nanLon = CurrentLocation(latitude = 0.0, longitude = Double.NaN, accuracyMeters = 5f)

        assertFalse(tooHigh.isValid)
        assertFalse(tooLow.isValid)
        assertFalse(nanLon.isValid)
        assertFalse(CurrentLocation.isValidCoordinate(0.0, 180.1))
        assertFalse(CurrentLocation.isValidCoordinate(0.0, -181.0))
    }

    @Test
    fun `TEST 5 - Permission denied handled cleanly without crashing`() {
        val permissionError = LocationError.LocationPermissionDenied
        assertNotNull(permissionError.message)
        assertTrue(permissionError.message.contains("ACCESS_FINE_LOCATION"))

        // NavLocation UNAVAILABLE state represents denied/unavailable location
        val loc = NavLocation.UNAVAILABLE
        assertFalse(loc.isAvailable)
        assertNull(loc.toCurrentLocation())
    }

    @Test
    fun `TEST 6 - Location services disabled or unavailable handled cleanly`() {
        val unavailableError = LocationError.LocationUnavailable
        assertNotNull(unavailableError.message)

        val loc = NavLocation(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracyMeters = null,
            isAvailable = false
        )
        assertFalse(loc.isAvailable)
        assertNull("Unavailable location must not convert to CurrentLocation", loc.toCurrentLocation())
    }

    @Test
    fun `TEST 7 - Reported accuracy preserved without alteration or fabrication`() {
        val rawAccuracy = 8.35f
        val loc = NavLocation(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracyMeters = rawAccuracy,
            isAvailable = true
        )

        assertEquals("Accuracy must be preserved exactly as reported by Android", rawAccuracy, loc.accuracyMeters)
        val current = loc.toCurrentLocation()
        assertNotNull(current)
        assertEquals(rawAccuracy, current!!.accuracyMeters)
    }

    @Test
    fun `distanceTo calculates accurate distance between coordinates`() {
        val central = NavLocation(latitude = 13.0827, longitude = 80.2707, isAvailable = true)
        val hospital = NavLocation(latitude = 13.0810, longitude = 80.2790, isAvailable = true)

        val distance = central.distanceTo(hospital)
        assertTrue("Distance should be between 850m and 950m, was $distance", distance in 850f..950f)
    }
}
