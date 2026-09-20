package com.visionnav.safety

import com.visionnav.vision.CorridorOverlap
import com.visionnav.vision.DepthEstimator
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionEstimator
import com.visionnav.vision.MotionState
import com.visionnav.vision.TrackedObject
import com.visionnav.vision.WalkingCorridorAnalyzer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Safety Risk Engine evaluating real-time spatial threats to visually impaired pedestrians.
 *
 * Combines object classification, multi-frame persistence, walking corridor intersection,
 * distance categorization, and motion estimation to decide safety severity.
 *
 * Implements safety escalation, smart duplicate warning suppression, and critical bypass.
 */
class SafetyRiskEngine(
    val depthEstimator: DepthEstimator = DepthEstimator(),
    val motionEstimator: MotionEstimator = MotionEstimator(),
    val corridorAnalyzer: WalkingCorridorAnalyzer = WalkingCorridorAnalyzer(),
    val warningCooldownMs: Long = 8000L,
    val minPersistenceDetections: Int = 2
) {
    private val _safetyState = MutableStateFlow(SafetyState())
    val safetyState: StateFlow<SafetyState> = _safetyState.asStateFlow()

    private val _events = MutableSharedFlow<SafetyEvent>(replay = 64, extraBufferCapacity = 64)
    val events: SharedFlow<SafetyEvent> = _events.asSharedFlow()

    // Keyed by trackId
    private val activeHazards = mutableMapOf<Int, ActiveHazard>()
    private var lastClearedTimestampMs = 0L

    /**
     * Evaluates active tracked objects from the vision pipeline for a new frame.
     */
    fun evaluateFrame(
        trackedObjects: List<TrackedObject>,
        userSpeedMps: Float? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val currentFrameTrackIds = trackedObjects.map { it.trackId }.toSet()

        // 1. Process active tracked objects
        for (obj in trackedObjects) {
            // Require minimum persistence detections before escalating risks
            if (obj.totalDetections < minPersistenceDetections && obj.currentConfidence < 0.90f) {
                continue
            }

            val corridor = corridorAnalyzer.evaluate(obj.currentBox)
            val depth = depthEstimator.estimate(obj)
            val motion = motionEstimator.estimate(obj, userSpeedMps)

            val level = computeRiskLevel(obj.detectionClass, corridor, depth.category, motion.state)
            val existing = activeHazards[obj.trackId]

            if (level != SafetyLevel.NONE) {
                handleHazard(obj, level, depth.category, motion.state, existing, timestampMs)
            } else if (existing != null) {
                // Hazard de-escalated to NONE
                clearHazard(existing, timestampMs)
            }
        }

        // 2. Identify lost or cleared tracks
        val clearedTrackIds = activeHazards.keys.filter { it !in currentFrameTrackIds }
        for (id in clearedTrackIds) {
            val hazard = activeHazards[id]
            if (hazard != null) {
                clearHazard(hazard, timestampMs)
            }
        }

        // 3. Update overall SafetyState
        val highestLevel = activeHazards.values.maxOfOrNull { it.level } ?: SafetyLevel.NONE
        _safetyState.value = _safetyState.value.copy(
            overallLevel = highestLevel,
            activeHazards = activeHazards.values.toList(),
            lastFrameTimestampMs = timestampMs,
            timestampMs = timestampMs
        )
    }

    private fun computeRiskLevel(
        clazz: DetectionClass,
        corridor: CorridorOverlap,
        distance: DistanceCategory,
        motion: MotionState
    ): SafetyLevel {
        return when {
            // Vehicles (Cars, Trucks, Buses, Motorcycles)
            clazz.isVehicle -> {
                when {
                    // Critical: ONLY when multi-signal criteria met:
                    // 1. Vehicle in or near walking corridor
                    // 2. Approaching motion
                    // 3. Very near distance (< 2m)
                    // (Never trigger CRITICAL on distance alone without approaching motion!)
                    corridor.isHazardousPath && motion == MotionState.APPROACHING && distance == DistanceCategory.VERY_NEAR -> {
                        SafetyLevel.CRITICAL
                    }
                    // Warning: approaching in corridor at near range (2-5m), or crossing corridor
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && (motion == MotionState.APPROACHING || motion == MotionState.CROSSING) && distance == DistanceCategory.NEAR -> {
                        SafetyLevel.WARNING
                    }
                    // Warning: stationary vehicle directly blocking corridor at very near distance (not critical because stationary)
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && distance == DistanceCategory.VERY_NEAR -> {
                        SafetyLevel.WARNING
                    }
                    // Caution: approaching in corridor at medium range, or crossing buffer
                    corridor.isHazardousPath && motion.isDynamicThreat && distance == DistanceCategory.MEDIUM -> {
                        SafetyLevel.CAUTION
                    }
                    // Caution: stationary vehicle inside corridor at near range
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && distance == DistanceCategory.NEAR -> {
                        SafetyLevel.CAUTION
                    }
                    // Info: vehicle outside corridor or moving away
                    corridor == CorridorOverlap.OUTSIDE_CORRIDOR && motion == MotionState.APPROACHING -> {
                        SafetyLevel.INFO
                    }
                    else -> SafetyLevel.NONE
                }
            }

            // Pedestrians
            clazz == DetectionClass.PERSON -> {
                when {
                    // Warning: person directly in corridor at very near proximity moving towards user
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && motion == MotionState.APPROACHING && distance == DistanceCategory.VERY_NEAR -> {
                        SafetyLevel.WARNING
                    }
                    // Caution: person inside corridor at near or very near range
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && distance.isHazardous -> {
                        SafetyLevel.CAUTION
                    }
                    // Caution: person approaching corridor
                    corridor == CorridorOverlap.NEAR_CORRIDOR && motion == MotionState.APPROACHING && distance.isHazardous -> {
                        SafetyLevel.CAUTION
                    }
                    // Info: person in corridor at medium range
                    corridor == CorridorOverlap.INSIDE_CORRIDOR && distance == DistanceCategory.MEDIUM -> {
                        SafetyLevel.INFO
                    }
                    else -> SafetyLevel.NONE
                }
            }

            else -> SafetyLevel.NONE
        }
    }

    private fun handleHazard(
        obj: TrackedObject,
        level: SafetyLevel,
        distance: DistanceCategory,
        motion: MotionState,
        existing: ActiveHazard?,
        timestampMs: Long
    ) {
        val spokenMessage = buildHazardMessage(obj.detectionClass, level, distance, motion)

        if (existing == null) {
            // New hazard detected
            val hazard = ActiveHazard(
                trackId = obj.trackId,
                detectionClass = obj.detectionClass,
                level = level,
                distance = distance,
                motion = motion,
                lastAnnouncedLevel = level,
                lastAnnouncedTimestampMs = timestampMs,
                currentBox = obj.currentBox
            )
            activeHazards[obj.trackId] = hazard

            _events.tryEmit(
                SafetyEvent.HazardDetected(
                    trackId = obj.trackId,
                    detectionClass = obj.detectionClass,
                    level = level,
                    distance = distance,
                    motion = motion,
                    spokenMessage = spokenMessage,
                    timestampMs = timestampMs
                )
            )
        } else {
            // Existing hazard: Check escalation and cooldown
            val isEscalation = level.priority > existing.lastAnnouncedLevel.priority
            val isCritical = level == SafetyLevel.CRITICAL
            val cooldownElapsed = (timestampMs - existing.lastAnnouncedTimestampMs) >= warningCooldownMs

            val shouldAnnounce = isEscalation || isCritical || cooldownElapsed

            val updatedHazard = existing.copy(
                level = level,
                distance = distance,
                motion = motion,
                currentBox = obj.currentBox,
                lastAnnouncedLevel = if (shouldAnnounce) level else existing.lastAnnouncedLevel,
                lastAnnouncedTimestampMs = if (shouldAnnounce) timestampMs else existing.lastAnnouncedTimestampMs
            )
            activeHazards[obj.trackId] = updatedHazard

            if (isEscalation) {
                _events.tryEmit(
                    SafetyEvent.HazardEscalated(
                        trackId = obj.trackId,
                        detectionClass = obj.detectionClass,
                        previousLevel = existing.lastAnnouncedLevel,
                        newLevel = level,
                        distance = distance,
                        motion = motion,
                        spokenMessage = spokenMessage,
                        timestampMs = timestampMs
                    )
                )
            } else if (shouldAnnounce && level.requiresAudio) {
                _events.tryEmit(
                    SafetyEvent.HazardDetected(
                        trackId = obj.trackId,
                        detectionClass = obj.detectionClass,
                        level = level,
                        distance = distance,
                        motion = motion,
                        spokenMessage = spokenMessage,
                        timestampMs = timestampMs
                    )
                )
            }
        }
    }

    private fun clearHazard(hazard: ActiveHazard, timestampMs: Long) {
        activeHazards.remove(hazard.trackId)
        val clearMsg = "${hazard.detectionClass.label.replaceFirstChar { it.uppercase() }} cleared."
        _events.tryEmit(
            SafetyEvent.HazardCleared(
                trackId = hazard.trackId,
                detectionClass = hazard.detectionClass,
                spokenMessage = clearMsg,
                timestampMs = timestampMs
            )
        )
    }

    private fun buildHazardMessage(
        clazz: DetectionClass,
        level: SafetyLevel,
        distance: DistanceCategory,
        motion: MotionState
    ): String {
        val name = clazz.label.lowercase()

        return when (level) {
            SafetyLevel.CRITICAL -> {
                "STOP. ${name.uppercase()} APPROACHING."
            }
            SafetyLevel.WARNING -> {
                when (motion) {
                    MotionState.APPROACHING -> "${name.replaceFirstChar { it.uppercase() }} approaching ahead."
                    MotionState.CROSSING -> "${name.replaceFirstChar { it.uppercase() }} crossing your path."
                    else -> "$name directly ahead."
                }
            }
            SafetyLevel.CAUTION -> {
                when (motion) {
                    MotionState.APPROACHING -> "$name approaching."
                    else -> "$name ahead."
                }
            }
            SafetyLevel.INFO -> {
                "$name detected."
            }
            SafetyLevel.NONE -> ""
        }
    }

    fun reportLowLight(isLowLight: Boolean) {
        val previous = _safetyState.value.isLowLight
        _safetyState.value = _safetyState.value.copy(isLowLight = isLowLight)
        if (isLowLight && !previous) {
            _events.tryEmit(
                SafetyEvent.CameraQualityDegraded(
                    reason = "Low light",
                    spokenMessage = "Low visibility. Vision confidence reduced."
                )
            )
        }
    }

    fun reportVisionUnavailable(reason: String) {
        _safetyState.value = _safetyState.value.copy(isVisionAvailable = false)
        _events.tryEmit(
            SafetyEvent.VisionSubsystemError("Vision unavailable: $reason")
        )
    }

    fun getActiveHazards(): List<ActiveHazard> = activeHazards.values.toList()

    /**
     * Checks if current vision/safety data is fresh within the given timeout window.
     */
    fun isDataFresh(nowMs: Long = System.currentTimeMillis(), timeoutMs: Long = 3000L): Boolean {
        return _safetyState.value.isFresh(nowMs, timeoutMs)
    }

    /**
     * Retrieves the most recent active hazard announced within maxAgeMs (for RepeatHazard).
     */
    fun getLatestActiveHazard(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 10000L): ActiveHazard? {
        return activeHazards.values
            .filter { (nowMs - it.lastAnnouncedTimestampMs) <= maxAgeMs }
            .maxByOrNull { it.level.priority }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun reset() {
        activeHazards.clear()
        _events.resetReplayCache()
        _safetyState.value = SafetyState()
    }
}
