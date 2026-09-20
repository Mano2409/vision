package com.visionnav.environment

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.TrackedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentUnderstandingEngineTest {

    private fun createTrackedObject(
        trackId: Int,
        clazz: DetectionClass,
        box: BoundingBox,
        confidence: Float = 0.85f,
        timestampMs: Long = 1000L
    ): TrackedObject {
        return TrackedObject(
            trackId = trackId,
            detectionClass = clazz,
            currentBox = box,
            currentConfidence = confidence,
            firstSeenMs = timestampMs,
            lastSeenMs = timestampMs,
            totalDetections = 3
        )
    }

    @Test
    fun test1_validSnapshot_correctlyPopulatesObjectsAndPathInfo() {
        val engine = EnvironmentUnderstandingEngine()

        // Center walking corridor object: Person in corridor
        val personBox = BoundingBox(left = 0.35f, top = 0.40f, right = 0.65f, bottom = 0.95f)
        val person = createTrackedObject(trackId = 1, clazz = DetectionClass.PERSON, box = personBox, confidence = 0.90f)

        val snapshot = engine.evaluateFrame(listOf(person), timestampMs = 1000L)

        assertEquals("Should have 1 visible object", 1, snapshot.visibleObjects.size)
        val envObj = snapshot.visibleObjects.first()
        assertEquals(1, envObj.objectId)
        assertEquals(EnvironmentCategory.PERSON, envObj.category)
        assertEquals(0.90f, envObj.confidence, 0.001f)
        assertTrue("Object should be in corridor", envObj.isInCorridor)
        assertTrue("Person in near/very near corridor is blocking path", envObj.isBlockingPath)
        assertFalse("Corridor should not be clear", snapshot.pathInformation.isCorridorClear)
        assertEquals(SceneCategory.OBSTRUCTED_PATH, snapshot.dominantScene)
    }

    @Test
    fun test2_staleSnapshot_returnsStaleWhenTimeoutExceeded() {
        val engine = EnvironmentUnderstandingEngine(freshnessTimeoutMs = 3000L)

        val box = BoundingBox(left = 0.1f, top = 0.5f, right = 0.3f, bottom = 0.8f)
        val car = createTrackedObject(trackId = 1, clazz = DetectionClass.CAR, box = box)

        engine.evaluateFrame(listOf(car), timestampMs = 10000L)

        // 1. Fresh check within timeout (at t=12000, age=2000ms <= 3000ms)
        val freshSnapshot = engine.getLatestSnapshot(currentTimeMs = 12000L)
        assertFalse("Snapshot should not be stale", freshSnapshot.isStale)
        assertTrue("isFresh() should return true", freshSnapshot.isFresh(currentTimeMs = 12000L))

        // 2. Stale check past timeout (at t=14000, age=4000ms > 3000ms)
        val staleSnapshot = engine.getLatestSnapshot(currentTimeMs = 14000L)
        assertTrue("Snapshot should be marked stale", staleSnapshot.isStale)
        assertFalse("isFresh() should return false", staleSnapshot.isFresh(currentTimeMs = 14000L))
        assertEquals("Dominant scene should be UNKNOWN when stale", SceneCategory.UNKNOWN, staleSnapshot.dominantScene)
        assertFalse("Corridor cannot be assumed clear when stale", staleSnapshot.pathInformation.isCorridorClear)
    }

    @Test
    fun test3_emptyEnvironment_reportsClearPath() {
        val engine = EnvironmentUnderstandingEngine()

        val snapshot = engine.evaluateFrame(emptyList(), timestampMs = 1000L)

        assertTrue("Visible objects must be empty", snapshot.visibleObjects.isEmpty())
        assertTrue("Corridor should be clear", snapshot.pathInformation.isCorridorClear)
        assertEquals(SceneCategory.CLEAR_PATH, snapshot.dominantScene)
        assertEquals(1.0f, snapshot.overallConfidence, 0.001f)
    }

    @Test
    fun test4_multipleObjects_categorizesBlockingVsUseful() {
        val engine = EnvironmentUnderstandingEngine()

        // 1. Blocking object: Large person in center corridor
        val blockingBox = BoundingBox(left = 0.35f, top = 0.40f, right = 0.65f, bottom = 0.95f)
        val blockingPerson = createTrackedObject(trackId = 1, clazz = DetectionClass.PERSON, box = blockingBox)

        // 2. Useful non-blocking object: Bicycle on sidewalk far left
        val sidewalkBox = BoundingBox(left = 0.02f, top = 0.60f, right = 0.18f, bottom = 0.85f)
        val sidewalkBicycle = createTrackedObject(trackId = 2, clazz = DetectionClass.BICYCLE, box = sidewalkBox)

        // 3. Useful non-blocking object: Car on right
        val carBox = BoundingBox(left = 0.80f, top = 0.50f, right = 0.98f, bottom = 0.80f)
        val roadsideCar = createTrackedObject(trackId = 3, clazz = DetectionClass.CAR, box = carBox)

        val snapshot = engine.evaluateFrame(listOf(blockingPerson, sidewalkBicycle, roadsideCar))

        assertEquals("Total visible objects should be 3", 3, snapshot.visibleObjects.size)
        assertEquals("Should have 1 blocking object", 1, snapshot.pathInformation.blockingObjects.size)
        assertEquals(1, snapshot.pathInformation.blockingObjects.first().objectId)
        assertEquals("Should have 2 nearby useful objects", 2, snapshot.nearbyUsefulObjects.size)
        assertFalse("Corridor should be obstructed", snapshot.pathInformation.isCorridorClear)
        assertNotNull("Nearest obstacle distance should be computed", snapshot.pathInformation.nearestObstacleDistance)
    }

    @Test
    fun test5_duplicateObjects_deduplicatesHighIoU() {
        val engine = EnvironmentUnderstandingEngine(duplicateIouThreshold = 0.70f)

        // Two overlapping bounding boxes representing the same object with different confidences
        val boxA = BoundingBox(left = 0.30f, top = 0.40f, right = 0.60f, bottom = 0.90f)
        val boxB = BoundingBox(left = 0.31f, top = 0.41f, right = 0.61f, bottom = 0.91f) // high IoU with boxA

        val track1 = createTrackedObject(trackId = 10, clazz = DetectionClass.CAR, box = boxA, confidence = 0.95f)
        val track2 = createTrackedObject(trackId = 11, clazz = DetectionClass.CAR, box = boxB, confidence = 0.70f)

        val snapshot = engine.evaluateFrame(listOf(track1, track2))

        assertEquals("Deduplication should keep only 1 object", 1, snapshot.visibleObjects.size)
        val kept = snapshot.visibleObjects.first()
        assertEquals("Should keep the higher confidence object", 10, kept.objectId)
        assertEquals(0.95f, kept.confidence, 0.001f)
    }

    @Test
    fun test6_invalidDetections_filtersCorruptedAndLowConfidence() {
        val engine = EnvironmentUnderstandingEngine(minConfidenceThreshold = 0.40f)

        // Invalid: Negative coordinate / area <= 0
        val invalidBox = BoundingBox(left = -0.1f, top = 0.0f, right = 0.5f, bottom = 0.5f)
        val invalidObj = createTrackedObject(trackId = 1, clazz = DetectionClass.PERSON, box = invalidBox, confidence = 0.90f)

        // Invalid: Confidence below min threshold (0.30 < 0.40)
        val lowConfBox = BoundingBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.6f)
        val lowConfObj = createTrackedObject(trackId = 2, clazz = DetectionClass.PERSON, box = lowConfBox, confidence = 0.30f)

        // Valid
        val validBox = BoundingBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.6f)
        val validObj = createTrackedObject(trackId = 3, clazz = DetectionClass.PERSON, box = validBox, confidence = 0.85f)

        val snapshot = engine.evaluateFrame(listOf(invalidObj, lowConfObj, validObj))

        assertEquals("Only the valid object should pass filtering", 1, snapshot.visibleObjects.size)
        assertEquals(3, snapshot.visibleObjects.first().objectId)
    }

    @Test
    fun test7_timestampHandling_preservesFrameTimestampAndComputesFreshness() {
        val engine = EnvironmentUnderstandingEngine()

        val box = BoundingBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.6f)
        val obj = createTrackedObject(trackId = 1, clazz = DetectionClass.PERSON, box = box)

        val frameTime = 987654321L
        val snapshot = engine.evaluateFrame(listOf(obj), timestampMs = frameTime)

        assertEquals("Frame timestamp must match input", frameTime, snapshot.timestampMs)
        assertEquals("Object timestamp must match frame timestamp", frameTime, snapshot.visibleObjects.first().timestampMs)

        // Fresh at frameTime + 1000ms
        assertTrue(snapshot.isFresh(currentTimeMs = frameTime + 1000L, timeoutMs = 3000L))
        // Stale at frameTime + 3001ms
        assertFalse(snapshot.isFresh(currentTimeMs = frameTime + 3001L, timeoutMs = 3000L))
    }

    @Test
    fun test8_confidenceHandling_preservesObjectConfidenceAndCalculatesOverall() {
        val engine = EnvironmentUnderstandingEngine()

        val box1 = BoundingBox(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.4f)
        val box2 = BoundingBox(left = 0.7f, top = 0.1f, right = 0.9f, bottom = 0.4f)

        val obj1 = createTrackedObject(trackId = 1, clazz = DetectionClass.PERSON, box = box1, confidence = 0.90f)
        val obj2 = createTrackedObject(trackId = 2, clazz = DetectionClass.CAR, box = box2, confidence = 0.80f)

        val snapshot = engine.evaluateFrame(listOf(obj1, obj2))

        assertEquals(0.90f, snapshot.visibleObjects[0].confidence, 0.001f)
        assertEquals(0.80f, snapshot.visibleObjects[1].confidence, 0.001f)
        assertEquals("Overall confidence should be average", 0.85f, snapshot.overallConfidence, 0.001f)
    }

    @Test
    fun test9_supportedCategories_onlyIncludesActualSupportedClasses() {
        // Supported classes must be true
        assertTrue(EnvironmentCategory.PERSON.isCurrentlySupported)
        assertTrue(EnvironmentCategory.BICYCLE.isCurrentlySupported)
        assertTrue(EnvironmentCategory.MOTORCYCLE.isCurrentlySupported)
        assertTrue(EnvironmentCategory.CAR.isCurrentlySupported)
        assertTrue(EnvironmentCategory.BUS.isCurrentlySupported)
        assertTrue(EnvironmentCategory.TRUCK.isCurrentlySupported)

        // Future/unsupported classes must be false
        assertFalse(EnvironmentCategory.POSSIBLE_DOOR.isCurrentlySupported)
        assertFalse(EnvironmentCategory.POSSIBLE_STAIRS.isCurrentlySupported)
        assertFalse(EnvironmentCategory.POSSIBLE_CROSSWALK.isCurrentlySupported)
        assertFalse(EnvironmentCategory.POSSIBLE_TRAFFIC_INFRASTRUCTURE.isCurrentlySupported)

        // Mapping from DetectionClass
        assertEquals(EnvironmentCategory.PERSON, EnvironmentCategory.fromDetectionClass(DetectionClass.PERSON))
        assertEquals(EnvironmentCategory.CAR, EnvironmentCategory.fromDetectionClass(DetectionClass.CAR))
        assertEquals(EnvironmentCategory.TRUCK, EnvironmentCategory.fromDetectionClass(DetectionClass.TRUCK))
    }
}

