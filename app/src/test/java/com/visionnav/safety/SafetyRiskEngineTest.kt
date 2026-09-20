package com.visionnav.safety

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.TrackedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafetyRiskEngineTest {

    private lateinit var riskEngine: SafetyRiskEngine

    @Before
    fun setUp() {
        riskEngine = SafetyRiskEngine(
            warningCooldownMs = 8000L,
            minPersistenceDetections = 2
        )
    }

    private fun createTrackedObject(
        trackId: Int = 1,
        clazz: DetectionClass = DetectionClass.CAR,
        boxes: List<Pair<Long, BoundingBox>>
    ): TrackedObject {
        val last = boxes.last()
        return TrackedObject(
            trackId = trackId,
            detectionClass = clazz,
            currentBox = last.second,
            currentConfidence = 0.92f,
            firstSeenMs = boxes.first().first,
            lastSeenMs = last.first,
            totalDetections = boxes.size,
            boxHistory = boxes.toMutableList()
        )
    }

    @Test
    fun testCriticalApproachingVehicleTriggersCriticalLevel() {
        // Vehicle expanding into critical proximity in center corridor
        val box1 = BoundingBox(0.35f, 0.35f, 0.65f, 0.70f) // height = 0.35
        val box2 = BoundingBox(0.20f, 0.20f, 0.80f, 0.85f) // height = 0.65 (> 0.50 -> CRITICAL_PROXIMITY)

        val vehicle = createTrackedObject(
            clazz = DetectionClass.CAR,
            boxes = listOf(Pair(1000L, box1), Pair(1300L, box2))
        )

        riskEngine.evaluateFrame(listOf(vehicle), timestampMs = 1300L)

        val state = riskEngine.safetyState.value
        assertEquals(SafetyLevel.CRITICAL, state.overallLevel)
        assertEquals(1, state.activeHazards.size)
        assertEquals(SafetyLevel.CRITICAL, state.activeHazards.first().level)
    }

    @Test
    fun testVeryNearStationaryVehicleDoesNotTriggerCritical() {
        // Very near vehicle (<2m, height 0.65) that is STATIONARY in corridor
        // Rule: Distance VERY_NEAR alone does NOT trigger CRITICAL without approaching motion!
        val box = BoundingBox(0.20f, 0.20f, 0.80f, 0.85f)
        val stationaryVehicle = createTrackedObject(
            clazz = DetectionClass.CAR,
            boxes = listOf(Pair(1000L, box), Pair(1300L, box))
        )

        riskEngine.evaluateFrame(listOf(stationaryVehicle), timestampMs = 1300L)

        val state = riskEngine.safetyState.value
        // Must be WARNING (blocked path), NOT CRITICAL!
        assertEquals("Stationary vehicle at VERY_NEAR must NOT be CRITICAL", SafetyLevel.WARNING, state.overallLevel)
    }

    @Test
    fun testStaleVisionDataRejectedByFreshnessCheck() {
        val box = BoundingBox(0.40f, 0.35f, 0.60f, 0.75f)
        val person = createTrackedObject(
            clazz = DetectionClass.PERSON,
            boxes = listOf(Pair(1000L, box), Pair(1200L, box))
        )

        riskEngine.evaluateFrame(listOf(person), timestampMs = 1200L)

        // Fresh within 3000ms
        assertTrue("Data should be fresh at 2000ms", riskEngine.isDataFresh(nowMs = 2000L, timeoutMs = 3000L))

        // Stale after 5000ms (1200 + 3000 = 4200 cutoff)
        org.junit.Assert.assertFalse("Data should be stale at 5000ms", riskEngine.isDataFresh(nowMs = 5000L, timeoutMs = 3000L))
    }

    @Test
    fun testPersonInCorridorProducesCaution() {
        val box1 = BoundingBox(0.40f, 0.35f, 0.60f, 0.75f) // height = 0.40 -> NEAR
        val box2 = BoundingBox(0.40f, 0.35f, 0.60f, 0.75f)

        val person = createTrackedObject(
            clazz = DetectionClass.PERSON,
            boxes = listOf(Pair(1000L, box1), Pair(1200L, box2))
        )

        riskEngine.evaluateFrame(listOf(person), timestampMs = 1200L)

        val state = riskEngine.safetyState.value
        assertEquals(SafetyLevel.CAUTION, state.overallLevel)
    }

    @Test
    fun testStationaryParkedCarOutsideCorridorIsNoneOrInfo() {
        val curbBox = BoundingBox(0.02f, 0.40f, 0.15f, 0.70f)
        val parkedCar = createTrackedObject(
            clazz = DetectionClass.CAR,
            boxes = listOf(Pair(1000L, curbBox), Pair(1200L, curbBox))
        )

        riskEngine.evaluateFrame(listOf(parkedCar), timestampMs = 1200L)
        val state = riskEngine.safetyState.value
        assertEquals(SafetyLevel.NONE, state.overallLevel)
    }

    @Test
    fun testDuplicateWarningSuppressionWithinCooldown() {
        val box1 = BoundingBox(0.40f, 0.35f, 0.60f, 0.75f)
        val person = createTrackedObject(
            clazz = DetectionClass.PERSON,
            boxes = listOf(Pair(1000L, box1), Pair(1200L, box1))
        )

        val events = mutableListOf<SafetyEvent>()

        // First announcement
        riskEngine.evaluateFrame(listOf(person), timestampMs = 1200L)
        events.addAll(riskEngine.events.replayCache)

        // Rapid subsequent frame at same level 500ms later (cooldown is 8000ms)
        val personNextFrame = createTrackedObject(
            clazz = DetectionClass.PERSON,
            boxes = listOf(Pair(1000L, box1), Pair(1700L, box1))
        )
        val countBefore = riskEngine.events.replayCache.size
        riskEngine.evaluateFrame(listOf(personNextFrame), timestampMs = 1700L)
        val countAfter = riskEngine.events.replayCache.size

        assertEquals("Duplicate warning should be suppressed by cooldown", countBefore, countAfter)
    }

    @Test
    fun testCriticalEscalationBypassesCooldown() {
        // Step 1: Car at near distance in corridor -> Warning
        val box1 = BoundingBox(0.35f, 0.40f, 0.65f, 0.70f)
        val car1 = createTrackedObject(
            clazz = DetectionClass.CAR,
            boxes = listOf(Pair(1000L, box1), Pair(1200L, box1))
        )
        riskEngine.evaluateFrame(listOf(car1), timestampMs = 1200L)
        val initialEvents = riskEngine.events.replayCache.size
        assertTrue(initialEvents > 0)

        // Step 2: 500ms later, car rapidly expands to critical proximity -> Escalates to CRITICAL
        val boxCritical = BoundingBox(0.20f, 0.15f, 0.80f, 0.85f)
        val carCritical = createTrackedObject(
            clazz = DetectionClass.CAR,
            boxes = listOf(Pair(1000L, box1), Pair(1700L, boxCritical))
        )
        riskEngine.evaluateFrame(listOf(carCritical), timestampMs = 1700L)

        val newEvents = riskEngine.events.replayCache.drop(initialEvents)
        assertTrue("Critical escalation must bypass cooldown", newEvents.isNotEmpty())
        assertTrue("Event must be HazardEscalated or HazardDetected with CRITICAL",
            newEvents.any { (it is SafetyEvent.HazardEscalated && it.newLevel == SafetyLevel.CRITICAL) ||
                    (it is SafetyEvent.HazardDetected && it.level == SafetyLevel.CRITICAL) })
    }

    @Test
    fun testHazardClearanceWhenTrackDisappears() {
        val box = BoundingBox(0.40f, 0.35f, 0.60f, 0.75f)
        val person = createTrackedObject(
            clazz = DetectionClass.PERSON,
            boxes = listOf(Pair(1000L, box), Pair(1200L, box))
        )

        riskEngine.evaluateFrame(listOf(person), timestampMs = 1200L)
        assertEquals(1, riskEngine.safetyState.value.activeHazards.size)

        // Frame with no objects (obstacle cleared)
        riskEngine.evaluateFrame(emptyList(), timestampMs = 1500L)
        assertEquals(0, riskEngine.safetyState.value.activeHazards.size)
        assertEquals(SafetyLevel.NONE, riskEngine.safetyState.value.overallLevel)

        val lastEvent = riskEngine.events.replayCache.lastOrNull()
        assertTrue("HazardCleared event should be emitted", lastEvent is SafetyEvent.HazardCleared)
    }
}

