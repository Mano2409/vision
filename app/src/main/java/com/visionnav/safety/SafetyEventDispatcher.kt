package com.visionnav.safety

import com.visionnav.audio.AudioMessage
import com.visionnav.audio.AudioPriority
import com.visionnav.audio.AudioQueueManager
import com.visionnav.navigation.HapticFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Dispatches safety events emitted by the SafetyRiskEngine to the audio and haptic subsystems.
 *
 * Enforces the core rule:
 * - SafetyRiskEngine decides risk level.
 * - SafetyEventDispatcher emits events.
 * - AudioQueueManager arbitrates priority and handles speech.
 * - HapticFeedbackManager delivers tactile alerts.
 */
class SafetyEventDispatcher(
    private val audioQueueManager: AudioQueueManager,
    private val hapticFeedbackManager: HapticFeedbackManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var observationJob: Job? = null

    /**
     * Binds this dispatcher to a SafetyRiskEngine's event flow.
     */
    fun bind(engine: SafetyRiskEngine) {
        observationJob?.cancel()
        observationJob = engine.events
            .onEach { event -> dispatch(event) }
            .launchIn(scope)
    }

    fun dispatch(event: SafetyEvent) {
        when (event) {
            is SafetyEvent.HazardDetected -> {
                dispatchHazardAlert(event.level, event.spokenMessage)
            }

            is SafetyEvent.HazardEscalated -> {
                dispatchHazardAlert(event.newLevel, event.spokenMessage)
            }

            is SafetyEvent.HazardCleared -> {
                audioQueueManager.onHazardClearanceReported()
            }

            is SafetyEvent.CameraQualityDegraded -> {
                audioQueueManager.post(
                    AudioMessage(
                        text = event.spokenMessage,
                        priority = AudioPriority.SYSTEM_FEEDBACK
                    )
                )
            }

            is SafetyEvent.VisionSubsystemError -> {
                audioQueueManager.post(
                    AudioMessage(
                        text = event.errorMessage,
                        priority = AudioPriority.SYSTEM_FEEDBACK
                    )
                )
            }
        }
    }

    private fun dispatchHazardAlert(level: SafetyLevel, message: String) {
        if (!level.requiresAudio || message.isBlank()) return

        // 1. Deliver tactile haptic alert immediately (<50ms target)
        hapticFeedbackManager.onSafetyLevel(level)

        // 2. Map to AudioPriority
        val priority = when (level) {
            SafetyLevel.CRITICAL -> AudioPriority.CRITICAL_SAFETY
            SafetyLevel.WARNING -> AudioPriority.WARNING_SAFETY
            SafetyLevel.CAUTION -> AudioPriority.CAUTION_SAFETY
            SafetyLevel.INFO -> AudioPriority.SYSTEM_FEEDBACK
            SafetyLevel.NONE -> return
        }

        audioQueueManager.post(
            AudioMessage(
                text = message,
                priority = priority,
                requiresHaptic = (level == SafetyLevel.CRITICAL)
            )
        )
    }

    fun unbind() {
        observationJob?.cancel()
        observationJob = null
    }
}

