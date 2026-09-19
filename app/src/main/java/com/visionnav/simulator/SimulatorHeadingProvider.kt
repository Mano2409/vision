package com.visionnav.simulator

import com.visionnav.location.IHeadingProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Development Heading/Azimuth simulator for testing compass orientation and wrap-around.
 */
class SimulatorHeadingProvider(
    initialHeading: Float = 45f // Northeast
) : IHeadingProvider {

    private val _headingFlow = MutableStateFlow<Float?>(initialHeading)
    override val headingFlow: StateFlow<Float?> = _headingFlow.asStateFlow()

    private var isStarted = false

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }

    fun setHeading(degrees: Float) {
        val normalized = (degrees % 360f + 360f) % 360f
        _headingFlow.value = normalized
    }

    fun rotateBy(deltaDegrees: Float) {
        val current = _headingFlow.value ?: 0f
        setHeading(current + deltaDegrees)
    }
}

