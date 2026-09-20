package com.visionnav.navigation

/**
 * Lifecycle states of the pedestrian navigation system.
 */
enum class NavigationState {
    IDLE,
    ROUTE_LOADING,
    ROUTE_READY,
    NAVIGATING,
    PAUSED,
    POSSIBLY_OFF_ROUTE,
    OFF_ROUTE,
    REROUTING,
    ARRIVED,
    NETWORK_UNAVAILABLE,
    ERROR
}

