package com.visionnav.location

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction layer for spatial location providers.
 * Decouples the UI and navigation decision engine from Google Play Services,
 * allowing live GPS and offline mock simulation to be swapped seamlessly.
 */
interface ILocationProvider {
    /**
     * Observable stream of user coordinates, accuracy, and confidence.
     */
    val locationFlow: StateFlow<NavLocation>

    /**
     * Starts listening for location updates.
     */
    fun start()

    /**
     * Stops listening for location updates to conserve device battery.
     */
    fun stop()

    /**
     * Configures the location update interval (e.g. 1-2s for active walking, 10s when stationary).
     */
    fun setUpdateInterval(intervalMs: Long)
}

