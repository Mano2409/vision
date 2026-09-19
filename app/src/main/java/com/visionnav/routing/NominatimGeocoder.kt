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

    override suspend fun search(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
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
        val url = "$BASE_URL?format=json&q=$encodedQuery&limit=$LIMIT&addressdetails=1"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
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
            parseNominatimJson(bodyString)
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

            val displayName = item.optString("display_name", "Location ($lat, $lon)")
            val type = item.optString("type", item.optString("class", ""))
            val placeId = item.optLong("place_id", System.currentTimeMillis() + i)

            results.add(
                GeocodingResult(
                    id = "nominatim_$placeId",
                    displayName = displayName,
                    latitude = lat,
                    longitude = lon,
                    category = if (type.isNotBlank()) type else null,
                    addressSnippet = displayName,
                    provider = "Nominatim"
                )
            )
        }

        return results
    }
}

