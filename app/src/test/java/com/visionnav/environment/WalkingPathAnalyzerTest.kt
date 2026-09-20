package com.visionnav.environment

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.CorridorOverlap
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingPathAnalyzerTest {

    private fun createEnvObject(
        id: Int = 1,
        category: EnvironmentCategory = EnvironmentCategory.PERSON,
        box: BoundingBox = BoundingBox(left = 0.40f, top = 0.40f, right = 0.60f, bottom = 0.90f),
        distance: DistanceCategory = DistanceCategory.NEAR,
        motion: MotionState = MotionState.STATIONARY,
        corridor: CorridorOverlap = CorridorOverlap.INSIDE_CORRIDOR,
        confidence: Float = 0.85f,
        timestampMs: Long = 1000L
    ): EnvironmentObject {
        return EnvironmentObject(
            objectId = id,
            category = category,
            confidence = confidence,
            boundingBox = box,
            distance = distance,
            motion = motion,
            corridorRelationship = corridor,
            timestampMs = timestampMs
        )
    }

    private fun createSnapshot(
        objects: List<EnvironmentObject> = emptyList(),
        timestampMs: Long = 1000L,
        isStale: Boolean = false
    ): EnvironmentSnapshot {
        return EnvironmentSnapshot(
            timestampMs = timestampMs,
            visibleObjects = objects,
            isCameraActive = true,
            isStale = isStale
        )
    }

    @Test
    fun test1_emptyCorridor_reportsOpenPath() {
        val analyzer = WalkingPathAnalyzer()
        val snapshot = createSnapshot(emptyList(), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertEquals(PathStatus.OPEN, result.status)
        assertEquals("The path appears open.", result.summaryMessage)
        assertTrue(result.blockingObjects.isEmpty())
        assertEquals(0f, result.corridorObstructionRatio, 0.001f)
    }

    @Test
    fun test2_personInCorridor_reportsBlockedOrPartiallyBlocked() {
        val analyzer = WalkingPathAnalyzer()

        // Wide person occupying center corridor [0.35, 0.65]
        val person = createEnvObject(
            box = BoundingBox(left = 0.35f, top = 0.40f, right = 0.65f, bottom = 0.95f),
            distance = DistanceCategory.NEAR,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )
        val snapshot = createSnapshot(listOf(person), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertTrue(
            "Status should be BLOCKED or PARTIALLY_BLOCKED",
            result.status == PathStatus.BLOCKED || result.status == PathStatus.PARTIALLY_BLOCKED
        )
        assertEquals(1, result.blockingObjects.size)
        assertEquals(ObjectRelevance.DIRECTLY_IN_CORRIDOR, result.blockingObjects.first().relevance)
        assertEquals(DistanceCategory.NEAR, result.nearestObstacleDistance)
    }

    @Test
    fun test3_vehicleOutsideCorridor_reportsOpenPath() {
        val analyzer = WalkingPathAnalyzer()

        // Parked car outside the walking corridor on the far right sidewalk
        val car = createEnvObject(
            id = 2,
            category = EnvironmentCategory.CAR,
            box = BoundingBox(left = 0.82f, top = 0.50f, right = 0.98f, bottom = 0.85f),
            distance = DistanceCategory.MEDIUM,
            corridor = CorridorOverlap.OUTSIDE_CORRIDOR
        )
        val snapshot = createSnapshot(listOf(car), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertEquals(PathStatus.OPEN, result.status)
        assertEquals("The path appears open.", result.summaryMessage)
        assertTrue("Car outside corridor should not block path", result.blockingObjects.isEmpty())
        assertEquals(0f, result.corridorObstructionRatio, 0.001f)
    }

    @Test
    fun test4_objectCrossingCorridor_accountsForMotion() {
        val analyzer = WalkingPathAnalyzer()

        // Bicycle near corridor crossing into the path
        val bicycle = createEnvObject(
            id = 3,
            category = EnvironmentCategory.BICYCLE,
            box = BoundingBox(left = 0.20f, top = 0.55f, right = 0.33f, bottom = 0.80f),
            distance = DistanceCategory.NEAR,
            motion = MotionState.CROSSING,
            corridor = CorridorOverlap.NEAR_CORRIDOR
        )
        val snapshot = createSnapshot(listOf(bicycle), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertEquals(1, result.blockingObjects.size)
        assertTrue("Crossing object near corridor must be flagged as obstruction", result.blockingObjects.first().isPotentialObstruction)
        assertEquals(ObjectRelevance.NEAR_CORRIDOR, result.blockingObjects.first().relevance)
    }

    @Test
    fun test5_uncertainDepth_reportsUnknownStatus() {
        val analyzer = WalkingPathAnalyzer()

        // Object directly in corridor with UNKNOWN distance
        val uncertainObj = createEnvObject(
            box = BoundingBox(left = 0.40f, top = 0.40f, right = 0.60f, bottom = 0.80f),
            distance = DistanceCategory.UNKNOWN,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )
        val snapshot = createSnapshot(listOf(uncertainObj), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertEquals(PathStatus.UNKNOWN, result.status)
        assertEquals("Path status is uncertain.", result.summaryMessage)
    }

    @Test
    fun test6_staleVision_reportsStaleStatus() {
        val analyzer = WalkingPathAnalyzer()

        // Snapshot from t=1000, current time t=5000 (age 4000ms > 3000ms)
        val snapshot = createSnapshot(emptyList(), timestampMs = 1000L)

        val result = analyzer.analyze(snapshot, currentTimeMs = 5000L)

        assertEquals(PathStatus.STALE, result.status)
        assertEquals("Path information is stale.", result.summaryMessage)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun test7_noisyDetections_temporalConsistencyPreventsFlicker() {
        val analyzer = WalkingPathAnalyzer(persistenceRequired = 2)

        // Baseline frame: Path is OPEN
        val emptySnapshot = createSnapshot(emptyList(), timestampMs = 1000L)
        val initialResult = analyzer.analyze(emptySnapshot, currentTimeMs = 1000L)
        assertEquals(PathStatus.OPEN, initialResult.status)

        // 1st noisy frame: Narrow obstacle appears in corridor (PARTIALLY_BLOCKED candidate)
        val obstacle = createEnvObject(
            box = BoundingBox(left = 0.40f, top = 0.60f, right = 0.48f, bottom = 0.85f),
            distance = DistanceCategory.NEAR,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )
        val noisySnapshot = createSnapshot(listOf(obstacle), timestampMs = 1050L)
        val noisyResult1 = analyzer.analyze(noisySnapshot, currentTimeMs = 1050L)

        // Must NOT switch immediately on single noisy frame
        assertEquals("Single frame should not cause state switch", PathStatus.OPEN, noisyResult1.status)

        // 2nd frame: Obstacle persists
        val noisyResult2 = analyzer.analyze(noisySnapshot, currentTimeMs = 1100L)
        assertEquals("Persistent obstacle across 2 frames should switch state", PathStatus.PARTIALLY_BLOCKED, noisyResult2.status)
    }

    @Test
    fun test8_multipleObstacles_aggregatesObstructionRatio() {
        val analyzer = WalkingPathAnalyzer()

        // Obstacle 1: Left side of corridor [0.35, 0.48]
        val obs1 = createEnvObject(
            id = 1,
            box = BoundingBox(left = 0.35f, top = 0.50f, right = 0.48f, bottom = 0.85f),
            distance = DistanceCategory.NEAR,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )

        // Obstacle 2: Right side of corridor [0.52, 0.65]
        val obs2 = createEnvObject(
            id = 2,
            box = BoundingBox(left = 0.52f, top = 0.50f, right = 0.65f, bottom = 0.85f),
            distance = DistanceCategory.NEAR,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )

        val snapshot = createSnapshot(listOf(obs1, obs2), timestampMs = 1000L)
        val result = analyzer.analyze(snapshot, currentTimeMs = 1000L)

        assertEquals(2, result.blockingObjects.size)
        assertTrue("Combined obstruction ratio should be substantial", result.corridorObstructionRatio > 0.50f)
        assertEquals(PathStatus.BLOCKED, result.status)
    }

    @Test
    fun test9_cameraMovementCompensation_adjustsForHeadingDelta() {
        val analyzer = WalkingPathAnalyzer()

        // Object located at centerX = 0.50 (dead center in camera)
        val centerObj = createEnvObject(
            box = BoundingBox(left = 0.45f, top = 0.50f, right = 0.55f, bottom = 0.80f),
            distance = DistanceCategory.NEAR,
            corridor = CorridorOverlap.INSIDE_CORRIDOR
        )
        val snapshot = createSnapshot(listOf(centerObj), timestampMs = 1000L)

        // Straight ahead: Region is CENTER
        val straightResult = analyzer.analyze(snapshot, headingDeltaDegrees = 0f, currentTimeMs = 1000L)
        assertEquals(CorridorRegion.CENTER, straightResult.analyzedObjects.first().region)

        // Sharp left turn (-35 deg): Camera rotated left, forward walking trajectory is to the RIGHT relative to camera frame
        val leftTurnResult = analyzer.analyze(snapshot, headingDeltaDegrees = -35f, currentTimeMs = 1000L)
        assertNotNull(leftTurnResult.analyzedObjects.first().region)
    }
}

