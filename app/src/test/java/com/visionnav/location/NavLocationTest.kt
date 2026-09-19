package com.visionnav.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLocationTest {

    @Test
    fun `TEST 3 - UNAVAILABLE location is flagged correctly`() {
        val loc = NavLocation.UNAVAILABLE
        assertFalse(loc.isAvailable)
        assertEquals(LocationConfidence.LOW, loc.confidence)
    }

    @Test
    fun `distanceTo calculates accurate real-world distance between coordinates`() {
        // Distance between Chennai Central (13.0827, 80.2707) and General Hospital (13.0810, 80.2790)
        // Expected distance is approximately ~920 meters
        val central = NavLocation(latitude = 13.0827, longitude = 80.2707, isAvailable = true)
        val hospital = NavLocation(latitude = 13.0810, longitude = 80.2790, isAvailable = true)

        val distance = central.distanceTo(hospital)
        assertTrue("Distance should be between 850m and 950m, was $distance", distance in 850f..950f)
    }

    @Test
    fun `TEST 4 - Poor accuracy and confidence level assignment`() {
        val highAcc = NavLocation(accuracyMeters = 5.0f, confidence = LocationConfidence.HIGH, isAvailable = true)
        val modAcc = NavLocation(accuracyMeters = 18.0f, confidence = LocationConfidence.MODERATE, isAvailable = true)
        val lowAcc = NavLocation(accuracyMeters = 40.0f, confidence = LocationConfidence.LOW, isAvailable = true)

        assertEquals(LocationConfidence.HIGH, highAcc.confidence)
        assertEquals(LocationConfidence.MODERATE, modAcc.confidence)
        assertEquals(LocationConfidence.LOW, lowAcc.confidence)
    }

    @Test
    fun `outlier flag distinguishes plausible pedestrian move from GPS jump`() {
        val normalStep = NavLocation(
            latitude = 13.0820,
            longitude = 80.2700,
            isOutlier = false,
            isAvailable = true
        )
        assertFalse(normalStep.isOutlier)

        val outlierJump = NavLocation(
            latitude = 13.1500,
            longitude = 80.3500,
            isOutlier = true,
            isAvailable = true
        )
        assertTrue(outlierJump.isOutlier)
    }
}

