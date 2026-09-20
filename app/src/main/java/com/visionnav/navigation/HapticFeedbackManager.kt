package com.visionnav.navigation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.visionnav.routing.ManeuverType
import com.visionnav.safety.SafetyLevel

/**
 * Manages tactile vibration feedback for pedestrian navigation events.
 *
 * Provides distinct, non-speech directional cues enabling visually impaired users
 * to sense turns, wrong directions, and arrivals without continuous audio.
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Dispatches vibration corresponding to a NavigationEvent.
     */
    fun onNavigationEvent(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.TurnSoon, is NavigationEvent.TurnNow -> {
                val direction = if (event is NavigationEvent.TurnSoon) event.direction else (event as NavigationEvent.TurnNow).direction
                when (direction) {
                    ManeuverType.LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> vibrateTurnLeft()
                    ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> vibrateTurnRight()
                    ManeuverType.U_TURN -> vibrateWrongDirection()
                    else -> Unit
                }
            }
            is NavigationEvent.WrongDirection -> vibrateWrongDirection()
            is NavigationEvent.OffRoute -> vibrateOffRoute()
            is NavigationEvent.Arrived -> vibrateArrived()
            else -> Unit
        }
    }

    /**
     * Pattern for LEFT turns: Two crisp medium pulses (tap-tap).
     */
    fun vibrateTurnLeft() {
        vibratePattern(
            timings = longArrayOf(0, 120, 100, 120),
            amplitudes = intArrayOf(0, 180, 0, 180)
        )
    }

    /**
     * Pattern for RIGHT turns: Single sustained pulse (hummm).
     */
    fun vibrateTurnRight() {
        vibratePattern(
            timings = longArrayOf(0, 350),
            amplitudes = intArrayOf(0, 220)
        )
    }

    /**
     * Pattern for WRONG DIRECTION: Three sharp urgent pulses (staccato warning).
     */
    fun vibrateWrongDirection() {
        vibratePattern(
            timings = longArrayOf(0, 80, 60, 80, 60, 80),
            amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        )
    }

    /**
     * Pattern for OFF ROUTE: Heavy pulsing buzz.
     */
    fun vibrateOffRoute() {
        vibratePattern(
            timings = longArrayOf(0, 250, 120, 250),
            amplitudes = intArrayOf(0, 240, 0, 240)
        )
    }

    /**
     * Pattern for ARRIVED: Joyful rising crescendo pattern.
     */
    fun vibrateArrived() {
        vibratePattern(
            timings = longArrayOf(0, 100, 80, 150, 80, 300),
            amplitudes = intArrayOf(0, 100, 0, 180, 0, 255)
        )
    }

    /**
     * Dispatches vibration corresponding to a SafetyLevel.
     */
    fun onSafetyLevel(level: SafetyLevel) {
        when (level) {
            SafetyLevel.CAUTION -> vibrateCaution()
            SafetyLevel.WARNING -> vibrateWarning()
            SafetyLevel.CRITICAL -> vibrateCritical()
            else -> Unit
        }
    }

    /**
     * Safety Caution: Short crisp tap.
     */
    fun vibrateCaution() {
        vibratePattern(
            timings = longArrayOf(0, 80),
            amplitudes = intArrayOf(0, 150)
        )
    }

    /**
     * Safety Warning: Strong dual pulse buzz.
     */
    fun vibrateWarning() {
        vibratePattern(
            timings = longArrayOf(0, 150, 100, 200),
            amplitudes = intArrayOf(0, 220, 0, 240)
        )
    }

    /**
     * Safety Critical: Urgent repeating emergency pulses.
     */
    fun vibrateCritical() {
        vibratePattern(
            timings = longArrayOf(0, 200, 80, 300, 80, 300),
            amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        )
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: Exception) {
            // Graceful fallback if system vibration service is disabled or restricted
        }
    }

    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }
}

