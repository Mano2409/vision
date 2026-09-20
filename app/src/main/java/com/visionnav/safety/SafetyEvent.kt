package com.visionnav.safety

import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState

/**
 * Decoupled safety event hierarchy emitted by the SafetyRiskEngine.
 */
sealed class SafetyEvent {
    abstract val timestampMs: Long

    data class HazardDetected(
        val trackId: Int,
        val detectionClass: DetectionClass,
        val level: SafetyLevel,
        val distance: DistanceCategory,
        val motion: MotionState,
        val spokenMessage: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : SafetyEvent()

    data class HazardEscalated(
        val trackId: Int,
        val detectionClass: DetectionClass,
        val previousLevel: SafetyLevel,
        val newLevel: SafetyLevel,
        val distance: DistanceCategory,
        val motion: MotionState,
        val spokenMessage: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : SafetyEvent()

    data class HazardCleared(
        val trackId: Int,
        val detectionClass: DetectionClass,
        val spokenMessage: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : SafetyEvent()

    data class CameraQualityDegraded(
        val reason: String,
        val spokenMessage: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : SafetyEvent()

    data class VisionSubsystemError(
        val errorMessage: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : SafetyEvent()
}

