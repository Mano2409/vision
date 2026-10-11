package com.visionnav.routing

import com.visionnav.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Primary Geocoding Provider using the Komoot Photon API (OSM-based Elasticsearch).
 *
 * Fast, typo-tolerant forward geocoding well-suited for voice-to-text queries.
 */
class PhotonGeocoder(
    private val httpClient: OkHttpClient = NetworkClient.client
) : IGeocodingClient {

    companion object {
        private const val BASE_URL = "https://photon.komoot.io/api/"
        private const val LIMIT = 5
    }

    override suspend fun search(
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<GeocodingResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = if (userLat != null && userLon != null && Destination.isValidCoordinate(userLat, userLon)) {
            "$BASE_URL?q=$encodedQuery&limit=$LIMIT&lat=$userLat&lon=$userLon"
        } else {
            "$BASE_URL?q=$encodedQuery&limit=$LIMIT"
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
                message = "Connection timed out connecting to Photon geocoder: ${e.message}",
                cause = e
            )
        } catch (e: IOException) {
            throw GeocodingException(
                errorType = GeocodingErrorType.NETWORK_ERROR,
                message = "Network error connecting to Photon geocoder: ${e.message}",
                cause = e
            )
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorType = if (resp.code == 429) GeocodingErrorType.RATE_LIMITED else GeocodingErrorType.SERVER_ERROR
                throw GeocodingException(
                    errorType = errorType,
                    message = "Photon API returned HTTP ${resp.code}"
                )
            }

            val bodyString = resp.body?.string() ?: return@withContext emptyList()
            try {
                parsePhotonGeoJson(bodyString)
            } catch (e: org.json.JSONException) {
                throw GeocodingException(
                    errorType = GeocodingErrorType.INVALID_RESULT,
                    message = "Malformed JSON received from Photon API: ${e.message}",
                    cause = e
                )
            }
        }
    }

    private fun parsePhotonGeoJson(jsonString: String): List<GeocodingResult> {
        val results = mutableListOf<GeocodingResult>()
        val root = JSONObject(jsonString)
        val features = root.optJSONArray("features") ?: return emptyList()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            // GeoJSON coordinates order: [longitude, latitude]
            val lon = coordinates.getDouble(0)
            val lat = coordinates.getDouble(1)

            // Validate coordinate boundaries [-90, +90] and [-180, +180]
            if (!Destination.isValidCoordinate(lat, lon)) {
                continue
            }

            val properties = feature.getJSONObject("properties")
            val name = properties.optString("name", "")
            val street = properties.optString("street", "")
            val city = properties.optString("city", properties.optString("district", ""))
            val state = properties.optString("state", "")
            val country = properties.optString("country", "")
            val osmValue = properties.optString("osm_value", "")

            val title = when {
                name.isNotBlank() -> name
                street.isNotBlank() -> street
                else -> "Location ($lat, $lon)"
            }

            val addressParts = listOf(street, city, state, country).filter { it.isNotBlank() && it != title }
            val addressSnippet = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else null
            val fullDisplayName = if (addressSnippet != null) "$title, $addressSnippet" else title

            if (fullDisplayName.isBlank()) continue

            results.add(
                GeocodingResult(
                    id = "photon_${properties.optLong("osm_id", System.currentTimeMillis() + i)}",
                    displayName = fullDisplayName,
                    latitude = lat,
                    longitude = lon,
                    category = if (osmValue.isNotBlank()) osmValue else null,
                    addressSnippet = addressSnippet,
                    provider = "Photon",
                    state = if (state.isNotBlank()) state else null,
                    country = if (country.isNotBlank()) country else null
                )
            )
        }

        return results
    }
}

