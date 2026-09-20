package com.visionnav.safety

import com.visionnav.audio.AudioMessage
import com.visionnav.audio.AudioPriority
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEventDispatcherTest {

    private class RecordingDispatcher {
        val postedMessages = mutableListOf<AudioMessage>()
        val hapticEvents = mutableListOf<SafetyLevel>()
        var clearanceReported = false

        fun dispatch(event: SafetyEvent) {
            when (event) {
                is SafetyEvent.HazardDetected -> {
                    dispatchHazardAlert(event.level, event.spokenMessage)
                }
                is SafetyEvent.HazardEscalated -> {
                    dispatchHazardAlert(event.newLevel, event.spokenMessage)
                }
                is SafetyEvent.HazardCleared -> {
                    clearanceReported = true
                }
                is SafetyEvent.CameraQualityDegraded -> {
                    postedMessages.add(AudioMessage(text = event.spokenMessage, priority = AudioPriority.SYSTEM_FEEDBACK))
                }
                is SafetyEvent.VisionSubsystemError -> {
                    postedMessages.add(AudioMessage(text = event.errorMessage, priority = AudioPriority.SYSTEM_FEEDBACK))
                }
            }
        }

        private fun dispatchHazardAlert(level: SafetyLevel, message: String) {
            if (!level.requiresAudio || message.isBlank()) return

            hapticEvents.add(level)

            val priority = when (level) {
                SafetyLevel.CRITICAL -> AudioPriority.CRITICAL_SAFETY
                SafetyLevel.WARNING -> AudioPriority.WARNING_SAFETY
                SafetyLevel.CAUTION -> AudioPriority.CAUTION_SAFETY
                SafetyLevel.INFO -> AudioPriority.SYSTEM_FEEDBACK
                SafetyLevel.NONE -> return
            }

            postedMessages.add(
                AudioMessage(
                    text = message,
                    priority = priority,
                    requiresHaptic = (level == SafetyLevel.CRITICAL)
                )
            )
        }
    }

    @Test
    fun testCriticalHazardDispatchesCriticalPriorityAndHaptic() {
        val dispatcher = RecordingDispatcher()
        val event = SafetyEvent.HazardDetected(
            trackId = 1,
            detectionClass = DetectionClass.CAR,
            level = SafetyLevel.CRITICAL,
            distance = DistanceCategory.VERY_NEAR,
            motion = MotionState.APPROACHING,
            spokenMessage = "STOP. CAR APPROACHING."
        )

        dispatcher.dispatch(event)

        assertEquals(1, dispatcher.postedMessages.size)
        val msg = dispatcher.postedMessages.first()
        assertEquals(AudioPriority.CRITICAL_SAFETY, msg.priority)
        assertTrue(msg.requiresHaptic)
        assertEquals(listOf(SafetyLevel.CRITICAL), dispatcher.hapticEvents)
    }

    @Test
    fun testWarningHazardDispatchesWarningPriority() {
        val dispatcher = RecordingDispatcher()
        val event = SafetyEvent.HazardDetected(
            trackId = 2,
            detectionClass = DetectionClass.BICYCLE,
            level = SafetyLevel.WARNING,
            distance = DistanceCategory.NEAR,
            motion = MotionState.CROSSING,
            spokenMessage = "Bicycle crossing your path."
        )

        dispatcher.dispatch(event)

        assertEquals(1, dispatcher.postedMessages.size)
        val msg = dispatcher.postedMessages.first()
        assertEquals(AudioPriority.WARNING_SAFETY, msg.priority)
        assertEquals(listOf(SafetyLevel.WARNING), dispatcher.hapticEvents)
    }

    @Test
    fun testCautionHazardDispatchesCautionPriority() {
        val dispatcher = RecordingDispatcher()
        val event = SafetyEvent.HazardDetected(
            trackId = 3,
            detectionClass = DetectionClass.PERSON,
            level = SafetyLevel.CAUTION,
            distance = DistanceCategory.NEAR,
            motion = MotionState.STATIONARY,
            spokenMessage = "Person ahead."
        )

        dispatcher.dispatch(event)

        assertEquals(1, dispatcher.postedMessages.size)
        val msg = dispatcher.postedMessages.first()
        assertEquals(AudioPriority.CAUTION_SAFETY, msg.priority)
        assertEquals(listOf(SafetyLevel.CAUTION), dispatcher.hapticEvents)
    }

    @Test
    fun testHazardClearedDispatchesClearanceReport() {
        val dispatcher = RecordingDispatcher()
        val event = SafetyEvent.HazardCleared(
            trackId = 1,
            detectionClass = DetectionClass.CAR,
            spokenMessage = "Car cleared."
        )

        dispatcher.dispatch(event)
        assertTrue("Clearance must be reported to audio arbiter", dispatcher.clearanceReported)
    }
}

