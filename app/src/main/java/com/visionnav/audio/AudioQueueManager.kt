package com.visionnav.audio

import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P0 Priority Audio Arbiter enforcing non-negotiable pedestrian safety.
 *
 * Rules:
 * 1. Critical vehicle threats (TTC < 2.5s) immediately fire haptics, flush routine speech,
 *    and command "STOP. [VEHICLE] APPROACHING" or "STOP. PATH NOT CLEAR".
 * 2. Routine route instructions can NEVER interrupt or speak over an active critical danger.
 * 3. Hysteresis: An interrupted route direction is preserved and only resumed after a
 *    verified 1.5-second clear window.
 */
class AudioQueueManager(
    private val voiceSpeaker: VoiceSpeaker,
    private val hapticManager: HapticManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val tag = "AudioQueueManager"

    private var currentMessage: AudioMessage? = null
    private var interruptedManeuver: AudioMessage? = null

    private val _isCriticalAlertActive = MutableStateFlow(false)
    val isCriticalAlertActive: StateFlow<Boolean> = _isCriticalAlertActive.asStateFlow()

    private var clearWindowJob: Job? = null

    init {
        voiceSpeaker.onUtteranceDone = { utteranceId ->
            Log.d(tag, "Finished playback of utterance: $utteranceId")
            if (currentMessage?.id == utteranceId) {
                currentMessage = null
            }
        }
    }

    /**
     * Post an audio message to the arbiter.
     */
    fun post(message: AudioMessage) {
        when (message.priority) {
            AudioPriority.CRITICAL_SAFETY -> {
                handleCriticalSafety(message)
            }
            AudioPriority.STATIC_OBSTACLE -> {
                handleStaticObstacle(message)
            }
            AudioPriority.ROUTINE_NAVIGATION -> {
                handleRoutineNavigation(message)
            }
            AudioPriority.SYSTEM_FEEDBACK -> {
                handleSystemFeedback(message)
            }
        }
    }

    private fun handleCriticalSafety(message: AudioMessage) {
        Log.w(tag, "🚨 CRITICAL VEHICLE THREAT ESCALATION: ${message.text}")

        // Cancel any pending clearance recovery
        clearWindowJob?.cancel()
        _isCriticalAlertActive.value = true

        // 1. Preserve interrupted turn maneuver if applicable
        if (currentMessage?.priority == AudioPriority.ROUTINE_NAVIGATION) {
            interruptedManeuver = currentMessage
            Log.i(tag, "Preserved interrupted maneuver: ${interruptedManeuver?.text}")
        }

        // 2. Hardware reaction (<50ms): Fire emergency dual-pulse vibration shock
        hapticManager.vibrateEmergency()

        // 3. Immediately kill active TTS speech
        voiceSpeaker.stop()

        // 4. Speak authoritative emergency command at alarm volume
        currentMessage = message
        voiceSpeaker.speak(
            text = message.text,
            queueMode = TextToSpeech.QUEUE_FLUSH,
            utteranceId = message.id
        )
    }

    private fun handleStaticObstacle(message: AudioMessage) {
        // Critical safety threats cannot be interrupted by static obstacles
        if (_isCriticalAlertActive.value) {
            Log.d(tag, "Dropping static obstacle warning during active vehicle threat: ${message.text}")
            return
        }

        // Preempt routine navigation for path-blocking obstacle
        if (currentMessage?.priority == AudioPriority.ROUTINE_NAVIGATION) {
            interruptedManeuver = currentMessage
        }

        currentMessage = message
        voiceSpeaker.speak(
            text = message.text,
            queueMode = TextToSpeech.QUEUE_FLUSH,
            utteranceId = message.id
        )
    }

    private fun handleRoutineNavigation(message: AudioMessage) {
        // Routine turn maneuvers are strictly suppressed during active vehicle danger
        if (_isCriticalAlertActive.value) {
            Log.i(tag, "Buffering routine maneuver during active vehicle threat: ${message.text}")
            interruptedManeuver = message
            return
        }

        currentMessage = message
        voiceSpeaker.speak(
            text = message.text,
            queueMode = TextToSpeech.QUEUE_ADD,
            utteranceId = message.id
        )
    }

    private fun handleSystemFeedback(message: AudioMessage) {
        if (_isCriticalAlertActive.value) return

        currentMessage = message
        voiceSpeaker.speak(
            text = message.text,
            queueMode = TextToSpeech.QUEUE_ADD,
            utteranceId = message.id
        )
    }

    /**
     * Notified by the Decision Engine when the camera verifies the danger is cleared.
     * Enforces the 1.5-second sustained clear window before route resumption.
     */
    fun onHazardClearanceReported() {
        if (!_isCriticalAlertActive.value) return

        clearWindowJob?.cancel()
        clearWindowJob = scope.launch {
            Log.i(tag, "Hazard clearance reported. Entering 1.5-second stability verification window...")
            delay(1500) // 1.5-second hysteresis window required by PRD 18.1

            _isCriticalAlertActive.value = false
            hapticManager.cancel()

            val maneuverToResume = interruptedManeuver
            interruptedManeuver = null

            val resumeMessage = if (maneuverToResume != null) {
                "Danger cleared. Resuming route: ${maneuverToResume.text}"
            } else {
                "Danger cleared. Path is clear."
            }

            Log.i(tag, "Hazard verified clear for 1.5s. Resuming: $resumeMessage")
            post(AudioMessage(text = resumeMessage, priority = AudioPriority.ROUTINE_NAVIGATION))
        }
    }

    fun stopAll() {
        clearWindowJob?.cancel()
        _isCriticalAlertActive.value = false
        currentMessage = null
        interruptedManeuver = null
        hapticManager.cancel()
        voiceSpeaker.stop()
    }
}
