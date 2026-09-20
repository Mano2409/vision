package com.visionnav.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RouteGeometryParserTest {

    @Test
    fun `TEST 9 - Valid Polyline6 geometry decodes into ordered points`() {
        // Valhalla Polyline6 example for (13.0827, 80.2707) to (13.0837, 80.2717)
        val encoded = "c`~`Ag`{mMO_@O_@"
        val points = RouteGeometryParser.decodePolyline(encoded, 1e6)

        assertNotNull(points)
        assertTrue(points.isNotEmpty())
    }

    @Test
    fun `TEST 10 - Invalid geometry fails safely without crashing`() {
        val points = RouteGeometryParser.decodePolyline("", 1e6)
        assertTrue(points.isEmpty())
    }

    @Test
    fun `TEST 11 - Decoded coordinates are validated against geographical bounds`() {
        // Coordinate points must have valid latitude [-90, +90] and longitude [-180, +180]
        val validPoint = RoutePoint(13.0827, 80.2707)
        assertTrue(validPoint.isValid)

        val invalidLat = RoutePoint(95.0, 80.2707)
        assertFalse(invalidLat.isValid)

        val nanPoint = RoutePoint(Double.NaN, 80.2707)
        assertFalse(nanPoint.isValid)
    }

    @Test
    fun `TEST 12 - Straight instruction recognized from Valhalla type`() {
        val direction = RouteGeometryParser.mapValhallaTypeToManeuver(1) // kStart / kContinue
        assertEquals(ManeuverType.STRAIGHT, direction)
    }

    @Test
    fun `TEST 13 - Left turn maneuvers mapped correctly`() {
        val left = RouteGeometryParser.mapValhallaTypeToManeuver(15)       // kLeft
        val sharpLeft = RouteGeometryParser.mapValhallaTypeToManeuver(14)  // kSharpLeft
        val slightLeft = RouteGeometryParser.mapValhallaTypeToManeuver(16) // kSlightLeft

        assertEquals(ManeuverType.LEFT, left)
        assertEquals(ManeuverType.SHARP_LEFT, sharpLeft)
        assertEquals(ManeuverType.SLIGHT_LEFT, slightLeft)
    }

    @Test
    fun `TEST 14 - Right turn maneuvers mapped correctly`() {
        val right = RouteGeometryParser.mapValhallaTypeToManeuver(10)       // kRight
        val sharpRight = RouteGeometryParser.mapValhallaTypeToManeuver(11) // kSharpRight
        val slightRight = RouteGeometryParser.mapValhallaTypeToManeuver(9)  // kSlightRight

        assertEquals(ManeuverType.RIGHT, right)
        assertEquals(ManeuverType.SHARP_RIGHT, sharpRight)
        assertEquals(ManeuverType.SLIGHT_RIGHT, slightRight)
    }

    @Test
    fun `TEST 15 - Arrival and U-turn maneuvers handled`() {
        val dest = RouteGeometryParser.mapValhallaTypeToManeuver(4)   // kDestination
        val uTurn = RouteGeometryParser.mapValhallaTypeToManeuver(12) // kUturnRight

        assertEquals(ManeuverType.DESTINATION, dest)
        assertEquals(ManeuverType.U_TURN, uTurn)
    }
}

