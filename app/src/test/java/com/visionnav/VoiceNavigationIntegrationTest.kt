package com.visionnav

import com.visionnav.audio.AudioPriority
import com.visionnav.audio.VoiceCommand
import com.visionnav.audio.VoiceCommandParser
import com.visionnav.simulator.SimulatorHeadingProvider
import com.visionnav.simulator.SimulatorLocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNavigationIntegrationTest {

    @Test
    fun `TEST 13 - Voice command parser creates Navigate command from spoken destination`() {
        val spokenText = "Navigate to Chennai Central"
        val command = VoiceCommandParser.parse(spokenText)

        assertTrue(command is VoiceCommand.NavigateTo)
        val navigateCommand = command as VoiceCommand.NavigateTo
        assertEquals("chennai central", navigateCommand.destination.lowercase())
    }

    @Test
    fun `TEST 14 - Safety Priority Contract - Critical vehicle safety strictly outranks routine navigation`() {
        // Enforce that CRITICAL_SAFETY is P0 (level 0), outranking P1, P2, and P3
        assertTrue(AudioPriority.CRITICAL_SAFETY.level < AudioPriority.STATIC_OBSTACLE.level)
        assertTrue(AudioPriority.CRITICAL_SAFETY.level < AudioPriority.ROUTINE_NAVIGATION.level)
        assertTrue(AudioPriority.CRITICAL_SAFETY.level < AudioPriority.SYSTEM_FEEDBACK.level)
    }

    @Test
    fun `TEST 15 - Simulator mode location and heading providers emit valid coordinates`() {
        val simLocation = SimulatorLocationProvider(initialLatitude = 13.0827, initialLongitude = 80.2707)
        val simHeading = SimulatorHeadingProvider(initialHeading = 90f)

        simLocation.start()
        simHeading.start()

        val loc = simLocation.locationFlow.value
        val heading = simHeading.headingFlow.value

        assertTrue(loc.isAvailable)
        assertEquals(13.0827, loc.latitude, 0.0001)
        assertEquals(80.2707, loc.longitude, 0.0001)
        assertNotNull(heading)
        assertEquals(90f, heading!!, 0.1f)

        // Step forward and rotate
        simLocation.stepForward()
        simHeading.rotateBy(45f)

        val updatedLoc = simLocation.locationFlow.value
        val updatedHeading = simHeading.headingFlow.value

        assertTrue(updatedLoc.latitude > 0.0)
        assertEquals(135f, updatedHeading!!, 0.1f)
    }

    @Test
    fun `TEST 16 - Natural conversational voice phrases extract destination correctly`() {
        val testCases = mapOf(
            "I want to go to Central Station" to "Central Station",
            "Please navigate to Central Station" to "Central Station",
            "Can you take me to Central Station" to "Central Station",
            "Please take me to Central Station" to "Central Station",
            "Take me to Central Station" to "Central Station",
            "Go to Central Station" to "Central Station",
            "Navigate to Central Station." to "Central Station",
            "Head to Central Station!" to "Central Station"
        )

        for ((input, expectedDest) in testCases) {
            val command = VoiceCommandParser.parse(input)
            assertTrue("Input '$input' should produce NavigateTo command", command is VoiceCommand.NavigateTo)
            val navCmd = command as VoiceCommand.NavigateTo
            assertEquals("Input '$input' should extract '$expectedDest'", expectedDest.lowercase(), navCmd.destination.lowercase())
        }
    }

    @Test
    fun `TEST 17 - VoiceCommandParser parses disambiguation candidate selections`() {
        val firstCases = listOf("first", "first one", "the first one", "option one", "option 1", "number one", "number 1")
        for (input in firstCases) {
            val cmd = VoiceCommandParser.parse(input)
            assertTrue("'$input' should parse as SelectCandidate", cmd is VoiceCommand.SelectCandidate)
            assertEquals(0, (cmd as VoiceCommand.SelectCandidate).index)
        }

        val secondCases = listOf("second", "second one", "the second one", "option two", "option 2", "number two", "number 2")
        for (input in secondCases) {
            val cmd = VoiceCommandParser.parse(input)
            assertTrue("'$input' should parse as SelectCandidate", cmd is VoiceCommand.SelectCandidate)
            assertEquals(1, (cmd as VoiceCommand.SelectCandidate).index)
        }
    }
}
