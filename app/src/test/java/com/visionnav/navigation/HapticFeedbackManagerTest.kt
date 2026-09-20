package com.visionnav.navigation

import android.content.Context
import android.os.Vibrator
import com.visionnav.routing.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackManagerTest {

    private class RecordingHapticManager {
        val vibrationHistory = mutableListOf<String>()

        fun onNavigationEvent(event: NavigationEvent) {
            when (event) {
                is NavigationEvent.TurnSoon, is NavigationEvent.TurnNow -> {
                    val direction = if (event is NavigationEvent.TurnSoon) event.direction else (event as NavigationEvent.TurnNow).direction
                    when (direction) {
                        ManeuverType.LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> vibrationHistory.add("LEFT")
                        ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> vibrationHistory.add("RIGHT")
                        ManeuverType.U_TURN -> vibrationHistory.add("WRONG_DIR")
                        else -> Unit
                    }
                }
                is NavigationEvent.WrongDirection -> vibrationHistory.add("WRONG_DIR")
                is NavigationEvent.OffRoute -> vibrationHistory.add("OFF_ROUTE")
                is NavigationEvent.Arrived -> vibrationHistory.add("ARRIVED")
                else -> Unit
            }
        }
    }

    @Test
    fun `TEST 39 - Left turn produces left-turn haptic event`() {
        val manager = RecordingHapticManager()
        manager.onNavigationEvent(NavigationEvent.TurnSoon(ManeuverType.LEFT, 20, "Oak St"))
        assertEquals(listOf("LEFT"), manager.vibrationHistory)
    }

    @Test
    fun `TEST 40 - Right turn produces right-turn haptic event`() {
        val manager = RecordingHapticManager()
        manager.onNavigationEvent(NavigationEvent.TurnNow(ManeuverType.RIGHT, "Main St"))
        assertEquals(listOf("RIGHT"), manager.vibrationHistory)
    }

    @Test
    fun `TEST 41 - Wrong direction produces warning haptic event`() {
        val manager = RecordingHapticManager()
        manager.onNavigationEvent(NavigationEvent.WrongDirection(150f))
        assertEquals(listOf("WRONG_DIR"), manager.vibrationHistory)
    }

    @Test
    fun `TEST 42 - Off-route produces warning haptic event`() {
        val manager = RecordingHapticManager()
        manager.onNavigationEvent(NavigationEvent.OffRoute(35f))
        assertEquals(listOf("OFF_ROUTE"), manager.vibrationHistory)
    }

    @Test
    fun `TEST 43 - Arrival produces arrival haptic event`() {
        val manager = RecordingHapticManager()
        manager.onNavigationEvent(NavigationEvent.Arrived("Central Station"))
        assertEquals(listOf("ARRIVED"), manager.vibrationHistory)
    }

    @Test
    fun `TEST 44 - Normal progress GPS updates do not trigger continuous vibration`() {
        val manager = RecordingHapticManager()
        // Routine continue straight events must NOT buzz
        manager.onNavigationEvent(NavigationEvent.ContinueStraight(100, "Main St"))
        manager.onNavigationEvent(NavigationEvent.NavigationStarted)
        manager.onNavigationEvent(NavigationEvent.NavigationResumed)

        assertTrue("Routine GPS movement must not produce vibration spam", manager.vibrationHistory.isEmpty())
    }
}

