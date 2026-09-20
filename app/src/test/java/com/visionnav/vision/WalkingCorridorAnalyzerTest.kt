package com.visionnav.vision

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WalkingCorridorAnalyzerTest {

    private lateinit var analyzer: WalkingCorridorAnalyzer

    @Before
    fun setUp() {
        analyzer = WalkingCorridorAnalyzer(
            corridorLeftTop = 0.35f,
            corridorRightTop = 0.65f,
            corridorLeftBottom = 0.25f,
            corridorRightBottom = 0.75f,
            nearBufferWidth = 0.12f
        )
    }

    @Test
    fun testObjectCenteredInCorridorIsInside() {
        val centerBox = BoundingBox(0.40f, 0.40f, 0.60f, 0.80f)
        assertEquals(CorridorOverlap.INSIDE_CORRIDOR, analyzer.evaluate(centerBox))
    }

    @Test
    fun testObjectInNearFlankIsNearCorridor() {
        // Flank on left: buffer extends to 0.25 - 0.12 = 0.13 at bottom
        val nearLeftBox = BoundingBox(0.14f, 0.40f, 0.23f, 0.80f)
        assertEquals(CorridorOverlap.NEAR_CORRIDOR, analyzer.evaluate(nearLeftBox))

        // Flank on right: buffer extends to 0.75 + 0.12 = 0.87 at bottom
        val nearRightBox = BoundingBox(0.77f, 0.40f, 0.85f, 0.80f)
        assertEquals(CorridorOverlap.NEAR_CORRIDOR, analyzer.evaluate(nearRightBox))
    }

    @Test
    fun testObjectFarToSideIsOutsideCorridor() {
        // Far left (e.g. parked car at curb)
        val farLeftBox = BoundingBox(0.02f, 0.40f, 0.10f, 0.80f)
        assertEquals(CorridorOverlap.OUTSIDE_CORRIDOR, analyzer.evaluate(farLeftBox))

        // Far right
        val farRightBox = BoundingBox(0.90f, 0.40f, 0.98f, 0.80f)
        assertEquals(CorridorOverlap.OUTSIDE_CORRIDOR, analyzer.evaluate(farRightBox))
    }
}

