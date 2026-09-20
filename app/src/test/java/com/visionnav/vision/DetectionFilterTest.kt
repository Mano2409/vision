package com.visionnav.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectionFilterTest {

    private lateinit var filter: DetectionFilter

    @Before
    fun setUp() {
        filter = DetectionFilter(
            minConfidenceThreshold = 0.50f,
            highConfidenceImmediateThreshold = 0.85f,
            requiredConsistentFrames = 2,
            historyWindowMs = 1000L
        )
    }

    @Test
    fun testWeakConfidenceDetectionsAreDropped() {
        val weak = Detection(
            detectionClass = DetectionClass.CAR,
            confidence = 0.35f,
            boundingBox = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f),
            timestampMs = 1000L
        )

        val result = filter.filter(listOf(weak), 1000L)
        assertTrue("Detection below 0.50 should be dropped", result.isEmpty())
    }

    @Test
    fun testHighConfidenceDetectionsPassImmediately() {
        val strong = Detection(
            detectionClass = DetectionClass.CAR,
            confidence = 0.90f,
            boundingBox = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f),
            timestampMs = 1000L
        )

        val result = filter.filter(listOf(strong), 1000L)
        assertEquals(1, result.size)
        assertEquals(DetectionClass.CAR, result.first().detectionClass)
    }

    @Test
    fun testModerateConfidenceRequiresTemporalConsistency() {
        val box = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f)
        val frame1 = Detection(DetectionClass.PERSON, 0.65f, box, 1000L)
        val frame2 = Detection(DetectionClass.PERSON, 0.70f, box, 1100L)

        // Frame 1: Only 1 observation -> should not pass yet
        val result1 = filter.filter(listOf(frame1), 1000L)
        assertTrue("Single moderate detection should not pass immediately", result1.isEmpty())

        // Frame 2: Second consistent observation within window -> passes
        val result2 = filter.filter(listOf(frame2), 1100L)
        assertEquals("Consistent detection across 2 frames should pass", 1, result2.size)
    }

    @Test
    fun testExpiredHistoryDoesNotTriggerConsistency() {
        val box = BoundingBox(0.3f, 0.3f, 0.7f, 0.7f)
        val frame1 = Detection(DetectionClass.PERSON, 0.65f, box, 1000L)
        val frame2 = Detection(DetectionClass.PERSON, 0.70f, box, 2500L) // 1500ms later (> 1000ms window)

        filter.filter(listOf(frame1), 1000L)
        val result2 = filter.filter(listOf(frame2), 2500L)
        assertTrue("Expired detection should not count towards consistency", result2.isEmpty())
    }

    @Test
    fun testInvalidBoundingBoxesAreDropped() {
        val invalidBox = BoundingBox(0.8f, 0.8f, 0.2f, 0.2f) // left > right
        val det = Detection(DetectionClass.CAR, 0.95f, invalidBox, 1000L)

        val result = filter.filter(listOf(det), 1000L)
        assertTrue("Invalid bounding box should be dropped", result.isEmpty())
    }
}

