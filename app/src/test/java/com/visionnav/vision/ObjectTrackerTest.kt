package com.visionnav.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObjectTrackerTest {

    private lateinit var tracker: ObjectTracker

    @Before
    fun setUp() {
        tracker = ObjectTracker(
            iouThreshold = 0.25f,
            maxCentroidDistance = 0.20f,
            trackTimeoutMs = 1000L
        )
    }

    @Test
    fun testTrackIdPersistsAcrossFrames() {
        val box1 = BoundingBox(0.3f, 0.3f, 0.6f, 0.7f)
        val box2 = BoundingBox(0.32f, 0.31f, 0.62f, 0.71f) // slight movement

        val det1 = Detection(detectionClass = DetectionClass.PERSON, confidence = 0.85f, boundingBox = box1, timestampMs = 1000L)
        val tracks1 = tracker.update(listOf(det1), 1000L)
        assertEquals(1, tracks1.size)
        val initialId = tracks1.first().trackId

        val det2 = Detection(detectionClass = DetectionClass.PERSON, confidence = 0.88f, boundingBox = box2, timestampMs = 1100L)
        val tracks2 = tracker.update(listOf(det2), 1100L)
        assertEquals(1, tracks2.size)
        assertEquals("Track ID should remain identical for tracked object", initialId, tracks2.first().trackId)
        assertEquals(2, tracks2.first().totalDetections)
    }

    @Test
    fun testDistinctObjectsReceiveDifferentTrackIds() {
        val personBox = BoundingBox(0.1f, 0.2f, 0.3f, 0.6f)
        val carBox = BoundingBox(0.6f, 0.4f, 0.9f, 0.8f)

        val det1 = Detection(DetectionClass.PERSON, 0.85f, personBox, 1000L)
        val det2 = Detection(DetectionClass.CAR, 0.90f, carBox, 1000L)

        val tracks = tracker.update(listOf(det1, det2), 1000L)
        assertEquals(2, tracks.size)
        assertTrue(tracks[0].trackId != tracks[1].trackId)
    }

    @Test
    fun testStaleTracksArePrunedAfterTimeout() {
        val box = BoundingBox(0.3f, 0.3f, 0.6f, 0.7f)
        val det = Detection(DetectionClass.PERSON, 0.85f, box, 1000L)

        tracker.update(listOf(det), 1000L)
        assertEquals(1, tracker.getActiveTracks().size)

        // 1500ms later with no detections in frame
        val tracksAfterTimeout = tracker.update(emptyList(), 2500L)
        assertTrue("Stale track should be pruned", tracksAfterTimeout.isEmpty())
    }

    @Test
    fun testVelocityCalculation() {
        val box1 = BoundingBox(0.4f, 0.3f, 0.6f, 0.5f) // centerX = 0.5, height = 0.2
        val box2 = BoundingBox(0.5f, 0.3f, 0.7f, 0.7f) // centerX = 0.6, height = 0.4 (moved right and expanded)

        val det1 = Detection(DetectionClass.CAR, 0.9f, box1, 1000L)
        val det2 = Detection(DetectionClass.CAR, 0.9f, box2, 2000L) // 1 second later

        tracker.update(listOf(det1), 1000L)
        val tracks = tracker.update(listOf(det2), 2000L)

        val track = tracks.first()
        val (dx, dHeight) = track.calculateVelocity(timeWindowMs = 2000L)

        assertEquals(0.1f, dx, 0.02f)
        assertEquals(0.2f, dHeight, 0.02f)
    }
}

