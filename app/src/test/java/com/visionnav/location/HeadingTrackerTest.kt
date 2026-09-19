package com.visionnav.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HeadingTrackerTest {

    @Test
    fun `smoothAngle initial target initializes heading directly`() {
        val initial = HeadingTracker.smoothAngle(null, 120f, 0.20f)
        assertEquals(120f, initial, 0.01f)
    }

    @Test
    fun `TEST 5 - smoothAngle smooths small heading changes incrementally`() {
        var current = 90f // Facing East
        val target = 100f // Rotating clockwise

        // Apply smoothing 3 times
        current = HeadingTracker.smoothAngle(current, target, 0.20f)
        assertEquals(92f, current, 0.01f)

        current = HeadingTracker.smoothAngle(current, target, 0.20f)
        assertEquals(93.6f, current, 0.01f)

        current = HeadingTracker.smoothAngle(current, target, 0.20f)
        assertEquals(94.88f, current, 0.01f)

        // Continues moving smoothly towards target
        assertTrue(current > 90f && current < 100f)
    }

    @Test
    fun `TEST 6 - smoothAngle crossing 359 to 0 degrees does NOT spin 180 degrees backward`() {
        val current = 359f // Just West of North
        val target = 1f   // Just East of North

        // Naive average would yield (359 + 1) / 2 = 180° (South)!
        // Our circular smoothing must rotate through North (359 -> 359.4 -> 0.x)
        val smoothed = HeadingTracker.smoothAngle(current, target, 0.20f)

        // The delta across North is +2°. With alpha=0.2, smoothed should be 359 + 0.4 = 359.4°
        assertEquals(359.4f, smoothed, 0.01f)
        assertTrue("Angle should stay near North, but was $smoothed", smoothed > 358f || smoothed < 2f)
    }

    @Test
    fun `TEST 6b - smoothAngle crossing 0 to 359 degrees wraps smoothly counter-clockwise`() {
        val current = 1f   // Just East of North
        val target = 359f // Just West of North

        // The delta across North is -2°. With alpha=0.2, smoothed should be 1 - 0.4 = 0.6°
        val smoothed = HeadingTracker.smoothAngle(current, target, 0.20f)

        assertEquals(0.6f, smoothed, 0.01f)
        assertTrue("Angle should stay near North, but was $smoothed", smoothed in 0f..2f)
    }
}

