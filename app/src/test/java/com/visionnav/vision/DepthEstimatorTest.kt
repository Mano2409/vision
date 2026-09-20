package com.visionnav.vision

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DepthEstimatorTest {

    private lateinit var depthEstimator: DepthEstimator

    @Before
    fun setUp() {
        depthEstimator = DepthEstimator()
    }

    private fun createTrackedObject(
        clazz: DetectionClass,
        box: BoundingBox,
        confidence: Float = 0.85f
    ): TrackedObject {
        return TrackedObject(
            trackId = 1,
            detectionClass = clazz,
            currentBox = box,
            currentConfidence = confidence,
            firstSeenMs = 1000L,
            lastSeenMs = 1000L,
            totalDetections = 1
        )
    }

    @Test
    fun testPersonDistanceCategories() {
        // Person tall (height 0.70) -> VERY_NEAR (< 2m)
        val criticalPerson = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.3f, 0.15f, 0.6f, 0.85f))
        assertEquals(DistanceCategory.VERY_NEAR, depthEstimator.estimate(criticalPerson).category)

        // Person medium height (height 0.45) -> NEAR (2-5m)
        val nearPerson = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.35f, 0.30f, 0.55f, 0.75f))
        assertEquals(DistanceCategory.NEAR, depthEstimator.estimate(nearPerson).category)

        // Person small (height 0.20) -> MEDIUM (5-10m)
        val medPerson = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.40f, 0.40f, 0.50f, 0.60f))
        assertEquals(DistanceCategory.MEDIUM, depthEstimator.estimate(medPerson).category)

        // Person very small (height 0.08) -> FAR (> 10m)
        val farPerson = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.45f, 0.45f, 0.49f, 0.53f))
        assertEquals(DistanceCategory.FAR, depthEstimator.estimate(farPerson).category)
    }

    @Test
    fun testVehicleDistanceCategories() {
        // Massive car box (width 0.65, height 0.55) -> VERY_NEAR
        val criticalCar = createTrackedObject(DetectionClass.CAR, BoundingBox(0.15f, 0.30f, 0.80f, 0.85f))
        assertEquals(DistanceCategory.VERY_NEAR, depthEstimator.estimate(criticalCar).category)

        // Near car (width 0.40, height 0.30) -> NEAR
        val nearCar = createTrackedObject(DetectionClass.CAR, BoundingBox(0.30f, 0.40f, 0.70f, 0.70f))
        assertEquals(DistanceCategory.NEAR, depthEstimator.estimate(nearCar).category)
    }

    @Test
    fun testLowConfidenceOrTinyBoxReturnsUnknown() {
        // Low confidence (0.25 < 0.40) -> UNKNOWN
        val weak = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.3f, 0.3f, 0.6f, 0.7f), confidence = 0.25f)
        assertEquals(DistanceCategory.UNKNOWN, depthEstimator.estimate(weak).category)

        // Tiny box height (< 0.03) -> UNKNOWN
        val tiny = createTrackedObject(DetectionClass.PERSON, BoundingBox(0.45f, 0.45f, 0.47f, 0.46f))
        assertEquals(DistanceCategory.UNKNOWN, depthEstimator.estimate(tiny).category)
    }
}

