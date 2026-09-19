package com.visionnav.location

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing device orientation and compass heading.
 */
interface IHeadingProvider {
    /**
     * Observable compass heading in degrees (0.0° - 359.9°), where 0° = North, 90° = East, 180° = South, 270° = West.
     * Emits null if sensors are unavailable or uncalibrated.
     */
    val headingFlow: StateFlow<Float?>

    /**
     * Starts listening to device orientation sensors.
     */
    fun start()

    /**
     * Stops listening to device orientation sensors to prevent battery drain.
     */
    fun stop()
}

