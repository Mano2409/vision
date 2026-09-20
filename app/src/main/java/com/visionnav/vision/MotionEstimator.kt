package com.visionnav.vision

import kotlin.math.abs

/**
 * Coarse motion classifications for tracked physical objects.
 */
enum class MotionState {
    STATIONARY,
    APPROACHING,
    MOVING_AWAY,
    CROSSING,
    UNKNOWN;

    val isDynamicThreat: Boolean
        get() = this == APPROACHING || this == CROSSING
}

/**
 * Result of motion estimation.
 */
data class MotionEstimate(
    val state: MotionState,
    val confidence: Float,
    val lateralVelocityPerSec: Float = 0f,
    val expansionRatePerSec: Float = 0f
)

/**
 * Estimates physical motion state from temporal changes in tracked bounding boxes,
 * discounting camera motion and incorporating user movement where available.
 */
class MotionEstimator(
    val minObservationCount: Int = 2,
    val minTimeSpanMs: Long = 150L,
    val approachingExpansionThreshold: Float = 0.18f,
    val movingAwayShrinkThreshold: Float = -0.15f,
    val crossingLateralThreshold: Float = 0.12f
) {
    /**
     * Estimates the motion state of a tracked object over recent history.
     *
     * @param trackedObject Tracked object with historical bounding boxes
     * @param userSpeedMps Optional current pedestrian GPS walking speed in m/s
     * @param timeWindowMs Analysis time window in milliseconds
     */
    fun estimate(
        trackedObject: TrackedObject,
        userSpeedMps: Float? = null,
        timeWindowMs: Long = 1000L
    ): MotionEstimate {
        val history = trackedObject.boxHistory
        if (history.size < minObservationCount) {
            return MotionEstimate(MotionState.UNKNOWN, confidence = 0.3f)
        }

        val now = trackedObject.lastSeenMs
        val recent = history.filter { (now - it.first) <= timeWindowMs }
        if (recent.size < minObservationCount) {
            return MotionEstimate(MotionState.UNKNOWN, confidence = 0.4f)
        }

        val oldest = recent.first()
        val newest = recent.last()
        val dtSec = (newest.first - oldest.first) / 1000f

        if (dtSec < (minTimeSpanMs / 1000f)) {
            return MotionEstimate(MotionState.UNKNOWN, confidence = 0.5f)
        }

        val oldBox = oldest.second
        val newBox = newest.second

        val avgHeight = (oldBox.height + newBox.height) / 2f
        if (avgHeight <= 0.001f) {
            return MotionEstimate(MotionState.UNKNOWN, confidence = 0.2f)
        }

        val dxPerSec = (newBox.centerX - oldBox.centerX) / dtSec
        val dHeightPerSec = (newBox.height - oldBox.height) / dtSec
        val expansionRate = dHeightPerSec / avgHeight

        // Account for pedestrian forward walking speed (~1.2 m/s typical)
        // If the user is walking forward, stationary objects appear to expand slightly.
        val adjustedApproachingThreshold = if (userSpeedMps != null && userSpeedMps > 0.8f) {
            approachingExpansionThreshold + 0.10f
        } else {
            approachingExpansionThreshold
        }

        val absDx = abs(dxPerSec)

        val (state, confidence) = when {
            // Rapid lateral movement across the screen -> Crossing
            absDx >= crossingLateralThreshold -> {
                MotionState.CROSSING to (0.75f + (absDx / 2f).coerceAtMost(0.20f))
            }

            // High expansion rate -> Approaching
            expansionRate >= adjustedApproachingThreshold -> {
                MotionState.APPROACHING to (0.80f + (expansionRate / 2f).coerceAtMost(0.18f))
            }

            // Shrinking in size -> Moving away
            expansionRate <= movingAwayShrinkThreshold -> {
                MotionState.MOVING_AWAY to 0.80f
            }

            // Minimal translation and minimal scale change -> Stationary
            else -> {
                MotionState.STATIONARY to 0.85f
            }
        }

        return MotionEstimate(
            state = state,
            confidence = confidence.coerceIn(0f, 1f),
            lateralVelocityPerSec = dxPerSec,
            expansionRatePerSec = expansionRate
        )
    }
}

