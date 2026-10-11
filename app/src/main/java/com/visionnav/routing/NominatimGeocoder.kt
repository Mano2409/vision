package com.visionnav.routing

import com.visionnav.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder

/**
 * Fallback Geocoding Provider using OpenStreetMap Nominatim.
 *
 * Enforces strict 1-second throttling and Custom User-Agent compliance
 * to adhere strictly to the published Nominatim Usage Policy.
 */
class NominatimGeocoder(
    private val httpClient: OkHttpClient = NetworkClient.client
) : IGeocodingClient {

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val LIMIT = 5
        private const val MIN_REQUEST_INTERVAL_MS = 1050L // OSM Nominatim policy: max 1 req/sec

        private val throttleMutex = Mutex()
        private var lastRequestTimestampMs = 0L
    }

    override suspend fun search(
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<GeocodingResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // Enforce rate-limit throttle
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTimestampMs
            if (timeSinceLast < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - timeSinceLast)
            }
            lastRequestTimestampMs = System.currentTimeMillis()
        }

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = if (userLat != null && userLon != null && Destination.isValidCoordinate(userLat, userLon)) {
            val minLon = (userLon - 1.5).coerceIn(-180.0, 180.0)
            val maxLon = (userLon + 1.5).coerceIn(-180.0, 180.0)
            val minLat = (userLat - 1.5).coerceIn(-90.0, 90.0)
            val maxLat = (userLat + 1.5).coerceIn(-90.0, 90.0)
            val viewbox = "$minLon,$maxLat,$maxLon,$minLat"
            "$BASE_URL?format=json&q=$encodedQuery&limit=$LIMIT&addressdetails=1&viewbox=$viewbox&bounded=0"
        } else {
            "$BASE_URL?format=json&q=$encodedQuery&limit=$LIMIT&addressdetails=1"
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: java.net.SocketTimeoutException) {
            throw GeocodingException(
                errorType = GeocodingErrorType.TIMEOUT,
                message = "Connection timed out connecting to Nominatim geocoder: ${e.message}",
                cause = e
            )
        } catch (e: IOException) {
            throw GeocodingException(
                errorType = GeocodingErrorType.NETWORK_ERROR,
                message = "Network error connecting to Nominatim geocoder: ${e.message}",
                cause = e
            )
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorType = when (resp.code) {
                    429 -> GeocodingErrorType.RATE_LIMITED
                    in 500..599 -> GeocodingErrorType.SERVER_ERROR
                    else -> GeocodingErrorType.SERVER_ERROR
                }
                throw GeocodingException(
                    errorType = errorType,
                    message = "Nominatim API returned HTTP ${resp.code}"
                )
            }

            val bodyString = resp.body?.string() ?: return@withContext emptyList()
            try {
                parseNominatimJson(bodyString)
            } catch (e: org.json.JSONException) {
                throw GeocodingException(
                    errorType = GeocodingErrorType.INVALID_RESULT,
                    message = "Malformed JSON received from Nominatim API: ${e.message}",
                    cause = e
                )
            }
        }
    }

    private fun parseNominatimJson(jsonString: String): List<GeocodingResult> {
        val results = mutableListOf<GeocodingResult>()
        val array = JSONArray(jsonString)

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val latStr = item.optString("lat", "")
            val lonStr = item.optString("lon", "")
            val lat = latStr.toDoubleOrNull() ?: continue
            val lon = lonStr.toDoubleOrNull() ?: continue

            // Validate coordinate boundaries [-90, +90] and [-180, +180]
            if (!Destination.isValidCoordinate(lat, lon)) {
                continue
            }

            val displayName = item.optString("display_name", "Location ($lat, $lon)")
            if (displayName.isBlank()) continue

            val type = item.optString("type", item.optString("class", ""))
            val placeId = item.optLong("place_id", System.currentTimeMillis() + i)

            val addressObj = item.optJSONObject("address")
            val state = addressObj?.optString("state", "") ?: ""
            val country = addressObj?.optString("country", "") ?: ""

            results.add(
                GeocodingResult(
                    id = "nominatim_$placeId",
                    displayName = displayName,
                    latitude = lat,
                    longitude = lon,
                    category = if (type.isNotBlank()) type else null,
                    addressSnippet = displayName,
                    provider = "Nominatim",
                    state = if (state.isNotBlank()) state else null,
                    country = if (country.isNotBlank()) country else null
                )
            )
        }

        return results
    }
}

