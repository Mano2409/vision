package com.visionnav.environment

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DistanceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecializedEnvironmentDetectorTest {

    private fun createCandidate(
        type: SpecializedType,
        confidence: Float = 0.85f,
        box: BoundingBox = BoundingBox(left = 0.40f, top = 0.20f, right = 0.60f, bottom = 0.80f),
        timestampMs: Long = 1000L
    ): SpecializedCandidate {
        return SpecializedCandidate(
            type = type,
            confidence = confidence,
            boundingBox = box,
            timestampMs = timestampMs
        )
    }

    @Test
    fun test1_detectionFiltering_dropsLowConfidenceAndInvalidBoxes() {
        val detector = SpecializedEnvironmentDetector(minConfidenceThreshold = 0.50f)

        // 1. Invalid box: negative left
        val invalidBox = BoundingBox(left = -0.1f, top = 0.2f, right = 0.5f, bottom = 0.8f)
        val invalidCandidate = createCandidate(SpecializedType.DOOR, confidence = 0.90f, box = invalidBox)

        // 2. Low confidence: 0.35 < 0.50
        val lowConfCandidate = createCandidate(SpecializedType.DOOR, confidence = 0.35f)

        // 3. Valid candidate
        val validCandidate = createCandidate(SpecializedType.DOOR, confidence = 0.80f)

        val results = detector.updateCandidates(listOf(invalidCandidate, lowConfCandidate, validCandidate))

        assertEquals("Only 1 valid candidate should pass filtering", 1, results.size)
        assertEquals(SpecializedType.DOOR, results.first().type)
        assertEquals(0.80f, results.first().confidence, 0.001f)
    }

    @Test
    fun test2_staleCandidates_rejectedByFreshnessCheck() {
        val detector = SpecializedEnvironmentDetector(freshnessTimeoutMs = 3000L)

        val candidate = createCandidate(SpecializedType.ENTRANCE, timestampMs = 1000L)
        detector.updateCandidates(listOf(candidate), timestampMs = 1000L)

        // Fresh at t=2500 (age 1500ms <= 3000ms)
        val freshResult = detector.findNearestEntrance(currentTimeMs = 2500L)
        assertTrue(freshResult.isFresh)
        assertNotNull(freshResult.candidate)

        // Stale at t=4500 (age 3500ms > 3000ms)
        val staleResult = detector.findNearestEntrance(currentTimeMs = 4500L)
        assertFalse(staleResult.isFresh)
        assertNull(staleResult.candidate)
        assertEquals("No entrance detected nearby.", staleResult.spokenDescription)
    }

    @Test
    fun test3_directionClassification_correctlyAssignsLeftCenterRight() {
        val detector = SpecializedEnvironmentDetector()

        // Left box: centerX = 0.20 < 0.35
        val leftCandidate = createCandidate(
            type = SpecializedType.DOOR,
            box = BoundingBox(left = 0.10f, top = 0.20f, right = 0.30f, bottom = 0.80f)
        )

        // Center box: centerX = 0.50 (in [0.35, 0.65])
        val centerCandidate = createCandidate(
            type = SpecializedType.DOOR,
            box = BoundingBox(left = 0.40f, top = 0.20f, right = 0.60f, bottom = 0.80f)
        )

        // Right box: centerX = 0.80 > 0.65
        val rightCandidate = createCandidate(
            type = SpecializedType.DOOR,
            box = BoundingBox(left = 0.70f, top = 0.20f, right = 0.90f, bottom = 0.80f)
        )

        val results = detector.updateCandidates(listOf(leftCandidate, centerCandidate, rightCandidate))

        assertEquals(ScreenDirection.LEFT, results[0].coarsePosition)
        assertEquals(ScreenDirection.CENTER, results[1].coarsePosition)
        assertEquals(ScreenDirection.RIGHT, results[2].coarsePosition)
    }

    @Test
    fun test4_lowConfidence_assignsUnknownDirection() {
        val detector = SpecializedEnvironmentDetector(
            minConfidenceThreshold = 0.50f,
            directionConfidenceThreshold = 0.65f
        )

        // Confidence 0.55 passes min threshold (0.50), but falls below direction threshold (0.65)
        val candidate = createCandidate(
            type = SpecializedType.POSSIBLE_ENTRANCE,
            confidence = 0.55f,
            box = BoundingBox(left = 0.70f, top = 0.20f, right = 0.90f, bottom = 0.80f)
        )

        val results = detector.updateCandidates(listOf(candidate))

        assertEquals(1, results.size)
        assertEquals("Direction should be UNKNOWN when confidence is insufficient", ScreenDirection.UNKNOWN, results.first().coarsePosition)
    }

    @Test
    fun test5_entranceAwareness_findNearestEntranceSelectsClosest() {
        val detector = SpecializedEnvironmentDetector()

        // Far entrance on left (height 0.10 -> FAR)
        val farEntrance = createCandidate(
            type = SpecializedType.DOOR,
            confidence = 0.90f,
            box = BoundingBox(left = 0.10f, top = 0.40f, right = 0.25f, bottom = 0.50f)
        )

        // Near entrance on right (height 0.45 -> NEAR)
        val nearEntrance = createCandidate(
            type = SpecializedType.ENTRANCE,
            confidence = 0.85f,
            box = BoundingBox(left = 0.70f, top = 0.30f, right = 0.90f, bottom = 0.75f)
        )

        detector.updateCandidates(listOf(farEntrance, nearEntrance), timestampMs = 1000L)

        val result = detector.findNearestEntrance(currentTimeMs = 1000L)

        assertNotNull(result.candidate)
        assertEquals(SpecializedType.ENTRANCE, result.candidate?.type)
        assertEquals(DistanceCategory.NEAR, result.coarseDistance)
        assertEquals(ScreenDirection.RIGHT, result.direction)
        assertEquals("Entrance slightly right.", result.spokenDescription)
    }

    @Test
    fun test6_entranceAwareness_returnsNoEntranceWhenNoneDetected() {
        val detector = SpecializedEnvironmentDetector()

        // Only stairs detected, no doors or entrances
        val stairs = createCandidate(SpecializedType.STAIRS_UP)
        detector.updateCandidates(listOf(stairs), timestampMs = 1000L)

        val result = detector.findNearestEntrance(currentTimeMs = 1000L)

        assertNull("Candidate should be null when no entrance exists", result.candidate)
        assertFalse("isFresh should be false", result.isFresh)
        assertEquals("No entrance detected nearby.", result.spokenDescription)
    }

    @Test
    fun test7_stairsDetection_supportsUpAndDownAndUnknown() {
        val detector = SpecializedEnvironmentDetector()

        val up = createCandidate(SpecializedType.STAIRS_UP)
        val down = createCandidate(SpecializedType.STAIRS_DOWN)
        val unknown = createCandidate(SpecializedType.STAIRS_UNKNOWN)

        val results = detector.updateCandidates(listOf(up, down, unknown))

        assertEquals(3, results.size)
        assertTrue(results[0].isStairs)
        assertTrue(results[1].isStairs)
        assertTrue(results[2].isStairs)
        assertEquals("stairs going up", results[0].type.label)
        assertEquals("stairs going down", results[1].type.label)
        assertEquals("possible stairs", results[2].type.label)
    }

    @Test
    fun test8_crosswalkDetection_usesPossibleCrosswalkWithoutSafetyClaims() {
        val detector = SpecializedEnvironmentDetector()

        val crosswalk = createCandidate(SpecializedType.POSSIBLE_CROSSWALK)
        val results = detector.updateCandidates(listOf(crosswalk))

        assertEquals(1, results.size)
        assertTrue(results.first().isCrosswalk)
        assertEquals("possible crosswalk", results.first().type.label)
        assertFalse("Crosswalk label must never claim safe to cross", results.first().type.label.contains("safe"))
    }

    @Test
    fun test9_audioCooldown_preventsRepetitiveAnnouncements() {
        val detector = SpecializedEnvironmentDetector(announcementCooldownMs = 10000L)

        val crosswalk = createCandidate(SpecializedType.POSSIBLE_CROSSWALK, timestampMs = 1000L)

        // 1st update at t=1000 -> triggers announcement
        val first = detector.updateCandidates(listOf(crosswalk), timestampMs = 1000L)
        assertEquals(1, first.size)

        // 2nd update at t=3000 (within 10s cooldown) -> candidate updated but no new audio
        val second = detector.updateCandidates(listOf(crosswalk), timestampMs = 3000L)
        assertEquals(1, second.size)

        // 3rd update at t=12000 (after 10s cooldown) -> allowed
        val third = detector.updateCandidates(listOf(crosswalk), timestampMs = 12000L)
        assertEquals(1, third.size)
    }
}

