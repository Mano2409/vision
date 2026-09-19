package com.visionnav.ui.state

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

enum class NavigationStatus {
    IDLE,
    SEARCHING,
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
    val gpsAccuracyMeters: Float = 3.8f,
    val headingDegrees: Float? = 184f
)

data class NavUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val navigationStatus: NavigationStatus = NavigationStatus.IDLE,
    val destinationName: String? = null,
    val currentManeuver: ManeuverInstruction? = null,
    val nextManeuver: ManeuverInstruction? = null,
    val activeObstacles: List<DetectedObstacle> = emptyList(),
    val latestSpokenMessage: String = "Ready. Tap the bottom surface to speak.",
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val isDevHudVisible: Boolean = true,
    val isSimulatorDrawerOpen: Boolean = false
)

