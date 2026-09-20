package com.visionnav.safety

import android.util.Log
import com.visionnav.vision.DetectionClass
import com.visionnav.vision.MotionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Supported flashlight visibility patterns.
 */
enum class TorchPattern {
    SOS,
    SOLID
}

/**
 * Configuration parameters for the Visibility Assist feature.
 */
data class VisibilityAssistConfig(
    val isEnabled: Boolean = true,
    val pattern: TorchPattern = TorchPattern.SOS,
    val shortOnMs: Long = 250L,
    val shortOffMs: Long = 250L,
    val longOnMs: Long = 750L,
    val longOffMs: Long = 250L,
    val letterPauseMs: Long = 500L,
    val solidDurationMs: Long = 3000L,
    val maxActiveTimeMs: Long = 10000L,
    val cooldownMs: Long = 5000L
)

/**
 * Phase 5B: Manages rear-camera flashlight visibility assistance for pedestrians.
 *
 * When an existing Phase 5 high-risk approaching vehicle safety event is emitted,
 * this component temporarily pulses the flashlight in an SOS pattern to heighten
 * pedestrian visibility to approaching drivers.
 *
 * This component does NOT perform object detection, risk assessment, or TTS/haptic modification.
 */
class VisibilityAssistManager(
    private val torchController: ITorchController,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialConfig: VisibilityAssistConfig = VisibilityAssistConfig()
) {
    private val tag = "VisibilityAssistMgr"

    var config: VisibilityAssistConfig = initialConfig
        private set

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

    private var patternJob: Job? = null
    private var observationJob: Job? = null
    private var lastTriggerTimestampMs = 0L

    /**
     * Binds this manager to a stream of SafetyEvents emitted by the Phase 5 engine.
     */
    fun bind(events: Flow<SafetyEvent>): Job {
        observationJob?.cancel()
        return events
            .onEach { event -> handleSafetyEvent(event) }
            .launchIn(scope)
            .also { observationJob = it }
    }

    /**
     * Evaluates an incoming SafetyEvent and triggers the torch pattern if all criteria are satisfied.
     *
     * @param event The SafetyEvent emitted by the SafetyRiskEngine.
     * @param currentTimeMs Monotonic or wall-clock timestamp in milliseconds.
     * @return True if the torch pattern was triggered, false otherwise.
     */
    fun handleSafetyEvent(
        event: SafetyEvent,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Feature disabled check
        if (!config.isEnabled) {
            return false
        }

        // 2. Hardware availability check
        if (!torchController.isAvailable) {
            return false
        }

        // 3. Event relevance: Must be high-risk approaching vehicle
        if (!isHighRiskApproachingVehicle(event)) {
            return false
        }

        // 4. Duplicate trigger prevention: Do not restart if already running
        if (_isTorchActive.value || patternJob?.isActive == true) {
            Log.d(tag, "Torch pattern is already active. Ignoring duplicate event.")
            return false
        }

        // 5. Cooldown check: Prevent rapid re-triggering
        if (lastTriggerTimestampMs > 0L && (currentTimeMs - lastTriggerTimestampMs) < config.cooldownMs) {
            Log.d(tag, "Torch assist on cooldown (${currentTimeMs - lastTriggerTimestampMs}ms < ${config.cooldownMs}ms).")
            return false
        }

        lastTriggerTimestampMs = currentTimeMs
        startPattern()
        return true
    }

    /**
     * Determines whether an event represents a high-risk approaching vehicle.
     */
    fun isHighRiskApproachingVehicle(event: SafetyEvent): Boolean {
        return when (event) {
            is SafetyEvent.HazardDetected -> {
                event.detectionClass.isVehicle &&
                        (event.level == SafetyLevel.CRITICAL ||
                                (event.level == SafetyLevel.WARNING && event.motion == MotionState.APPROACHING))
            }

            is SafetyEvent.HazardEscalated -> {
                event.detectionClass.isVehicle &&
                        (event.newLevel == SafetyLevel.CRITICAL ||
                                (event.newLevel == SafetyLevel.WARNING && event.motion == MotionState.APPROACHING))
            }

            else -> false
        }
    }

    /**
     * Launches the configured blinking sequence under a strict safety timeout.
     */
    private fun startPattern() {
        patternJob?.cancel()
        _isTorchActive.value = true
        patternJob = scope.launch(dispatcher) {
            try {
                withTimeoutOrNull(config.maxActiveTimeMs) {
                    when (config.pattern) {
                        TorchPattern.SOS -> executeSosPattern()
                        TorchPattern.SOLID -> executeSolidPattern()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during torch execution: ${e.message}", e)
            } finally {
                safelySetTorch(false)
                _isTorchActive.value = false
            }
        }
    }

    /**
     * Suspends until the current pattern job finishes. Useful for lifecycle synchronization and tests.
     */
    suspend fun awaitCompletion() {
        patternJob?.join()
    }

    /**
     * Executes standard Morse SOS pattern:
     * S (...), pause, O (---), pause, S (...)
     */
    private suspend fun executeSosPattern() {
        // S: 3 short pulses
        repeat(3) { index ->
            safelySetTorch(true)
            delay(config.shortOnMs)
            safelySetTorch(false)
            if (index < 2) delay(config.shortOffMs)
        }

        delay(config.letterPauseMs)

        // O: 3 long pulses
        repeat(3) { index ->
            safelySetTorch(true)
            delay(config.longOnMs)
            safelySetTorch(false)
            if (index < 2) delay(config.longOffMs)
        }

        delay(config.letterPauseMs)

        // S: 3 short pulses
        repeat(3) { index ->
            safelySetTorch(true)
            delay(config.shortOnMs)
            safelySetTorch(false)
            if (index < 2) delay(config.shortOffMs)
        }
    }

    /**
     * Executes solid continuous light for configured duration.
     */
    private suspend fun executeSolidPattern() {
        safelySetTorch(true)
        delay(config.solidDurationMs)
        safelySetTorch(false)
    }

    private fun safelySetTorch(enabled: Boolean): Boolean {
        return try {
            torchController.setTorch(enabled)
        } catch (e: Exception) {
            Log.e(tag, "Torch controller failed: ${e.message}", e)
            false
        }
    }

    /**
     * Stops any running torch sequence immediately and turns the physical torch OFF.
     */
    fun stop() {
        patternJob?.cancel()
        patternJob = null
        safelySetTorch(false)
        _isTorchActive.value = false
    }

    /**
     * Releases observers and ensures torch is OFF.
     */
    fun release() {
        observationJob?.cancel()
        observationJob = null
        stop()
    }

    fun setEnabled(enabled: Boolean) {
        config = config.copy(isEnabled = enabled)
        if (!enabled) {
            stop()
        }
    }

    fun setPattern(pattern: TorchPattern) {
        config = config.copy(pattern = pattern)
    }

    fun updateConfig(newConfig: VisibilityAssistConfig) {
        config = newConfig
        if (!config.isEnabled) {
            stop()
        }
    }
}
