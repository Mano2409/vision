package com.visionnav.simulator

import com.visionnav.safety.SafetyEvent
import com.visionnav.safety.SafetyLevel
import com.visionnav.safety.SafetyRiskEngine
import com.visionnav.vision.BoundingBox
import com.visionnav.vision.Detection
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DetectionFilter
import com.visionnav.vision.MockYoloDetector
import com.visionnav.vision.ObjectTracker

/**
 * Deterministic Vision and Safety Scenario Simulator.
 *
 * Enables reproducible automated testing and developer simulation of all 12 Phase 5 safety scenarios
 * without requiring physical camera hardware, external services, or non-deterministic delays.
 */
class VisionScenarioSimulator(
    val yoloDetector: MockYoloDetector = MockYoloDetector(),
    val filter: DetectionFilter = DetectionFilter(),
    val tracker: ObjectTracker = ObjectTracker(),
    val riskEngine: SafetyRiskEngine = SafetyRiskEngine()
) {
    val recordedEvents = mutableListOf<SafetyEvent>()

    init {
        // Collect emitted events synchronously for testing
        // SafetyRiskEngine uses extraBufferCapacity = 64
    }

    private fun stepPipeline(detections: List<Detection>, timestampMs: Long): List<SafetyEvent> {
        val startEventCount = riskEngine.events.replayCache.size

        val stable = filter.filter(detections, timestampMs)
        val tracked = tracker.update(stable, timestampMs)
        riskEngine.evaluateFrame(tracked, userSpeedMps = 1.2f, timestampMs = timestampMs)

        // Return newly emitted events
        return riskEngine.events.replayCache.drop(startEventCount)
    }

    fun reset() {
        yoloDetector.clearQueue()
        filter.reset()
        tracker.reset()
        riskEngine.reset()
        recordedEvents.clear()
    }

    /**
     * 1. Person Ahead Scenario:
     * A pedestrian walking in the central corridor at near distance.
     */
    fun scenarioPersonAhead(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Frame 1
        val det1 = Detection(
            detectionClass = DetectionClass.PERSON,
            confidence = 0.88f,
            boundingBox = BoundingBox(0.40f, 0.30f, 0.60f, 0.80f),
            timestampMs = baseTimestampMs
        )
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))

        // Frame 2: Consistent observation
        val det2 = Detection(
            detectionClass = DetectionClass.PERSON,
            confidence = 0.90f,
            boundingBox = BoundingBox(0.40f, 0.30f, 0.60f, 0.80f),
            timestampMs = baseTimestampMs + 100L
        )
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))

        return events
    }

    /**
     * 2. Vehicle Approaching Scenario:
     * A car in the corridor rapidly expanding towards the user.
     */
    fun scenarioVehicleApproaching(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Frame 1: Car at distance (smaller box)
        val det1 = Detection(
            detectionClass = DetectionClass.CAR,
            confidence = 0.92f,
            boundingBox = BoundingBox(0.35f, 0.40f, 0.65f, 0.70f), // height = 0.30
            timestampMs = baseTimestampMs
        )
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))

        // Frame 2: Car closer (looming/expanding)
        val det2 = Detection(
            detectionClass = DetectionClass.CAR,
            confidence = 0.95f,
            boundingBox = BoundingBox(0.30f, 0.30f, 0.70f, 0.85f), // height = 0.55
            timestampMs = baseTimestampMs + 300L
        )
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 300L))

        return events
    }

    /**
     * 3. Bicycle Crossing Scenario:
     * A cyclist crossing laterally from left to right across the corridor.
     */
    fun scenarioBicycleCrossing(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Frame 1: Cyclist on the left
        val det1 = Detection(
            detectionClass = DetectionClass.BICYCLE,
            confidence = 0.88f,
            boundingBox = BoundingBox(0.15f, 0.40f, 0.35f, 0.75f),
            timestampMs = baseTimestampMs
        )
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))

        // Frame 2: Cyclist translated laterally into corridor
        val det2 = Detection(
            detectionClass = DetectionClass.BICYCLE,
            confidence = 0.91f,
            boundingBox = BoundingBox(0.35f, 0.40f, 0.55f, 0.75f),
            timestampMs = baseTimestampMs + 250L
        )
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 250L))

        return events
    }

    /**
     * 4. Stationary Object Scenario:
     * A car parked outside the corridor with no scale or lateral change.
     */
    fun scenarioStationaryObject(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        val box = BoundingBox(0.05f, 0.45f, 0.25f, 0.65f) // parked far to the left
        val det1 = Detection(DetectionClass.CAR, 0.85f, box, baseTimestampMs)
        val det2 = Detection(DetectionClass.CAR, 0.86f, box, baseTimestampMs + 200L)

        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 200L))

        return events
    }

    /**
     * 5. Object Entering Corridor Scenario:
     * An object starts outside the corridor and enters it in subsequent frames.
     */
    fun scenarioObjectEnteringCorridor(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Frame 1: Outside
        val det1 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.10f, 0.35f, 0.25f, 0.75f), baseTimestampMs)
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))

        // Frame 2: Moving into corridor
        val det2 = Detection(DetectionClass.PERSON, 0.92f, BoundingBox(0.38f, 0.35f, 0.53f, 0.75f), baseTimestampMs + 300L)
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 300L))

        return events
    }

    /**
     * 6. Object Leaving Corridor Scenario:
     * An object in the corridor exits, triggering de-escalation and clearance.
     */
    fun scenarioObjectLeavingCorridor(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Frame 1 & 2: Object in corridor
        val det1 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.40f, 0.35f, 0.55f, 0.75f), baseTimestampMs)
        val det2 = Detection(DetectionClass.PERSON, 0.92f, BoundingBox(0.40f, 0.35f, 0.55f, 0.75f), baseTimestampMs + 100L)
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))

        // Frame 3: Object moves toward edge
        val det3 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.55f, 0.35f, 0.70f, 0.75f), baseTimestampMs + 300L)
        events.addAll(stepPipeline(listOf(det3), baseTimestampMs + 300L))

        // Frame 4: Object moves completely outside corridor
        val det4 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.88f, 0.35f, 0.98f, 0.75f), baseTimestampMs + 500L)
        events.addAll(stepPipeline(listOf(det4), baseTimestampMs + 500L))

        // Frame 5: Track times out and clears
        events.addAll(stepPipeline(emptyList(), baseTimestampMs + 1800L))

        return events
    }

    /**
     * 7. Warning Escalation Scenario:
     * Person moves from near distance (Caution) to critical proximity moving towards user (Warning).
     */
    fun scenarioWarningEscalation(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // Step 1: Person at near distance in corridor (Caution)
        val det1 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.42f, 0.40f, 0.58f, 0.75f), baseTimestampMs)
        val det2 = Detection(DetectionClass.PERSON, 0.90f, BoundingBox(0.42f, 0.40f, 0.58f, 0.75f), baseTimestampMs + 100L)
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))

        // Step 2: Person expands to critical proximity moving towards user (Warning/Critical)
        val det3 = Detection(DetectionClass.PERSON, 0.95f, BoundingBox(0.35f, 0.20f, 0.65f, 0.90f), baseTimestampMs + 300L)
        events.addAll(stepPipeline(listOf(det3), baseTimestampMs + 300L))

        return events
    }

    /**
     * 8. Warning Cooldown Scenario:
     * Repeated detections at the same level within cooldown period are suppressed.
     */
    fun scenarioWarningCooldown(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        val box = BoundingBox(0.42f, 0.40f, 0.58f, 0.75f)
        val det1 = Detection(DetectionClass.PERSON, 0.90f, box, baseTimestampMs)
        val det2 = Detection(DetectionClass.PERSON, 0.90f, box, baseTimestampMs + 100L)
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))

        // Rapid subsequent frames at the same position and level (within 8000ms cooldown)
        val det3 = Detection(DetectionClass.PERSON, 0.90f, box, baseTimestampMs + 500L)
        val det4 = Detection(DetectionClass.PERSON, 0.90f, box, baseTimestampMs + 1000L)
        events.addAll(stepPipeline(listOf(det3), baseTimestampMs + 500L))
        events.addAll(stepPipeline(listOf(det4), baseTimestampMs + 1000L))

        return events
    }

    /**
     * 9. Critical Safety Override Scenario:
     * An approaching vehicle jumps to CRITICAL and immediately overrides any active cooldown.
     */
    fun scenarioCriticalSafetyOverride(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        val events = mutableListOf<SafetyEvent>()

        // 1. Initial moderate warning (Caution)
        val det1 = Detection(DetectionClass.CAR, 0.85f, BoundingBox(0.38f, 0.45f, 0.62f, 0.65f), baseTimestampMs)
        val det2 = Detection(DetectionClass.CAR, 0.85f, BoundingBox(0.38f, 0.45f, 0.62f, 0.65f), baseTimestampMs + 100L)
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))

        // 2. Imminent collision vehicle within cooldown window (<2m, looming, in corridor)
        val det3 = Detection(DetectionClass.CAR, 0.96f, BoundingBox(0.20f, 0.15f, 0.80f, 0.85f), baseTimestampMs + 400L)
        events.addAll(stepPipeline(listOf(det3), baseTimestampMs + 400L))

        return events
    }

    /**
     * 10. Camera Unavailable Scenario:
     * Camera disconnect or error reports safe fallback without crashing.
     */
    fun scenarioCameraUnavailable(): List<SafetyEvent> {
        reset()
        riskEngine.reportVisionUnavailable("Camera disconnected by system")
        return riskEngine.events.replayCache
    }

    /**
     * 11. Depth Unavailable Scenario:
     * When distance cannot be computed, system returns UNKNOWN without fabricating numbers.
     */
    fun scenarioDepthUnavailable(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        reset()
        // Corrupted / tiny bounding box
        val det1 = Detection(DetectionClass.PERSON, 0.85f, BoundingBox(0.49f, 0.49f, 0.50f, 0.50f), baseTimestampMs)
        val det2 = Detection(DetectionClass.PERSON, 0.85f, BoundingBox(0.49f, 0.49f, 0.50f, 0.50f), baseTimestampMs + 100L)

        val events = mutableListOf<SafetyEvent>()
        events.addAll(stepPipeline(listOf(det1), baseTimestampMs))
        events.addAll(stepPipeline(listOf(det2), baseTimestampMs + 100L))
        return events
    }

    /**
     * 12. Offline Mode Scenario:
     * Fully on-device execution without network access.
     */
    fun scenarioOfflineMode(baseTimestampMs: Long = 1000L): List<SafetyEvent> {
        // Local vision pipeline works entirely without network
        return scenarioVehicleApproaching(baseTimestampMs)
    }
}

