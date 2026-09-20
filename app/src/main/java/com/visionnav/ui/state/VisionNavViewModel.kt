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
import com.visionnav.location.HeadingTracker
import com.visionnav.location.IHeadingProvider
import com.visionnav.location.ILocationProvider
import com.visionnav.location.LocationProvider
import com.visionnav.location.NavLocation
import com.visionnav.routing.CompositeGeocodingClient
import com.visionnav.routing.GeocodingException
import com.visionnav.routing.GeocodingResult
import com.visionnav.routing.IGeocodingClient
import com.visionnav.routing.MockGeocodingClient
import com.visionnav.simulator.SimulatorHeadingProvider
import com.visionnav.simulator.SimulatorLocationProvider
import com.visionnav.environment.EntranceSearchResult
import com.visionnav.environment.EnvironmentUnderstandingEngine
import com.visionnav.environment.PathAnalysisResult
import com.visionnav.environment.PathStatus
import com.visionnav.environment.SpecializedEnvironmentDetector
import com.visionnav.environment.WalkingPathAnalyzer
import com.visionnav.navigation.HapticFeedbackManager
import com.visionnav.safety.SafetyEventDispatcher
import com.visionnav.safety.SafetyRiskEngine
import com.visionnav.safety.TorchPattern
import com.visionnav.safety.VisibilityAssistManager
import com.visionnav.vision.CameraFrameAnalyzer
import com.visionnav.vision.CameraManager
import com.visionnav.vision.DetectionFilter
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.IYoloDetector
import com.visionnav.vision.MockYoloDetector
import com.visionnav.vision.ObjectTracker
import com.visionnav.vision.VisionPerformanceMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VisionNavViewModel(application: Application) : AndroidViewModel(application) {

    private val hapticManager = HapticManager(application)
    private val hapticFeedbackManager = HapticFeedbackManager(application)
    private val voiceSpeaker = VoiceSpeaker(application)
    private val audioQueue = AudioQueueManager(voiceSpeaker, hapticManager, viewModelScope)

    // Phase 5 Vision & Safety Subsystem
    val safetyRiskEngine = SafetyRiskEngine()
    val cameraManager = CameraManager(application)
    val visibilityAssistManager = VisibilityAssistManager(cameraManager, viewModelScope)
    private val safetyEventDispatcher = SafetyEventDispatcher(audioQueue, hapticFeedbackManager, visibilityAssistManager, viewModelScope)
    val yoloDetector: IYoloDetector = MockYoloDetector()
    val detectionFilter = DetectionFilter()
    val objectTracker = ObjectTracker()
    val environmentEngine = EnvironmentUnderstandingEngine()
    val walkingPathAnalyzer = WalkingPathAnalyzer()
    val specializedDetector = SpecializedEnvironmentDetector(audioQueue)
    private val _pathAnalysis = MutableStateFlow(PathAnalysisResult(status = PathStatus.UNKNOWN, confidence = 0f, corridorObstructionRatio = 0f))
    val pathAnalysis: StateFlow<PathAnalysisResult> = _pathAnalysis.asStateFlow()

    val frameAnalyzer = CameraFrameAnalyzer { frameBytes, width, height, rotation, isLowLight ->
        safetyRiskEngine.reportLowLight(isLowLight)
        viewModelScope.launch {
            try {
                val rawDetections = yoloDetector.detect(frameBytes, width, height, rotation)
                val stableDetections = detectionFilter.filter(rawDetections)
                val trackedObjects = objectTracker.update(stableDetections)
                safetyRiskEngine.evaluateFrame(trackedObjects)
                val snapshot = environmentEngine.evaluateFrame(trackedObjects)
                val pathResult = walkingPathAnalyzer.analyze(snapshot)
                _pathAnalysis.value = pathResult
            } catch (e: Exception) {
                // Analyzer error must not crash the navigation
            }
        }
    }

    // Hardware Providers (Live Mode)
    private val liveLocationProvider = LocationProvider(application)
    private val liveHeadingTracker = HeadingTracker(application)

    // Simulated Providers (Offline/Dev Mode)
    private val simulatorLocationProvider = SimulatorLocationProvider()
    private val simulatorHeadingProvider = SimulatorHeadingProvider()

    // Geocoding Clients
    private val compositeGeocodingClient: IGeocodingClient = CompositeGeocodingClient()
    private val mockGeocodingClient: IGeocodingClient = MockGeocodingClient()

    private var activeLocationProvider: ILocationProvider = liveLocationProvider
    private var activeHeadingProvider: IHeadingProvider = liveHeadingTracker

    private var locationObservationJob: Job? = null
    private var headingObservationJob: Job? = null

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

        // Bind safety risk engine events to audio and haptic dispatcher
        safetyEventDispatcher.bind(safetyRiskEngine)

        // Observe safety state and map active hazards to UI obstacles for spatial overlay
        viewModelScope.launch {
            safetyRiskEngine.safetyState.collect { safetyState ->
                val obstacles = safetyState.activeHazards.map { hazard ->
                    val zone = when {
                        hazard.currentBox.centerX < 0.33f -> SpatialZone.LEFT
                        hazard.currentBox.centerX > 0.66f -> SpatialZone.RIGHT
                        else -> SpatialZone.CENTER
                    }
                    val proximity = when (hazard.distance) {
                        DistanceCategory.VERY_NEAR -> ProximityLevel.CRITICAL
                        DistanceCategory.NEAR -> ProximityLevel.NEAR
                        DistanceCategory.MEDIUM -> ProximityLevel.MEDIUM
                        DistanceCategory.FAR, DistanceCategory.UNKNOWN -> ProximityLevel.FAR
                    }
                    DetectedObstacle(
                        id = "hazard_${hazard.trackId}",
                        label = hazard.detectionClass.label,
                        confidence = 0.90f,
                        boundingBox = NormalizedBoundingBox(
                            hazard.currentBox.left,
                            hazard.currentBox.top,
                            hazard.currentBox.right,
                            hazard.currentBox.bottom
                        ),
                        zone = zone,
                        proximity = proximity,
                        isBlockingPath = (zone == SpatialZone.CENTER && proximity == ProximityLevel.CRITICAL)
                    )
                }
                _uiState.update { it.copy(activeObstacles = obstacles) }
            }
        }

        // Observe torch state for UI feedback
        viewModelScope.launch {
            visibilityAssistManager.isTorchActive.collect { isActive ->
                _uiState.update { it.copy(isTorchActive = isActive) }
            }
        }

        // Start observing location and heading streams
        bindLocationAndHeadingProviders()
    }

    /**
     * Connects observers to either live hardware or simulated provider flows.
     */
    private fun bindLocationAndHeadingProviders() {
        locationObservationJob?.cancel()
        headingObservationJob?.cancel()

        activeLocationProvider.start()
        activeHeadingProvider.start()

        locationObservationJob = viewModelScope.launch {
            activeLocationProvider.locationFlow.collect { loc ->
                _uiState.update { current ->
                    current.copy(
                        currentLocation = loc,
                        telemetry = current.telemetry.copy(
                            latitude = if (loc.isAvailable) loc.latitude else current.telemetry.latitude,
                            longitude = if (loc.isAvailable) loc.longitude else current.telemetry.longitude,
                            gpsAccuracyMeters = loc.accuracyMeters,
                            locationConfidence = loc.confidence,
                            isGpsAvailable = loc.isAvailable,
                            isOutlier = loc.isOutlier,
                            isSimulatorMode = current.isSimulatorMode
                        )
                    )
                }
            }
        }

        headingObservationJob = viewModelScope.launch {
            activeHeadingProvider.headingFlow.collect { heading ->
                _uiState.update { current ->
                    current.copy(
                        telemetry = current.telemetry.copy(
                            headingDegrees = heading
                        )
                    )
                }
            }
        }
    }

    /**
     * Toggles between Live Hardware sensors and Offline Simulator.
     */
    fun toggleSimulationMode() {
        hapticManager.vibrateClick()
        val newSimMode = !_uiState.value.isSimulatorMode

        activeLocationProvider.stop()
        activeHeadingProvider.stop()

        if (newSimMode) {
            activeLocationProvider = simulatorLocationProvider
            activeHeadingProvider = simulatorHeadingProvider
        } else {
            activeLocationProvider = liveLocationProvider
            activeHeadingProvider = liveHeadingTracker
        }

        _uiState.update {
            it.copy(
                isSimulatorMode = newSimMode,
                telemetry = it.telemetry.copy(isSimulatorMode = newSimMode),
                latestSpokenMessage = if (newSimMode) "Switched to Simulator Mode" else "Switched to Live GPS Mode"
            )
        }

        bindLocationAndHeadingProviders()
        speakWithPriority(
            if (newSimMode) "Simulator Mode enabled." else "Live GPS Mode enabled.",
            AudioPriority.SYSTEM_FEEDBACK
        )
    }

    fun startLocationUpdates() {
        activeLocationProvider.start()
        activeHeadingProvider.start()
    }

    fun stopLocationUpdates() {
        activeLocationProvider.stop()
        activeHeadingProvider.stop()
    }

    // ==========================================
    // SIMULATOR CONTROLS (PHASE 3)
    // ==========================================

    fun simulatorStepForward() {
        hapticManager.vibrateClick()
        simulatorLocationProvider.stepForward()
    }

    fun simulatorRotateHeading(deltaDegrees: Float) {
        hapticManager.vibrateClick()
        simulatorHeadingProvider.rotateBy(deltaDegrees)
    }

    fun simulatorInjectGpsOutlier() {
        hapticManager.vibrateClick()
        simulatorLocationProvider.injectOutlierJump()
        speakWithPriority("GPS outlier jump injected.", AudioPriority.SYSTEM_FEEDBACK)
    }

    fun simulatorSetLowAccuracy() {
        hapticManager.vibrateClick()
        simulatorLocationProvider.setLowAccuracy()
        speakWithPriority("Low GPS accuracy injected.", AudioPriority.SYSTEM_FEEDBACK)
    }

    // ==========================================
    // VOICE & NAVIGATION INTERACTIONS
    // ==========================================

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
                latestSpokenMessage = "Listening... Speak destination, e.g., 'Navigate to Chennai Central'"
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
            is VoiceCommand.StartNavigation -> {
                speakWithPriority("Navigation started.", AudioPriority.ROUTINE_NAVIGATION)
            }
            is VoiceCommand.PauseNavigation -> {
                speakWithPriority("Navigation paused.", AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.ResumeNavigation -> {
                speakWithPriority("Navigation resumed.", AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.StopNavigation -> {
                resetToIdle()
                speakWithPriority("Navigation stopped.", AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.RepeatInstruction -> {
                onManeuverCardClicked()
            }
            is VoiceCommand.WhereAmI -> {
                val loc = _uiState.value.currentLocation
                val currentManeuver = _uiState.value.currentManeuver
                val msg = when {
                    loc.isAvailable -> {
                        val heading = _uiState.value.telemetry.headingDegrees?.toInt() ?: 0
                        "Current coordinates: ${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}. Facing $heading degrees. GPS accuracy: ±${loc.accuracyMeters?.toInt() ?: 0} meters."
                    }
                    currentManeuver != null -> {
                        "Currently navigating to ${_uiState.value.destinationName}. Next step: ${currentManeuver.primaryText}."
                    }
                    else -> "VisionNav is ready. GPS active. Tap to speak a destination."
                }
                speakWithPriority(msg, AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.WhatsAhead -> {
                val now = System.currentTimeMillis()
                val safetyState = safetyRiskEngine.safetyState.value
                val isFresh = safetyRiskEngine.isDataFresh(now, timeoutMs = 3000L)
                val hazards = safetyRiskEngine.getActiveHazards()

                val msg = when {
                    !safetyState.isVisionAvailable -> {
                        "Vision is currently unavailable."
                    }
                    !isFresh -> {
                        "Vision data is stale. Vision unavailable."
                    }
                    safetyState.isLowLight -> {
                        "Low visibility. Path cannot be reliably verified."
                    }
                    hazards.isNotEmpty() -> {
                        val first = hazards.first()
                        "${first.detectionClass.label.replaceFirstChar { it.uppercase() }} ahead, approximately ${first.distance.label.lowercase()}."
                    }
                    else -> {
                        "Path ahead appears clear. Continue with caution."
                    }
                }
                speakWithPriority(msg, AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.RepeatHazard -> {
                val recentHazard = safetyRiskEngine.getLatestActiveHazard(System.currentTimeMillis(), maxAgeMs = 10000L)
                val msg = if (recentHazard != null) {
                    "Recent hazard: ${recentHazard.detectionClass.label} ${recentHazard.motion.name.lowercase()} at ${recentHazard.distance.label.lowercase()}."
                } else {
                    "No recent hazard warnings."
                }
                speakWithPriority(msg, AudioPriority.SYSTEM_FEEDBACK)
            }
            is VoiceCommand.Unknown -> {
                processSpokenDestination(command.rawText)
            }
        }
    }

    /**
     * Phase 3 Geocoding Flow:
     * Spoken destination string -> GeocodingClient -> Candidates -> Confirmation
     */
    fun processSpokenDestination(destinationQuery: String) {
        viewModelScope.launch {
            val searchMsg = "Searching location for \"$destinationQuery\"..."
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.PROCESSING,
                    navigationStatus = NavigationStatus.SEARCHING,
                    destinationName = destinationQuery,
                    telemetry = it.telemetry.copy(geocodingStatus = "SEARCHING"),
                    latestSpokenMessage = searchMsg
                )
            }
            audioQueue.post(AudioMessage(text = searchMsg, priority = AudioPriority.SYSTEM_FEEDBACK))

            try {
                val client = if (_uiState.value.isSimulatorMode) mockGeocodingClient else compositeGeocodingClient
                val results = client.search(destinationQuery)

                when {
                    results.isEmpty() -> {
                        handleGeocodingNoResults(destinationQuery)
                    }
                    results.size == 1 -> {
                        // Single unambiguous result found -> Confirm directly
                        selectDestinationCandidate(results.first())
                    }
                    else -> {
                        // Multiple results found -> Prompt for confirmation / disambiguation
                        handleAmbiguousDestinations(destinationQuery, results)
                    }
                }
            } catch (e: GeocodingException) {
                handleGeocodingError(destinationQuery, e)
            } catch (e: Exception) {
                handleGeocodingError(
                    destinationQuery,
                    GeocodingException(com.visionnav.routing.GeocodingErrorType.SERVER_ERROR, e.message ?: "Unknown error")
                )
            }
        }
    }

    private fun handleAmbiguousDestinations(query: String, candidates: List<GeocodingResult>) {
        val firstTwo = candidates.take(2)
        val candidate1Name = firstTwo[0].displayName.substringBefore(",")
        val candidate2Name = firstTwo[1].displayName.substringBefore(",")
        val disambiguationMsg = "I found ${candidates.size} locations for $query. Did you mean $candidate1Name or $candidate2Name?"

        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                navigationStatus = NavigationStatus.AMBIGUOUS_DESTINATION,
                ambiguousCandidates = candidates,
                telemetry = it.telemetry.copy(geocodingStatus = "AMBIGUOUS"),
                latestSpokenMessage = disambiguationMsg
            )
        }

        audioQueue.post(AudioMessage(text = disambiguationMsg, priority = AudioPriority.ROUTINE_NAVIGATION))
    }

    private fun handleGeocodingNoResults(query: String) {
        val noResultsMsg = "No location found for \"$query\". Please try another place name."
        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                navigationStatus = NavigationStatus.IDLE,
                telemetry = it.telemetry.copy(geocodingStatus = "NO_RESULTS"),
                latestSpokenMessage = noResultsMsg
            )
        }
        audioQueue.post(AudioMessage(text = noResultsMsg, priority = AudioPriority.SYSTEM_FEEDBACK))
    }

    private fun handleGeocodingError(query: String, error: GeocodingException) {
        val errorMsg = when (error.errorType) {
            com.visionnav.routing.GeocodingErrorType.NETWORK_ERROR ->
                "Network error while searching destination. Check internet or switch to simulator mode."
            com.visionnav.routing.GeocodingErrorType.RATE_LIMITED ->
                "Search service is temporarily busy. Please wait a moment."
            else -> "Could not find destination \"$query\". Please try again."
        }

        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                navigationStatus = NavigationStatus.IDLE,
                telemetry = it.telemetry.copy(geocodingStatus = "ERROR"),
                latestSpokenMessage = errorMsg
            )
        }
        audioQueue.post(AudioMessage(text = errorMsg, priority = AudioPriority.SYSTEM_FEEDBACK))
    }

    /**
     * Confirms the selected geocoding candidate destination and prepares for Phase 4 Valhalla routing.
     */
    fun selectDestinationCandidate(candidate: GeocodingResult) {
        val title = candidate.displayName.substringBefore(",")
        val confirmationMsg = "Destination confirmed: $title. Coordinates: ${String.format("%.4f", candidate.latitude)}, ${String.format("%.4f", candidate.longitude)}. Ready for route calculation."

        _uiState.update {
            it.copy(
                voiceState = VoiceState.SPEAKING,
                navigationStatus = NavigationStatus.DESTINATION_CONFIRMED,
                destinationName = title,
                confirmedDestination = candidate,
                ambiguousCandidates = emptyList(),
                telemetry = it.telemetry.copy(geocodingStatus = "RESOLVED"),
                currentManeuver = ManeuverInstruction(
                    id = "m1",
                    direction = TurnDirection.STRAIGHT,
                    primaryText = "Walk straight towards $title",
                    secondaryText = "${candidate.addressSnippet ?: "Destination path"} (Ready for Phase 4 Valhalla)",
                    distanceMeters = 350
                ),
                latestSpokenMessage = confirmationMsg
            )
        }

        audioQueue.post(AudioMessage(text = confirmationMsg, priority = AudioPriority.ROUTINE_NAVIGATION))
    }

    fun startNavigation() {
        val startMsg = "Starting walking navigation. Head straight along Pine Street."
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
            boundingBox = NormalizedBoundingBox(0.30f, 0.15f, 0.70f, 0.90f),
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
                    nextManeuver = null,
                    latestSpokenMessage = maneuverText
                )
            }
            speakWithPriority(maneuverText, AudioPriority.ROUTINE_NAVIGATION)
        } else {
            simulateArrival()
        }
    }

    fun simulateArrival() {
        val arrivalMsg = "You have arrived at ${_uiState.value.destinationName ?: "your destination"}."
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
                isSimulatorDrawerOpen = it.isSimulatorDrawerOpen,
                isSimulatorMode = it.isSimulatorMode,
                telemetry = it.telemetry.copy(geocodingStatus = "READY")
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

    fun setVehicleVisibilityAssistEnabled(enabled: Boolean) {
        visibilityAssistManager.setEnabled(enabled)
        _uiState.update { it.copy(isVehicleVisibilityAssistEnabled = enabled) }
    }

    fun setTorchPattern(pattern: TorchPattern) {
        visibilityAssistManager.setPattern(pattern)
        _uiState.update { it.copy(torchPattern = pattern) }
    }

    /**
     * Phase 6C: Discovers the nearest doorway or entrance.
     */
    fun findNearestEntrance(): EntranceSearchResult {
        return specializedDetector.findNearestEntrance()
    }

    override fun onCleared() {
        super.onCleared()
        locationObservationJob?.cancel()
        headingObservationJob?.cancel()
        activeLocationProvider.stop()
        activeHeadingProvider.stop()
        cameraManager.stopCamera()
        visibilityAssistManager.release()
        safetyEventDispatcher.unbind()
        environmentEngine.clear()
        walkingPathAnalyzer.reset()
        specializedDetector.clear()
        yoloDetector.close()
        audioQueue.stopAll()
        voiceSpeaker.shutdown()
        voiceRecognizer.destroy()
    }
}
