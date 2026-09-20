package com.visionnav.ui.state

import com.visionnav.location.LocationConfidence
import com.visionnav.location.NavLocation
import com.visionnav.routing.GeocodingResult
import com.visionnav.safety.TorchPattern

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

enum class NavigationStatus {
    IDLE,
    SEARCHING,
    AMBIGUOUS_DESTINATION,
    DESTINATION_CONFIRMED,
    ROUTE_READY,
    NAVIGATING,
    ARRIVED
}

enum class SpatialZone {
    LEFT,
    CENTER,
    RIGHT
}

enum class ProximityLevel {
    FAR,
    MEDIUM,
    NEAR,
    CRITICAL
}

enum class TurnDirection {
    STRAIGHT,
    LEFT,
    SLIGHT_LEFT,
    RIGHT,
    SLIGHT_RIGHT,
    U_TURN,
    DESTINATION
}

data class NormalizedBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DetectedObstacle(
    val id: String,
    val label: String,
    val confidence: Float,
    val boundingBox: NormalizedBoundingBox,
    val zone: SpatialZone,
    val proximity: ProximityLevel,
    val isBlockingPath: Boolean
)

data class ManeuverInstruction(
    val id: String,
    val direction: TurnDirection,
    val primaryText: String,
    val secondaryText: String,
    val distanceMeters: Int
)

data class SystemTelemetry(
    val fps: Float = 28.5f,
    val inferenceLatencyMs: Long = 34L,
    val latitude: Double? = 13.0827,
    val longitude: Double? = 80.2707,
    val gpsAccuracyMeters: Float? = 4.2f,
    val headingDegrees: Float? = 45.0f,
    val locationConfidence: LocationConfidence = LocationConfidence.HIGH,
    val isGpsAvailable: Boolean = true,
    val isOutlier: Boolean = false,
    val isSimulatorMode: Boolean = false,
    val geocodingStatus: String = "READY"
)

data class NavUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val navigationStatus: NavigationStatus = NavigationStatus.IDLE,
    val destinationName: String? = null,
    val confirmedDestination: GeocodingResult? = null,
    val ambiguousCandidates: List<GeocodingResult> = emptyList(),
    val currentLocation: NavLocation = NavLocation.UNAVAILABLE,
    val currentManeuver: ManeuverInstruction? = null,
    val nextManeuver: ManeuverInstruction? = null,
    val activeObstacles: List<DetectedObstacle> = emptyList(),
    val latestSpokenMessage: String = "Ready. Tap the bottom surface to speak.",
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val isDevHudVisible: Boolean = true,
    val isSimulatorDrawerOpen: Boolean = false,
    val isSimulatorMode: Boolean = false,
    val isVehicleVisibilityAssistEnabled: Boolean = true,
    val torchPattern: TorchPattern = TorchPattern.SOS,
    val isTorchActive: Boolean = false
)
