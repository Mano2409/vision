package com.visionnav.vision

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MotionEstimatorTest {

    private lateinit var motionEstimator: MotionEstimator

    @Before
    fun setUp() {
        motionEstimator = MotionEstimator()
    }

    private fun buildTrack(
        boxes: List<Pair<Long, BoundingBox>>,
        clazz: DetectionClass = DetectionClass.CAR
    ): TrackedObject {
        val last = boxes.last()
        return TrackedObject(
            trackId = 1,
            detectionClass = clazz,
            currentBox = last.second,
            currentConfidence = 0.90f,
            firstSeenMs = boxes.first().first,
            lastSeenMs = last.first,
            totalDetections = boxes.size,
            boxHistory = boxes.toMutableList()
        )
    }

    @Test
    fun testApproachingObjectDetectedViaExpansion() {
        // Object grows from height 0.30 to height 0.50 in 500ms
        val box1 = BoundingBox(0.4f, 0.4f, 0.6f, 0.7f) // height = 0.30
        val box2 = BoundingBox(0.3f, 0.3f, 0.7f, 0.8f) // height = 0.50

        val track = buildTrack(listOf(Pair(1000L, box1), Pair(1500L, box2)))
        val estimate = motionEstimator.estimate(track)

        assertEquals(MotionState.APPROACHING, estimate.state)
    }

    @Test
    fun testCrossingObjectDetectedViaLateralVelocity() {
        // Object translates laterally from left (centerX = 0.20) to right (centerX = 0.60) in 500ms
        val box1 = BoundingBox(0.1f, 0.4f, 0.3f, 0.7f)
        val box2 = BoundingBox(0.5f, 0.4f, 0.7f, 0.7f)

        val track = buildTrack(listOf(Pair(1000L, box1), Pair(1500L, box2)), DetectionClass.BICYCLE)
        val estimate = motionEstimator.estimate(track)

        assertEquals(MotionState.CROSSING, estimate.state)
    }

    @Test
    fun testMovingAwayObjectDetectedViaShrink() {
        // Object shrinks from height 0.60 to height 0.30 in 500ms
        val box1 = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f)
        val box2 = BoundingBox(0.35f, 0.35f, 0.65f, 0.65f)

        val track = buildTrack(listOf(Pair(1000L, box1), Pair(1500L, box2)))
        val estimate = motionEstimator.estimate(track)

        assertEquals(MotionState.MOVING_AWAY, estimate.state)
    }

    @Test
    fun testStationaryObjectDetectedWhenNoChange() {
        val box = BoundingBox(0.3f, 0.4f, 0.7f, 0.7f)
        val track = buildTrack(listOf(Pair(1000L, box), Pair(1500L, box)))
        val estimate = motionEstimator.estimate(track)

        assertEquals(MotionState.STATIONARY, estimate.state)
    }

    @Test
    fun testInsufficientHistoryReturnsUnknown() {
        val box = BoundingBox(0.3f, 0.4f, 0.7f, 0.7f)
        val track = buildTrack(listOf(Pair(1000L, box))) // Only 1 observation

        val estimate = motionEstimator.estimate(track)
        assertEquals(MotionState.UNKNOWN, estimate.state)
    }
}

