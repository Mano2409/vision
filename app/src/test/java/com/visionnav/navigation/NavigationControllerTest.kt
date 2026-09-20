package com.visionnav.navigation

import com.visionnav.audio.VoiceCommand
import com.visionnav.audio.VoiceCommandParser
import com.visionnav.location.CurrentLocation
import com.visionnav.routing.Destination
import com.visionnav.routing.GeocodingSource
import com.visionnav.routing.ManeuverType
import com.visionnav.routing.MockRouteProvider
import com.visionnav.routing.NavigationRoute
import com.visionnav.routing.ValhallaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationControllerTest {

    @Test
    fun `TEST 16 - User remains on route and state is NAVIGATING`() {
        val controller = NavigationController()
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        assertEquals(NavigationState.NAVIGATING, controller.state.value)

        // Update with point directly on route
        val onRouteLoc = CurrentLocation(route.points[0].latitude, route.points[0].longitude, 3.0f)
        controller.onLocationUpdate(onRouteLoc)

        assertEquals(NavigationState.NAVIGATING, controller.state.value)
    }

    @Test
    fun `TEST 17 & 18 - User approaches maneuver and turn announcement triggers`() {
        val controller = NavigationController(
            advanceDistanceMeters = 50f,
            nearDistanceMeters = 20f,
            nowDistanceMeters = 8f
        )
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        // Maneuver 1 is at points[2]
        val targetManeuverCoord = route.maneuvers[1].coordinate

        // Approach within ~18 meters (triggers NEAR stage: In 20 meters, turn right)
        // 18m offset ~ 0.00016 deg
        val nearLocation = CurrentLocation(
            latitude = targetManeuverCoord.latitude - 0.00015,
            longitude = targetManeuverCoord.longitude,
            accuracyMeters = 3.0f
        )
        controller.onLocationUpdate(nearLocation)

        assertTrue(capturedEvents.any { it is NavigationEvent.TurnSoon })
        job.cancel()
    }

    @Test
    fun `TEST 19 - Same turn announcement stage is not repeated continuously on every GPS update`() {
        val controller = NavigationController(
            advanceDistanceMeters = 50f,
            nearDistanceMeters = 20f,
            nowDistanceMeters = 8f
        )
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        val targetManeuverCoord = route.maneuvers[1].coordinate
        val nearLocation = CurrentLocation(
            latitude = targetManeuverCoord.latitude - 0.00015,
            longitude = targetManeuverCoord.longitude,
            accuracyMeters = 3.0f
        )

        // 3 consecutive updates at the same near location
        controller.onLocationUpdate(nearLocation)
        controller.onLocationUpdate(nearLocation)
        controller.onLocationUpdate(nearLocation)

        val turnSoonEvents = capturedEvents.filterIsInstance<NavigationEvent.TurnSoon>()
        assertEquals("TurnSoon must only trigger ONCE for the NEAR stage", 1, turnSoonEvents.size)
        job.cancel()
    }

    @Test
    fun `TEST 20 - Next maneuver activates correctly after completing previous turn`() {
        val controller = NavigationController()
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        val turnManeuverCoord = route.maneuvers[1].coordinate
        // Reach turn maneuver within 'now' threshold (8m)
        val atTurnManeuver = CurrentLocation(
            latitude = turnManeuverCoord.latitude,
            longitude = turnManeuverCoord.longitude,
            accuracyMeters = 2.0f
        )
        controller.onLocationUpdate(atTurnManeuver)

        // Should advance to next maneuver
        assertEquals(1, controller.getCurrentManeuver()?.maneuverIndex)
        job.cancel()
    }

    @Test
    fun `TEST 21 - Wrong direction detection triggers when facing backwards`() {
        val controller = NavigationController(wrongDirectionAngleDegrees = 120f)
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        // Bearing from points[0] to points[1] is North (0 degrees)
        // Set heading to South (180 degrees) -> 180 deg difference >= 120 deg
        val loc = CurrentLocation(route.points[0].latitude, route.points[0].longitude, 3f)
        controller.onLocationUpdate(loc, headingDegrees = 180f)

        assertTrue("Wrong direction must be detected when heading 180 deg away from path",
            capturedEvents.any { it is NavigationEvent.WrongDirection })
        job.cancel()
    }

    @Test
    fun `TEST 22 - Unreliable or null heading does not cause false wrong-direction warning`() {
        val controller = NavigationController(wrongDirectionAngleDegrees = 120f)
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        val loc = CurrentLocation(route.points[0].latitude, route.points[0].longitude, 3f)
        // Null heading (sensor unavailable)
        controller.onLocationUpdate(loc, headingDegrees = null)

        assertFalse("Null heading must not trigger wrong-direction warning",
            capturedEvents.any { it is NavigationEvent.WrongDirection })
        job.cancel()
    }

    @Test
    fun `TEST 26 - New route successfully replaces active route on rerouting`() {
        val controller = NavigationController()
        val originalRoute = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(originalRoute)

        assertEquals("mock_route_1", controller.getActiveRoute()?.id)

        val updatedRoute = originalRoute.copy(id = "new_route_recalculated")
        controller.loadRoute(updatedRoute)

        assertEquals("new_route_recalculated", controller.getActiveRoute()?.id)
    }

    @Test
    fun `TEST 32 - Repeat command emits latest navigation instruction`() {
        val controller = NavigationController()
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        controller.repeatLastInstruction()

        val repeatEvents = capturedEvents.filterIsInstance<NavigationEvent.RepeatInstruction>()
        assertEquals(1, repeatEvents.size)
        assertTrue(repeatEvents[0].text.isNotBlank())
        job.cancel()
    }

    @Test
    fun `TEST 33 & 34 - Pause and Resume navigation lifecycle`() {
        val controller = NavigationController()
        val route = MockRouteProvider.createMockPedestrianRoute()
        controller.loadRoute(route)
        controller.startNavigation()

        assertEquals(NavigationState.NAVIGATING, controller.state.value)

        // Pause
        controller.pauseNavigation()
        assertEquals(NavigationState.PAUSED, controller.state.value)

        // Resume with latest position
        val resumeLoc = CurrentLocation(route.points[1].latitude, route.points[1].longitude, 3f)
        controller.resumeNavigation(resumeLoc)
        assertEquals(NavigationState.NAVIGATING, controller.state.value)
    }

    @Test
    fun `TEST 35 - VoiceCommandParser parses navigation commands without invoking TTS`() {
        val startCmd = VoiceCommandParser.parse("start navigation")
        val pauseCmd = VoiceCommandParser.parse("pause navigation")
        val resumeCmd = VoiceCommandParser.parse("resume navigation")
        val repeatCmd = VoiceCommandParser.parse("repeat instruction")
        val whatWasThatCmd = VoiceCommandParser.parse("what was that?")

        assertEquals(VoiceCommand.StartNavigation, startCmd)
        assertEquals(VoiceCommand.PauseNavigation, pauseCmd)
        assertEquals(VoiceCommand.ResumeNavigation, resumeCmd)
        assertEquals(VoiceCommand.RepeatInstruction, repeatCmd)
        assertEquals(VoiceCommand.RepeatInstruction, whatWasThatCmd)
    }

    @Test
    fun `TEST 36 & 37 - Offline network loss preserves route and prevents endless reroute loop`() {
        val controller = NavigationController()
        val route = MockRouteProvider.createMockPedestrianRoute()
        val destination = Destination(13.0840, 80.2730, "Destination", GeocodingSource.PHOTON)
        controller.loadRoute(route, destination)
        controller.startNavigation()

        val capturedEvents = mutableListOf<NavigationEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            controller.events.collect { capturedEvents.add(it) }
        }

        // Simulate network loss
        controller.setNetworkStatus(false)
        assertFalse(controller.isNetworkAvailable)
        assertTrue(capturedEvents.any { it is NavigationEvent.NetworkUnavailable })

        // User continues walking along existing route offline
        val nextPoint = CurrentLocation(route.points[1].latitude, route.points[1].longitude, 3f)
        controller.onLocationUpdate(nextPoint)

        // Navigation continues smoothly on downloaded route
        assertEquals(NavigationState.NAVIGATING, controller.state.value)
        job.cancel()
    }

    @Test
    fun `TEST 38 - Network recovery restores online capability`() {
        val controller = NavigationController()
        controller.setNetworkStatus(false)
        assertFalse(controller.isNetworkAvailable)

        controller.setNetworkStatus(true)
        assertTrue(controller.isNetworkAvailable)
    }
}

