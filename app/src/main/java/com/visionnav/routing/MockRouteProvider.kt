package com.visionnav.routing

/**
 * Generates verified, reproducible pedestrian routes for offline testing and simulator replay.
 */
object MockRouteProvider {

    fun createMockPedestrianRoute(
        startLat: Double = 13.0827,
        startLon: Double = 80.2707,
        destLat: Double = 13.0840,
        destLon: Double = 80.2730
    ): NavigationRoute {
        val points = listOf(
            RoutePoint(startLat, startLon),
            RoutePoint(startLat + 0.0005, startLon),
            RoutePoint(startLat + 0.0010, startLon),
            RoutePoint(startLat + 0.0010, startLon + 0.0010),
            RoutePoint(destLat, destLon)
        )

        val maneuvers = listOf(
            ManeuverInstruction(
                id = "m_0",
                type = ManeuverType.STRAIGHT,
                coordinate = points[0],
                distanceFromRouteStartMeters = 0,
                distanceMeters = 110,
                streetName = "Central Station Way",
                instructionText = "Walk straight on Central Station Way",
                maneuverIndex = 0
            ),
            ManeuverInstruction(
                id = "m_1",
                type = ManeuverType.RIGHT,
                coordinate = points[2],
                distanceFromRouteStartMeters = 110,
                distanceMeters = 100,
                streetName = "Park Town Walkway",
                instructionText = "Turn right onto Park Town Walkway",
                maneuverIndex = 1
            ),
            ManeuverInstruction(
                id = "m_2",
                type = ManeuverType.DESTINATION,
                coordinate = points[4],
                distanceFromRouteStartMeters = 210,
                distanceMeters = 40,
                streetName = "Destination Promenade",
                instructionText = "You have arrived at your destination",
                maneuverIndex = 2
            )
        )

        val totalDist = 250
        val totalDur = 180

        return NavigationRoute(
            id = "mock_route_1",
            points = points,
            maneuvers = maneuvers,
            totalDistanceMeters = totalDist,
            estimatedDurationSeconds = totalDur,
            summary = RouteSummary.format(totalDist, totalDur),
            provider = "MockSimulator"
        )
    }
}

