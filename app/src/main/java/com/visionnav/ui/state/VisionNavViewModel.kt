package com.visionnav.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionnav.audio.AudioMessage
import com.visionnav.audio.AudioPriority
import com.visionnav.audio.AudioQueueManager
import com.visionnav.audio.HapticManager
import com.visionnav.audio.VoiceCommand
import com.visionnav.audio.VoiceCommandParser
import com.visionnav.audio.VoiceRecognizer
import com.visionnav.audio.VoiceSpeaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VisionNavViewModel(application: Application) : AndroidViewModel(application) {

    private val hapticManager = HapticManager(application)
    private val voiceSpeaker = VoiceSpeaker(application)
    private val audioQueue = AudioQueueManager(voiceSpeaker, hapticManager, viewModelScope)

    private val voiceRecognizer = VoiceRecognizer(
        context = application,
        onSpeechResult = { recognizedText ->
            handleRecognizedSpeech(recognizedText)
        },
        onErrorOccurred = { errorMsg ->
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.IDLE,
                    latestSpokenMessage = errorMsg
                )
            }
            audioQueue.post(AudioMessage(text = errorMsg, priority = AudioPriority.SYSTEM_FEEDBACK))
        }
    )

    private val _uiState = MutableStateFlow(NavUiState())
    val uiState: StateFlow<NavUiState> = _uiState.asStateFlow()

    init {
        // Observe voice recognizer listening state
        viewModelScope.launch {
            voiceRecognizer.isListening.collect { isListening ->
                _uiState.update {
                    it.copy(voiceState = if (isListening) VoiceState.LISTENING else it.voiceState)
                }
            }
        }

        // Observe TTS speaking state
        viewModelScope.launch {
            voiceSpeaker.isSpeaking.collect { isSpeaking ->
                _uiState.update {
                    if (isSpeaking) {
                        it.copy(voiceState = VoiceState.SPEAKING)
                    } else if (it.voiceState == VoiceState.SPEAKING) {
                        it.copy(voiceState = VoiceState.IDLE)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun onVoiceSurfaceClicked() {
        hapticManager.vibrateClick()
        val currentState = _uiState.value

        when (currentState.voiceState) {
            VoiceState.IDLE -> {
                startListening()
            }
            VoiceState.LISTENING -> {
                voiceRecognizer.stopListening()
                _uiState.update { it.copy(voiceState = VoiceState.PROCESSING) }
            }
            VoiceState.PROCESSING, VoiceState.SPEAKING -> {
                audioQueue.stopAll()
                voiceRecognizer.cancel()
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }

    fun onManeuverCardClicked() {
        hapticManager.vibrateClick()
        val maneuver = _uiState.value.currentManeuver
        if (maneuver != null) {
            val speech = "In ${maneuver.distanceMeters} meters, ${maneuver.primaryText}"
            speakWithPriority(speech, AudioPriority.ROUTINE_NAVIGATION)
        } else {
            speakWithPriority(_uiState.value.latestSpokenMessage, AudioPriority.SYSTEM_FEEDBACK)
        }
    }

    fun toggleDevHud() {
        hapticManager.vibrateClick()
        _uiState.update { it.copy(isDevHudVisible = !it.isDevHudVisible) }
    }

    fun toggleSimulatorDrawer() {
        hapticManager.vibrateClick()
        _uiState.update { it.copy(isSimulatorDrawerOpen = !it.isSimulatorDrawerOpen) }
    }

    fun startListening() {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.LISTENING,
                latestSpokenMessage = "Listening... Speak your destination or command."
            )
        }
        voiceRecognizer.startListening()
    }

    private fun handleRecognizedSpeech(text: String) {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.PROCESSING,
                latestSpokenMessage = "Heard: \"$text\""
            )
        }

        when (val command = VoiceCommandParser.parse(text)) {
            is VoiceCommand.NavigateTo -> {
                processSpokenDestination(command.destination)
            }
            is VoiceCommand.StopNavigation -> {
                resetToIdle()
                speakWithPriority("Navigation stopped.", AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.RepeatInstruction -> {
                onManeuverCardClicked()
            }
            is VoiceCommand.WhereAmI -> {
                val currentManeuver = _uiState.value.currentManeuver
                val msg = if (currentManeuver != null) {
                    "Currently navigating to ${_uiState.value.destinationName}. Next step: ${currentManeuver.primaryText}."
                } else {
                    "VisionNav is ready. No active route. Tap to speak a destination."
                }
                speakWithPriority(msg, AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.WhatsAhead -> {
                val obstacles = _uiState.value.activeObstacles
                val msg = if (obstacles.isNotEmpty()) {
                    "Path warning: ${obstacles.first().label} detected in ${obstacles.first().zone.name.lowercase()} zone."
                } else {
                    "Path ahead is clear. Continue walking."
                }
                speakWithPriority(msg, AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.Unknown -> {
                processSpokenDestination(command.rawText)
            }
        }
    }

    fun processSpokenDestination(destination: String) {
        viewModelScope.launch {
            val searchMsg = "Searching walking route to $destination..."
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.PROCESSING,
                    navigationStatus = NavigationStatus.SEARCHING,
                    destinationName = destination,
                    latestSpokenMessage = searchMsg
                )
            }
            audioQueue.post(AudioMessage(text = searchMsg, priority = AudioPriority.SYSTEM_FEEDBACK))

            delay(1400) // Simulate Valhalla route fetching

            val routeFoundMsg = "Route found to $destination. Total distance 450 meters. Tap to start navigation."
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.SPEAKING,
                    navigationStatus = NavigationStatus.ROUTE_READY,
                    currentManeuver = ManeuverInstruction(
                        id = "m1",
                        direction = TurnDirection.STRAIGHT,
                        primaryText = "Walk straight along Pine Street",
                        secondaryText = "Follow path for 120 meters",
                        distanceMeters = 120
                    ),
                    nextManeuver = ManeuverInstruction(
                        id = "m2",
                        direction = TurnDirection.LEFT,
                        primaryText = "Turn left onto Oak Avenue",
                        secondaryText = "Pedestrian crossing ahead",
                        distanceMeters = 120
                    ),
                    latestSpokenMessage = routeFoundMsg
                )
            }
            audioQueue.post(AudioMessage(text = routeFoundMsg, priority = AudioPriority.ROUTINE_NAVIGATION))
        }
    }

    fun startNavigation() {
        val startMsg = "Starting walking navigation. Head straight for 120 meters."
        _uiState.update {
            it.copy(
                navigationStatus = NavigationStatus.NAVIGATING,
                voiceState = VoiceState.IDLE,
                latestSpokenMessage = startMsg
            )
        }
        speakWithPriority(startMsg, AudioPriority.ROUTINE_NAVIGATION)
    }

    /**
     * 🚨 P0 Non-Negotiable Critical Safety Event Trigger:
     * Fast-moving approaching vehicle intersects trajectory.
     * Fires <50ms hardware vibration, cuts off routine speech, and commands STOP.
     */
    fun triggerCriticalVehicleThreat(vehicleType: String = "Lorry") {
        val alertText = "STOP. ${vehicleType.uppercase()} APPROACHING."

        val obstacle = DetectedObstacle(
            id = "threat_${System.currentTimeMillis()}",
            label = vehicleType,
            confidence = 0.96f,
            boundingBox = NormalizedBoundingBox(0.30f, 0.15f, 0.70f, 0.90f), // Large expanding bbox
            zone = SpatialZone.CENTER,
            proximity = ProximityLevel.CRITICAL,
            isBlockingPath = true
        )

        _uiState.update {
            it.copy(
                activeObstacles = listOf(obstacle),
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = alertText
            )
        }

        // Send to AudioQueueManager: triggers immediate haptic shock, kills TTS, and speaks STOP
        audioQueue.post(
            AudioMessage(
                text = alertText,
                priority = AudioPriority.CRITICAL_SAFETY
            )
        )
    }

    fun clearCriticalVehicleThreat() {
        _uiState.update {
            it.copy(
                activeObstacles = emptyList(),
                voiceState = VoiceState.IDLE,
                latestSpokenMessage = "Vehicle passed. Path clear."
            )
        }
        audioQueue.onHazardClearanceReported()
    }

    fun injectObstacle(zone: SpatialZone, proximity: ProximityLevel, label: String) {
        val (left, right) = when (zone) {
            SpatialZone.LEFT -> 0.05f to 0.30f
            SpatialZone.CENTER -> 0.35f to 0.65f
            SpatialZone.RIGHT -> 0.70f to 0.95f
        }
        val (top, bottom) = when (proximity) {
            ProximityLevel.CRITICAL -> 0.25f to 0.85f
            ProximityLevel.NEAR -> 0.35f to 0.75f
            ProximityLevel.MEDIUM -> 0.45f to 0.65f
            ProximityLevel.FAR -> 0.50f to 0.60f
        }

        val isBlocking = (zone == SpatialZone.CENTER && (proximity == ProximityLevel.CRITICAL || proximity == ProximityLevel.NEAR))

        val obstacle = DetectedObstacle(
            id = "obs_${System.currentTimeMillis()}",
            label = label,
            confidence = 0.89f,
            boundingBox = NormalizedBoundingBox(left, top, right, bottom),
            zone = zone,
            proximity = proximity,
            isBlockingPath = isBlocking
        )

        val spokenWarning = when {
            obstacle.isBlockingPath -> "Obstacle ahead in center: $label. Move slightly left."
            zone == SpatialZone.LEFT -> "Caution: $label on your left."
            zone == SpatialZone.RIGHT -> "Caution: $label on your right."
            else -> "$label detected."
        }

        _uiState.update {
            it.copy(
                activeObstacles = listOf(obstacle),
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = spokenWarning
            )
        }

        audioQueue.post(
            AudioMessage(
                text = spokenWarning,
                priority = AudioPriority.STATIC_OBSTACLE
            )
        )
    }

    fun clearObstacles() {
        _uiState.update {
            it.copy(
                activeObstacles = emptyList(),
                voiceState = VoiceState.IDLE,
                latestSpokenMessage = "Path clear. Continue straight."
            )
        }
        audioQueue.onHazardClearanceReported()
    }

    fun stepToNextManeuver() {
        val next = _uiState.value.nextManeuver
        if (next != null) {
            val maneuverText = "In ${next.distanceMeters} meters, ${next.primaryText}."
            _uiState.update {
                it.copy(
                    currentManeuver = next,
                    nextManeuver = ManeuverInstruction(
                        id = "m3",
                        direction = TurnDirection.DESTINATION,
                        primaryText = "Destination on your right",
                        secondaryText = "Arriving at ${it.destinationName ?: "destination"}",
                        distanceMeters = 80
                    ),
                    latestSpokenMessage = maneuverText
                )
            }
            speakWithPriority(maneuverText, AudioPriority.ROUTINE_NAVIGATION)
        } else {
            simulateArrival()
        }
    }

    fun simulateArrival() {
        val arrivalMsg = "You have arrived at your destination."
        _uiState.update {
            it.copy(
                navigationStatus = NavigationStatus.ARRIVED,
                currentManeuver = null,
                nextManeuver = null,
                activeObstacles = emptyList(),
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = arrivalMsg
            )
        }
        speakWithPriority(arrivalMsg, AudioPriority.ROUTINE_NAVIGATION)
    }

    fun resetToIdle() {
        audioQueue.stopAll()
        voiceRecognizer.cancel()
        _uiState.update {
            NavUiState(
                isDevHudVisible = it.isDevHudVisible,
                isSimulatorDrawerOpen = it.isSimulatorDrawerOpen
            )
        }
    }

    private fun speakWithPriority(msg: String, priority: AudioPriority) {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = msg
            )
        }
        audioQueue.post(AudioMessage(text = msg, priority = priority))
    }

    override fun onCleared() {
        super.onCleared()
        audioQueue.stopAll()
        voiceSpeaker.shutdown()
        voiceRecognizer.destroy()
    }
}
