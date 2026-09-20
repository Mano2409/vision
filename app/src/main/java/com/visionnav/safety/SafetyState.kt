package com.visionnav.safety

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState

/**
 * Representation of an active physical hazard being evaluated by the safety engine.
 */
data class ActiveHazard(
    val trackId: Int,
    val detectionClass: DetectionClass,
    val level: SafetyLevel,
    val distance: DistanceCategory,
    val motion: MotionState,
    val lastAnnouncedLevel: SafetyLevel,
    val lastAnnouncedTimestampMs: Long,
    val currentBox: BoundingBox
)

/**
 * Overall safety subsystem snapshot.
 */
data class SafetyState(
    val overallLevel: SafetyLevel = SafetyLevel.NONE,
    val activeHazards: List<ActiveHazard> = emptyList(),
    val isLowLight: Boolean = false,
    val isVisionAvailable: Boolean = true,
    val lastFrameTimestampMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Verifies whether the vision state is fresh within the given freshness window.
     */
    fun isFresh(nowMs: Long = System.currentTimeMillis(), timeoutMs: Long = 3000L): Boolean {
        return lastFrameTimestampMs > 0L && (nowMs - lastFrameTimestampMs) <= timeoutMs
    }
}

