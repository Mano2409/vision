package com.visionnav.routing

import com.visionnav.location.CurrentLocation
import com.visionnav.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Pedestrian Routing Client communicating with the OpenStreetMap Valhalla engine.
 *
 * Configures the dedicated pedestrian costing profile with sidewalk preference,
 * step penalties for accessibility, and strict network error handling.
 */
class ValhallaClient(
    private val httpClient: OkHttpClient = NetworkClient.client,
    val endpointUrl: String = VALHALLA_BASE_URL
) {

    companion object {
        const val VALHALLA_BASE_URL = "https://valhalla1.openstreetmap.de/route"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Requests a pedestrian route from the current location to the destination.
     * Throws RoutingException on failure.
     */
    suspend fun getPedestrianRoute(origin: CurrentLocation, destination: Destination): NavigationRoute =
        getPedestrianRoute(
            startLat = origin.latitude,
            startLon = origin.longitude,
            destLat = destination.latitude,
            destLon = destination.longitude
        )

    /**
     * Requests a pedestrian route between raw coordinate pairs.
     */
    suspend fun getPedestrianRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ): NavigationRoute = withContext(Dispatchers.IO) {
        if (!CurrentLocation.isValidCoordinate(startLat, startLon) ||
            !Destination.isValidCoordinate(destLat, destLon)
        ) {
            throw RoutingException(
                errorType = RoutingErrorType.INVALID_COORDINATES,
                message = "Invalid origin or destination coordinates"
            )
        }

        val payload = buildValhallaPayload(startLat, startLon, destLat, destLon)

        val request = Request.Builder()
            .url(endpointUrl)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw RoutingException(
                errorType = RoutingErrorType.TIMEOUT,
                message = "Valhalla routing request timed out",
                cause = e
            )
        } catch (e: IOException) {
            throw RoutingException(
                errorType = RoutingErrorType.NETWORK_ERROR,
                message = "Network error connecting to Valhalla: ${e.message}",
                cause = e
            )
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorType = when (resp.code) {
                    400 -> RoutingErrorType.SERVER_ERROR
                    429 -> RoutingErrorType.SERVER_ERROR
                    in 500..599 -> RoutingErrorType.SERVER_ERROR
                    else -> RoutingErrorType.SERVER_ERROR
                }
                throw RoutingException(
                    errorType = errorType,
                    message = "Valhalla API returned HTTP ${resp.code}"
                )
            }

            val body = resp.body?.string()
            if (body.isNullOrBlank()) {
                throw RoutingException(
                    errorType = RoutingErrorType.EMPTY_ROUTE,
                    message = "Empty response received from Valhalla"
                )
            }

            RouteGeometryParser.parseValhallaJson(body, provider = "Valhalla")
        }
    }

    /**
     * Builds the JSON body configured specifically for blind/visually impaired pedestrian navigation.
     */
    fun buildValhallaPayload(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ): String {
        val root = JSONObject()

        val locations = JSONArray()
        val startLoc = JSONObject().apply {
            put("lat", startLat)
            put("lon", startLon)
            put("type", "break")
        }
        val destLoc = JSONObject().apply {
            put("lat", destLat)
            put("lon", destLon)
            put("type", "break")
        }
        locations.put(startLoc)
        locations.put(destLoc)
        root.put("locations", locations)

        root.put("costing", "pedestrian")

        val pedestrianOptions = JSONObject().apply {
            put("use_sidewalk", 1.0)       // Strongly prefer sidewalks over roads
            put("step_penalty", 20.0)      // Penalize stairs/steps for accessibility
            put("walking_speed", 4.8)      // Standard ~1.33 m/s walking speed
        }
        val costingOptions = JSONObject().apply {
            put("pedestrian", pedestrianOptions)
        }
        root.put("costing_options", costingOptions)

        val directionsOptions = JSONObject().apply {
            put("units", "kilometers")
            put("language", "en-US")
        }
        root.put("directions_options", directionsOptions)

        return root.toString()
    }
}

