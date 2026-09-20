package com.visionnav.navigation

import com.visionnav.location.CurrentLocation
import com.visionnav.location.HeadingTracker
import com.visionnav.routing.Destination
import com.visionnav.routing.ManeuverInstruction
import com.visionnav.routing.ManeuverType
import com.visionnav.routing.NavigationRoute
import com.visionnav.routing.RoutePoint
import com.visionnav.routing.ValhallaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stages of announcement gates for a single maneuver, ensuring each is spoken once.
 */
enum class AnnouncementStage {
    NONE,
    ADVANCE,
    NEAR,
    NOW,
    COMPLETED
}

/**
 * Central Navigation Brain coordinating route progression, turn guidance,
 * heading awareness, and debounced rerouting for visually impaired pedestrians.
 *
 * Emits NavigationEvents to decoupled listeners (AudioQueueManager, Haptics, UI)
 * and strictly never invokes Text-to-Speech directly.
 */
class NavigationController(
    private val valhallaClient: ValhallaClient = ValhallaClient(),
    private val reroutingEngine: ReroutingEngine = ReroutingEngine(),
    private val arrivalDetector: ArrivalDetector = ArrivalDetector(),
    val advanceDistanceMeters: Float = 50.0f,
    val nearDistanceMeters: Float = 20.0f,
    val nowDistanceMeters: Float = 8.0f,
    val wrongDirectionAngleDegrees: Float = 120.0f,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _state = MutableStateFlow(NavigationState.IDLE)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<NavigationEvent> = _events.asSharedFlow()

    private var activeRoute: NavigationRoute? = null
    private var destination: Destination? = null
    private var currentManeuverIndex = 0
    private var currentAnnouncementStage = AnnouncementStage.NONE
    private var lastImportantInstructionText: String = ""
    private var lastWrongDirectionTimestampMs = 0L
    private val wrongDirectionCooldownMs = 15000L

    var isNetworkAvailable: Boolean = true
        private set

    /**
     * Loads a newly calculated route and transitions to ROUTE_READY.
     */
    fun loadRoute(route: NavigationRoute, targetDestination: Destination? = null) {
        activeRoute = route
        destination = targetDestination
        currentManeuverIndex = 0
        currentAnnouncementStage = AnnouncementStage.NONE
        reroutingEngine.reset()
        arrivalDetector.reset()

        lastImportantInstructionText = route.summary.text
        _state.value = NavigationState.ROUTE_READY
        _events.tryEmit(NavigationEvent.RouteReady(route.summary))
    }

    /**
     * Begins active turn-by-turn guidance on the loaded route.
     */
    fun startNavigation() {
        val route = activeRoute ?: return
        if (_state.value != NavigationState.ROUTE_READY && _state.value != NavigationState.PAUSED) return

        _state.value = NavigationState.NAVIGATING
        _events.tryEmit(NavigationEvent.NavigationStarted)

        val firstManeuver = route.maneuvers.firstOrNull()
        if (firstManeuver != null) {
            val text = firstManeuver.instructionText
            lastImportantInstructionText = text
            _events.tryEmit(
                NavigationEvent.ContinueStraight(
                    distanceMeters = firstManeuver.distanceMeters,
                    streetName = firstManeuver.streetName
                )
            )
        }
    }

    /**
     * Pauses turn announcements while preserving route and progress.
     */
    fun pauseNavigation() {
        if (_state.value == NavigationState.NAVIGATING ||
            _state.value == NavigationState.POSSIBLY_OFF_ROUTE
        ) {
            _state.value = NavigationState.PAUSED
            _events.tryEmit(NavigationEvent.NavigationPaused)
        }
    }

    /**
     * Resumes navigation, synchronizing with the user's latest physical position.
     */
    fun resumeNavigation(latestLocation: CurrentLocation) {
        if (_state.value != NavigationState.PAUSED) return

        _state.value = NavigationState.NAVIGATING
        _events.tryEmit(NavigationEvent.NavigationResumed)

        val route = activeRoute
        if (route != null && latestLocation.isValid) {
            recalculateActiveManeuver(latestLocation, route)
        }
    }

    /**
     * Repeats the latest important instruction without altering state.
     */
    fun repeatLastInstruction() {
        val text = lastImportantInstructionText
        if (text.isNotBlank()) {
            _events.tryEmit(NavigationEvent.RepeatInstruction(text))
        }
    }

    /**
     * Core update loop executed on each GPS fix.
     */
    fun onLocationUpdate(location: CurrentLocation, headingDegrees: Float? = null) {
        val currentState = _state.value
        if (currentState != NavigationState.NAVIGATING &&
            currentState != NavigationState.POSSIBLY_OFF_ROUTE &&
            currentState != NavigationState.OFF_ROUTE
        ) {
            return
        }

        if (!location.isValid) return
        val route = activeRoute ?: return

        // 1. Check Arrival
        val lastPoint = route.points.lastOrNull()
        if (lastPoint != null && arrivalDetector.checkArrival(location, lastPoint)) {
            _state.value = NavigationState.ARRIVED
            val destName = destination?.displayName ?: "destination"
            lastImportantInstructionText = "You have arrived at $destName"
            _events.tryEmit(NavigationEvent.Arrived(destName))
            return
        }

        // 2. Check Off-Route Divergence
        val offRouteStatus = reroutingEngine.evaluate(location, route)
        when (offRouteStatus) {
            OffRouteStatus.CONFIRMED_OFF_ROUTE -> {
                _state.value = NavigationState.OFF_ROUTE
                val distance = reroutingEngine.calculateMinDistanceToRoute(location, route.points)
                _events.tryEmit(NavigationEvent.OffRoute(distance))
                triggerReroute(location)
                return
            }
            OffRouteStatus.POSSIBLY_OFF_ROUTE -> {
                _state.value = NavigationState.POSSIBLY_OFF_ROUTE
                val distance = reroutingEngine.calculateMinDistanceToRoute(location, route.points)
                _events.tryEmit(NavigationEvent.PossiblyOffRoute(distance))
            }
            OffRouteStatus.ON_ROUTE -> {
                if (_state.value == NavigationState.POSSIBLY_OFF_ROUTE) {
                    _state.value = NavigationState.NAVIGATING
                }
            }
        }

        // 3. Track Maneuver Progression
        val targetManeuver = if (currentManeuverIndex + 1 in route.maneuvers.indices) {
            route.maneuvers[currentManeuverIndex + 1]
        } else {
            route.maneuvers.getOrNull(currentManeuverIndex)
        }

        if (targetManeuver != null) {
            val distanceToManeuver = ArrivalDetector.calculateDistance(
                location.latitude,
                location.longitude,
                targetManeuver.coordinate.latitude,
                targetManeuver.coordinate.longitude
            )

            evaluateAnnouncementGates(distanceToManeuver, targetManeuver)

            // Step Completion: if within 'now' threshold (8m), advance to next step
            if (distanceToManeuver <= nowDistanceMeters && currentManeuverIndex < route.maneuvers.lastIndex) {
                currentManeuverIndex++
                currentAnnouncementStage = AnnouncementStage.NONE
                val nextManeuver = route.maneuvers[currentManeuverIndex]
                val continueText = "Continue onto ${nextManeuver.streetName} for ${nextManeuver.distanceMeters} meters"
                lastImportantInstructionText = continueText
                _events.tryEmit(
                    NavigationEvent.ContinueStraight(
                        distanceMeters = nextManeuver.distanceMeters,
                        streetName = nextManeuver.streetName
                    )
                )
            }
        }

        // 4. Heading / Wrong-Direction Detection
        if (headingDegrees != null && headingDegrees in 0f..360f) {
            checkWrongDirection(headingDegrees, route)
        }
    }

    private fun evaluateAnnouncementGates(
        distanceToManeuver: Float,
        maneuver: ManeuverInstruction
    ) {
        when {
            distanceToManeuver <= nowDistanceMeters && currentAnnouncementStage < AnnouncementStage.NOW -> {
                currentAnnouncementStage = AnnouncementStage.NOW
                val text = "Turn ${maneuver.type.name.lowercase().replace('_', ' ')} now"
                lastImportantInstructionText = text
                _events.tryEmit(NavigationEvent.TurnNow(maneuver.type, maneuver.streetName))
            }
            distanceToManeuver <= nearDistanceMeters && currentAnnouncementStage < AnnouncementStage.NEAR -> {
                currentAnnouncementStage = AnnouncementStage.NEAR
                val text = "In 20 meters, turn ${maneuver.type.name.lowercase().replace('_', ' ')}"
                lastImportantInstructionText = text
                _events.tryEmit(NavigationEvent.TurnSoon(maneuver.type, 20, maneuver.streetName))
            }
            distanceToManeuver <= advanceDistanceMeters && currentAnnouncementStage < AnnouncementStage.ADVANCE -> {
                currentAnnouncementStage = AnnouncementStage.ADVANCE
                val text = "In 50 meters, turn ${maneuver.type.name.lowercase().replace('_', ' ')} onto ${maneuver.streetName}"
                lastImportantInstructionText = text
                _events.tryEmit(NavigationEvent.TurnSoon(maneuver.type, 50, maneuver.streetName))
            }
        }
    }

    private fun checkWrongDirection(headingDegrees: Float, route: NavigationRoute) {
        val now = System.currentTimeMillis()
        if (now - lastWrongDirectionTimestampMs < wrongDirectionCooldownMs) return

        // Compute azimuth of current path segment
        if (route.points.size < 2) return
        val currentPoint = route.points.getOrNull(currentManeuverIndex) ?: return
        val nextPoint = route.points.getOrNull(currentManeuverIndex + 1) ?: return

        val segmentBearing = calculateBearing(
            currentPoint.latitude,
            currentPoint.longitude,
            nextPoint.latitude,
            nextPoint.longitude
        )

        var diff = abs(headingDegrees - segmentBearing)
        if (diff > 180f) diff = 360f - diff

        if (diff >= wrongDirectionAngleDegrees) {
            lastWrongDirectionTimestampMs = now
            lastImportantInstructionText = "Please turn around"
            _events.tryEmit(NavigationEvent.WrongDirection(diff))
        }
    }

    private fun triggerReroute(location: CurrentLocation) {
        val dest = destination ?: return

        if (!isNetworkAvailable) {
            _events.tryEmit(NavigationEvent.NetworkUnavailable)
            return
        }

        _state.value = NavigationState.REROUTING
        _events.tryEmit(NavigationEvent.ReroutingStarted)

        scope.launch {
            try {
                val newRoute = valhallaClient.getPedestrianRoute(location, dest)
                loadRoute(newRoute, dest)
                _state.value = NavigationState.NAVIGATING
                _events.tryEmit(NavigationEvent.ReroutingCompleted(newRoute))
            } catch (e: Exception) {
                _events.tryEmit(NavigationEvent.NavigationError("Rerouting failed: ${e.message}"))
                _state.value = NavigationState.NAVIGATING // Continue on existing route
            }
        }
    }

    fun setNetworkStatus(available: Boolean) {
        isNetworkAvailable = available
        if (!available && _state.value == NavigationState.NAVIGATING) {
            _events.tryEmit(NavigationEvent.NetworkUnavailable)
        }
    }

    private fun recalculateActiveManeuver(location: CurrentLocation, route: NavigationRoute) {
        var closestIdx = 0
        var minDistance = Float.MAX_VALUE

        for (i in route.maneuvers.indices) {
            val m = route.maneuvers[i]
            val dist = ArrivalDetector.calculateDistance(
                location.latitude,
                location.longitude,
                m.coordinate.latitude,
                m.coordinate.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
                closestIdx = i
            }
        }

        currentManeuverIndex = closestIdx
        currentAnnouncementStage = AnnouncementStage.NONE
    }

    fun getActiveRoute(): NavigationRoute? = activeRoute
    fun getCurrentManeuver(): ManeuverInstruction? = activeRoute?.maneuvers?.getOrNull(currentManeuverIndex)

    companion object {
        fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(Math.toRadians(lat2))
            val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                    sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
            val rad = atan2(y, x)
            val deg = Math.toDegrees(rad)
            return ((deg + 360) % 360).toFloat()
        }
    }
}

