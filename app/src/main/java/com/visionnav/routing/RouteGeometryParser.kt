package com.visionnav.routing

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * High-precision decoder for Valhalla route geometry and pedestrian maneuvers.
 *
 * Implements standard Polyline6 (1e6 factor) decoding for centimeter-level
 * pedestrian sidewalk alignment and extracts clean internal ManeuverInstruction models.
 */
object RouteGeometryParser {

    /**
     * Decodes a polyline encoded string into a list of RoutePoints.
     * Precision defaults to 1e6 (Valhalla Polyline6 standard).
     * Validates every coordinate: rejects NaN, infinite, and out-of-bounds coordinates.
     */
    fun decodePolyline(encoded: String, precision: Double = 1e6): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat / precision
            val pLng = lng / precision

            val point = RoutePoint(latitude = pLat, longitude = pLng)
            if (point.isValid) {
                points.add(point)
            }
        }
        return points
    }

    /**
     * Parses a Valhalla JSON route response into a clean NavigationRoute model.
     * Throws RoutingException on malformed or empty payloads.
     */
    fun parseValhallaJson(jsonString: String, provider: String = "Valhalla"): NavigationRoute {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw RoutingException(
                errorType = RoutingErrorType.MALFORMED_RESPONSE,
                message = "Malformed JSON received from Valhalla",
                cause = e
            )
        }

        val trip = root.optJSONObject("trip")
            ?: throw RoutingException(
                errorType = RoutingErrorType.EMPTY_ROUTE,
                message = "Valhalla response missing 'trip' object"
            )

        val summaryJson = trip.optJSONObject("summary")
            ?: throw RoutingException(
                errorType = RoutingErrorType.EMPTY_ROUTE,
                message = "Valhalla response missing trip 'summary'"
            )

        // Distance in km -> convert to meters
        val lengthKm = summaryJson.optDouble("length", 0.0)
        val totalDistanceMeters = (lengthKm * 1000.0).roundToInt()
        val totalDurationSeconds = summaryJson.optInt("time", 0)

        val legs = trip.optJSONArray("legs")
        if (legs == null || legs.length() == 0) {
            throw RoutingException(
                errorType = RoutingErrorType.EMPTY_ROUTE,
                message = "Valhalla response contains no legs"
            )
        }

        val leg = legs.getJSONObject(0)
        val shapeEncoded = leg.optString("shape", "")
        if (shapeEncoded.isBlank()) {
            throw RoutingException(
                errorType = RoutingErrorType.EMPTY_ROUTE,
                message = "Valhalla response missing route shape"
            )
        }

        val decodedCoordinates = decodePolyline(shapeEncoded, 1e6)
        if (decodedCoordinates.isEmpty()) {
            throw RoutingException(
                errorType = RoutingErrorType.EMPTY_ROUTE,
                message = "Decoded route geometry is empty"
            )
        }

        val maneuversJson = leg.optJSONArray("maneuvers")
        val maneuvers = mutableListOf<ManeuverInstruction>()

        if (maneuversJson != null) {
            var cumulativeDistanceMeters = 0

            for (i in 0 until maneuversJson.length()) {
                val man = maneuversJson.getJSONObject(i)
                val instruction = man.optString("instruction", "Continue")
                val lengthManeuverKm = man.optDouble("length", 0.0)
                val distMeters = (lengthManeuverKm * 1000.0).roundToInt()
                val typeCode = man.optInt("type", 0)

                val streetNames = man.optJSONArray("street_names")
                val streetName = if (streetNames != null && streetNames.length() > 0) {
                    streetNames.getString(0)
                } else {
                    "Pedestrian Path"
                }

                val beginIdx = man.optInt("begin_shape_index", 0)
                val coord = if (beginIdx in decodedCoordinates.indices) {
                    decodedCoordinates[beginIdx]
                } else {
                    decodedCoordinates.first()
                }

                val maneuverType = mapValhallaTypeToManeuver(typeCode)

                maneuvers.add(
                    ManeuverInstruction(
                        id = "maneuver_$i",
                        type = maneuverType,
                        coordinate = coord,
                        distanceFromRouteStartMeters = cumulativeDistanceMeters,
                        distanceMeters = distMeters,
                        streetName = streetName,
                        instructionText = instruction,
                        maneuverIndex = i
                    )
                )

                cumulativeDistanceMeters += distMeters
            }
        }

        val summary = RouteSummary.format(totalDistanceMeters, totalDurationSeconds)

        return NavigationRoute(
            id = "route_${System.currentTimeMillis()}",
            points = decodedCoordinates,
            maneuvers = maneuvers,
            totalDistanceMeters = totalDistanceMeters,
            estimatedDurationSeconds = totalDurationSeconds,
            summary = summary,
            provider = provider
        )
    }

    /**
     * Maps Valhalla numeric maneuver type codes to clean internal ManeuverType enums.
     */
    fun mapValhallaTypeToManeuver(typeCode: Int): ManeuverType {
        return when (typeCode) {
            4, 5, 6 -> ManeuverType.DESTINATION // kDestination, kDestinationRight, kDestinationLeft
            2, 9 -> ManeuverType.SLIGHT_RIGHT    // kStartRight, kSlightRight
            3, 16 -> ManeuverType.SLIGHT_LEFT   // kStartLeft, kSlightLeft
            10, 18 -> ManeuverType.RIGHT        // kRight, kRampRight
            11 -> ManeuverType.SHARP_RIGHT      // kSharpRight
            15, 19 -> ManeuverType.LEFT         // kLeft, kRampLeft
            14 -> ManeuverType.SHARP_LEFT       // kSharpLeft
            12, 13 -> ManeuverType.U_TURN       // kUturnRight, kUturnLeft
            else -> ManeuverType.STRAIGHT       // kNone, kStart, kBecomes, kContinue
        }
    }
}

