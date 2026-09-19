package com.visionnav.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VisionNavViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NavUiState())
    val uiState: StateFlow<NavUiState> = _uiState.asStateFlow()

    fun onVoiceSurfaceClicked() {
        val currentState = _uiState.value
        when (currentState.voiceState) {
            VoiceState.IDLE -> {
                startListening()
            }
            VoiceState.LISTENING -> {
                // User tapped again while listening -> process mock input
                processSpokenDestination("Central Park")
            }
            VoiceState.PROCESSING, VoiceState.SPEAKING -> {
                // User tapped to interrupt
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }

    fun onManeuverCardClicked() {
        // Repeat latest instruction
        val maneuver = _uiState.value.currentManeuver
        if (maneuver != null) {
            speakMessage("In ${maneuver.distanceMeters} meters, ${maneuver.primaryText}")
        } else {
            speakMessage(_uiState.value.latestSpokenMessage)
        }
    }

    fun toggleDevHud() {
        _uiState.update { it.copy(isDevHudVisible = !it.isDevHudVisible) }
    }

    fun toggleSimulatorDrawer() {
        _uiState.update { it.copy(isSimulatorDrawerOpen = !it.isSimulatorDrawerOpen) }
    }

    fun startListening() {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.LISTENING,
                latestSpokenMessage = "Listening... Speak your destination."
            )
        }
    }

    fun processSpokenDestination(destination: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.PROCESSING,
                    navigationStatus = NavigationStatus.SEARCHING,
                    destinationName = destination,
                    latestSpokenMessage = "Searching walking route to $destination..."
                )
            }
            delay(1200) // Simulate Valhalla geocoding & route calculation

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
                    latestSpokenMessage = "Route found to $destination. Total distance 450 meters. Tap to start navigation."
                )
            }
        }
    }

    fun startNavigation() {
        _uiState.update {
            it.copy(
                navigationStatus = NavigationStatus.NAVIGATING,
                voiceState = VoiceState.IDLE,
                latestSpokenMessage = "Starting walking navigation. Head straight."
            )
        }
    }

    fun injectObstacle(zone: SpatialZone, proximity: ProximityLevel, label: String) {
        val (left, right) = when (zone) {
            SpatialZone.LEFT -> 0.05f to 0.30f
            SpatialZone.CENTER -> 0.35f to 0.65f
            SpatialZone.RIGHT -> 0.70f to 0.95f
        }
        val (top, bottom) = when (proximity) {
            ProximityLevel.CRITICAL -> 0.25f to 0.85f // Large bounding box = close
            ProximityLevel.NEAR -> 0.35f to 0.75f
            ProximityLevel.MEDIUM -> 0.45f to 0.65f
            ProximityLevel.FAR -> 0.50f to 0.60f     // Small bounding box = far
        }

        val obstacle = DetectedObstacle(
            id = "obs_${System.currentTimeMillis()}",
            label = label,
            confidence = 0.89f,
            boundingBox = NormalizedBoundingBox(left, top, right, bottom),
            zone = zone,
            proximity = proximity,
            isBlockingPath = (zone == SpatialZone.CENTER && (proximity == ProximityLevel.CRITICAL || proximity == ProximityLevel.NEAR))
        )

        val spokenWarning = when {
            obstacle.isBlockingPath -> "Obstacle ahead in center: $label. Move slightly ${if (zone == SpatialZone.CENTER) "left" else "around"}."
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
    }

    fun clearObstacles() {
        _uiState.update {
            it.copy(
                activeObstacles = emptyList(),
                voiceState = VoiceState.IDLE,
                latestSpokenMessage = "Path clear. Continue straight."
            )
        }
    }

    fun stepToNextManeuver() {
        val next = _uiState.value.nextManeuver
        if (next != null) {
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
                    latestSpokenMessage = "Now, ${next.primaryText} in ${next.distanceMeters} meters."
                )
            }
        } else {
            simulateArrival()
        }
    }

    fun simulateArrival() {
        _uiState.update {
            it.copy(
                navigationStatus = NavigationStatus.ARRIVED,
                currentManeuver = null,
                nextManeuver = null,
                activeObstacles = emptyList(),
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = "You have arrived at your destination."
            )
        }
    }

    fun resetToIdle() {
        _uiState.update {
            NavUiState(
                isDevHudVisible = it.isDevHudVisible,
                isSimulatorDrawerOpen = it.isSimulatorDrawerOpen
            )
        }
    }

    private fun speakMessage(msg: String) {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                latestSpokenMessage = msg
            )
        }
    }
}

