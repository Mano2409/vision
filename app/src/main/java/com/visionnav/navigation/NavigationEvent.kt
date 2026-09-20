package com.visionnav.navigation

import com.visionnav.routing.ManeuverType
import com.visionnav.routing.NavigationRoute
import com.visionnav.routing.RouteSummary

/**
 * Domain events emitted by the NavigationController.
 * Decoupled from UI and Text-to-Speech: consumers (AudioQueueManager, Haptics, UI)
 * subscribe to these events and render their appropriate presentation.
 */
sealed class NavigationEvent {
    data class RouteReady(val summary: RouteSummary) : NavigationEvent()
    object NavigationStarted : NavigationEvent()
    data class ContinueStraight(val distanceMeters: Int, val streetName: String) : NavigationEvent()
    data class TurnSoon(val direction: ManeuverType, val distanceMeters: Int, val streetName: String) : NavigationEvent()
    data class TurnNow(val direction: ManeuverType, val streetName: String) : NavigationEvent()
    data class WrongDirection(val headingDifferenceDegrees: Float) : NavigationEvent()
    data class PossiblyOffRoute(val distanceFromRouteMeters: Float) : NavigationEvent()
    data class OffRoute(val distanceFromRouteMeters: Float) : NavigationEvent()
    object ReroutingStarted : NavigationEvent()
    data class ReroutingCompleted(val newRoute: NavigationRoute) : NavigationEvent()
    object NetworkUnavailable : NavigationEvent()
    object NavigationPaused : NavigationEvent()
    object NavigationResumed : NavigationEvent()
    data class RepeatInstruction(val text: String) : NavigationEvent()
    data class Arrived(val destinationName: String) : NavigationEvent()
    data class NavigationError(val message: String) : NavigationEvent()
}

