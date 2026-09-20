package com.visionnav.safety

import com.visionnav.vision.DetectionClass
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class VisibilityAssistManagerTest {

    private class FakeTorchController(
        var isAvailableOverride: Boolean = true,
        var shouldThrowOnSet: Boolean = false
    ) : ITorchController {
        val torchHistory = CopyOnWriteArrayList<Boolean>()
        var currentTorchState: Boolean = false

        override val isAvailable: Boolean
            get() = isAvailableOverride

        override fun setTorch(enabled: Boolean): Boolean {
            if (shouldThrowOnSet) {
                throw RuntimeException("Simulated CameraControl hardware exception")
            }
            currentTorchState = enabled
            torchHistory.add(enabled)
            return true
        }
    }

    private fun createHighRiskVehicleEvent(
        detectionClass: DetectionClass = DetectionClass.CAR,
        level: SafetyLevel = SafetyLevel.CRITICAL,
        motion: MotionState = MotionState.APPROACHING,
        distance: DistanceCategory = DistanceCategory.VERY_NEAR
    ): SafetyEvent {
        return SafetyEvent.HazardDetected(
            trackId = 1,
            detectionClass = detectionClass,
            level = level,
            distance = distance,
            motion = motion,
            spokenMessage = "Stop. Vehicle approaching."
        )
    }

    @Test
    fun test1_torchDisabled_noTorchActivation() {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(isEnabled = false)
        )

        val event = createHighRiskVehicleEvent()
        val result = manager.handleSafetyEvent(event)

        assertFalse("Should return false when feature is disabled", result)
        assertFalse("Torch should not be active", manager.isTorchActive.value)
        assertTrue("No torch commands should be sent", fakeTorch.torchHistory.isEmpty())
    }

    @Test
    fun test2_torchUnavailable_noCrash() {
        val fakeTorch = FakeTorchController(isAvailableOverride = false)
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope
        )

        val event = createHighRiskVehicleEvent()
        val result = manager.handleSafetyEvent(event)

        assertFalse("Should return false when torch hardware is unavailable", result)
        assertFalse("Torch should not be active", manager.isTorchActive.value)
        assertTrue("No torch commands should be sent", fakeTorch.torchHistory.isEmpty())
    }

    @Test
    fun test3_validHighRiskVehicleEvent_startsTorchPattern() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                shortOnMs = 20L,
                shortOffMs = 20L,
                longOnMs = 40L,
                longOffMs = 20L,
                letterPauseMs = 20L
            )
        )

        val event = createHighRiskVehicleEvent()
        val result = manager.handleSafetyEvent(event)

        assertTrue("Valid high-risk vehicle event must trigger torch", result)
        assertTrue("Torch should be active immediately", manager.isTorchActive.value)
        delay(15)
        assertTrue("Torch must have been turned on", fakeTorch.torchHistory.contains(true))
        manager.stop()
    }

    @Test
    fun test4_nonVehicleSafetyEvent_noTorchActivation() {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope
        )

        val event = createHighRiskVehicleEvent(
            detectionClass = DetectionClass.PERSON,
            level = SafetyLevel.CRITICAL,
            motion = MotionState.APPROACHING
        )
        val result = manager.handleSafetyEvent(event)

        assertFalse("Non-vehicle events must never trigger torch", result)
        assertFalse("Torch should not be active", manager.isTorchActive.value)
        assertTrue("No torch commands should be sent", fakeTorch.torchHistory.isEmpty())
    }

    @Test
    fun test5_lowRiskVehicleEvent_noTorchActivation() {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope
        )

        // 1. Caution level vehicle
        val cautionEvent = createHighRiskVehicleEvent(
            level = SafetyLevel.CAUTION,
            motion = MotionState.APPROACHING,
            distance = DistanceCategory.MEDIUM
        )
        assertFalse("Caution vehicle must not trigger torch", manager.handleSafetyEvent(cautionEvent))

        // 2. Info level vehicle
        val infoEvent = createHighRiskVehicleEvent(
            level = SafetyLevel.INFO,
            motion = MotionState.APPROACHING,
            distance = DistanceCategory.FAR
        )
        assertFalse("Info vehicle must not trigger torch", manager.handleSafetyEvent(infoEvent))

        // 3. Stationary warning vehicle (not approaching)
        val stationaryWarning = createHighRiskVehicleEvent(
            level = SafetyLevel.WARNING,
            motion = MotionState.STATIONARY,
            distance = DistanceCategory.VERY_NEAR
        )
        assertFalse("Stationary warning vehicle must not trigger torch", manager.handleSafetyEvent(stationaryWarning))

        assertTrue("No torch commands should be sent", fakeTorch.torchHistory.isEmpty())
    }

    @Test
    fun test6_duplicateSameEvent_noRepeatedTriggering() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                shortOnMs = 100L,
                shortOffMs = 100L,
                cooldownMs = 5000L
            )
        )

        val event = createHighRiskVehicleEvent()
        val firstResult = manager.handleSafetyEvent(event, currentTimeMs = 1000L)
        assertTrue("First trigger must succeed", firstResult)
        assertTrue("Torch is currently active", manager.isTorchActive.value)

        // Send duplicate event immediately
        val duplicateResult = manager.handleSafetyEvent(event, currentTimeMs = 1050L)
        assertFalse("Duplicate event while running must be rejected", duplicateResult)
        manager.stop()
    }

    @Test
    fun test7_cooldown_preventsImmediateRetrigger() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                shortOnMs = 2L,
                shortOffMs = 2L,
                longOnMs = 2L,
                longOffMs = 2L,
                letterPauseMs = 2L,
                cooldownMs = 5000L
            )
        )

        val event = createHighRiskVehicleEvent()

        // 1. Initial trigger at t=1000
        val first = manager.handleSafetyEvent(event, currentTimeMs = 1000L)
        assertTrue("Initial trigger must succeed", first)

        // Wait for pattern to complete
        manager.awaitCompletion()
        assertFalse("Torch pattern finished", manager.isTorchActive.value)

        // 2. Attempt trigger at t=3000 (within 5000ms cooldown)
        val tooEarly = manager.handleSafetyEvent(event, currentTimeMs = 3000L)
        assertFalse("Trigger within cooldown period must be rejected", tooEarly)

        // 3. Attempt trigger at t=6001 (after 5000ms cooldown)
        val afterCooldown = manager.handleSafetyEvent(event, currentTimeMs = 6001L)
        assertTrue("Trigger after cooldown period must succeed", afterCooldown)
        manager.stop()
    }

    @Test
    fun test8_sosSequence_followsConfiguredTimingAndStates() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                shortOnMs = 3L,
                shortOffMs = 3L,
                longOnMs = 6L,
                longOffMs = 3L,
                letterPauseMs = 4L
            )
        )

        val event = createHighRiskVehicleEvent()
        manager.handleSafetyEvent(event)

        // Await full sequence
        manager.awaitCompletion()

        // In SOS pattern, there are exactly 9 ON pulses: 3 short (S), 3 long (O), 3 short (S)
        val onCount = fakeTorch.torchHistory.count { it }
        assertEquals("SOS pattern must have exactly 9 ON pulses", 9, onCount)

        // The final state must be OFF
        assertFalse("Torch must be OFF after sequence completes", fakeTorch.currentTorchState)
        assertFalse("isTorchActive must be false after sequence completes", manager.isTorchActive.value)
    }

    @Test
    fun test9_sequenceCompletion_torchTurnsOff() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                shortOnMs = 2L,
                shortOffMs = 2L,
                longOnMs = 2L,
                longOffMs = 2L,
                letterPauseMs = 2L
            )
        )

        val event = createHighRiskVehicleEvent()
        manager.handleSafetyEvent(event)
        manager.awaitCompletion()

        assertFalse("Torch must turn OFF upon sequence completion", fakeTorch.currentTorchState)
        assertFalse("isTorchActive must be false", manager.isTorchActive.value)
    }

    @Test
    fun test10_maximumTimeout_torchTurnsOff() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        // Configure a short maximum timeout of 40ms with a solid light of 5000ms
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                pattern = TorchPattern.SOLID,
                solidDurationMs = 5000L,
                maxActiveTimeMs = 40L
            )
        )

        val event = createHighRiskVehicleEvent()
        manager.handleSafetyEvent(event)

        assertTrue("Torch should be active initially", manager.isTorchActive.value)

        // Wait for timeout to trigger and complete job
        manager.awaitCompletion()

        assertFalse("Torch must turn OFF when maxActiveTimeMs timeout expires", fakeTorch.currentTorchState)
        assertFalse("isTorchActive must be false after timeout", manager.isTorchActive.value)
    }

    @Test
    fun test11_lifecycleStop_torchTurnsOffImmediately() = runBlocking {
        val fakeTorch = FakeTorchController()
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope,
            initialConfig = VisibilityAssistConfig(
                pattern = TorchPattern.SOLID,
                solidDurationMs = 5000L
            )
        )

        val event = createHighRiskVehicleEvent()
        manager.handleSafetyEvent(event)

        assertTrue("Torch is active", manager.isTorchActive.value)

        // Lifecycle stop invoked (e.g. app goes to background or destroyed)
        manager.stop()

        assertFalse("Torch must be OFF immediately on stop()", fakeTorch.currentTorchState)
        assertFalse("isTorchActive must be false immediately on stop()", manager.isTorchActive.value)
    }

    @Test
    fun test12_torchApiFailure_phase5ContinuesGracefully() = runBlocking {
        val fakeTorch = FakeTorchController(shouldThrowOnSet = true)
        val scope = CoroutineScope(Dispatchers.Default)
        val manager = VisibilityAssistManager(
            torchController = fakeTorch,
            scope = scope
        )

        val event = createHighRiskVehicleEvent()

        // Should not throw or crash
        val result = manager.handleSafetyEvent(event)
        assertTrue("handleSafetyEvent returns true even if subsequent hardware call fails gracefully", result)

        manager.awaitCompletion()
        assertFalse("isTorchActive should safely reset to false after hardware error", manager.isTorchActive.value)
    }
}

