package com.visionnav.routing

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust geocoding pipeline implementing:
 * 1. Fast in-memory cache with TTL (Time-To-Live).
 * 2. Primary lookup via Komoot Photon.
 * 3. Graceful fallback to OpenStreetMap Nominatim when Photon fails or returns empty.
 * 4. Structured domain error reporting when both fail.
 */
class CompositeGeocodingClient(
    private val photonGeocoder: IGeocodingClient = PhotonGeocoder(),
    private val nominatimGeocoder: IGeocodingClient = NominatimGeocoder(),
    private val cacheTtlMs: Long = 15 * 60 * 1000L // 15 minutes TTL
) : IGeocodingClient {

    companion object {
        private const val TAG = "CompositeGeocoder"

        /**
         * Normalizes search query by trimming, converting to lowercase,
         * and collapsing repeated whitespace.
         * Example: "  Chennai   Central  " -> "chennai central"
         */
        fun normalizeQuery(query: String): String {
            return query.trim().lowercase().replace(Regex("\\s+"), " ")
        }
    }

    private data class CacheEntry(
        val results: List<GeocodingResult>,
        val timestamp: Long
    )

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Resolves destination query into a clean, validated Destination model.
     * Uses the most relevant (first valid) result.
     * Throws GeocodingException on failure.
     */
    suspend fun resolveDestination(query: String): Destination {
        val results = search(query)
        return results.firstNotNullOfOrNull { it.toDestination() }
            ?: throw GeocodingException(
                errorType = GeocodingErrorType.NO_RESULTS,
                message = "No matching location found for \"$query\"."
            )
    }

    override suspend fun search(query: String): List<GeocodingResult> {
        val normalizedKey = normalizeQuery(query)
        if (normalizedKey.isBlank()) return emptyList()

        // 1. Check local in-memory cache
        val cached = memoryCache[normalizedKey]
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < cacheTtlMs && cached.results.isNotEmpty()) {
                Log.d(TAG, "Cache HIT for '$query' (age: ${age / 1000}s)")
                return cached.results
            } else {
                memoryCache.remove(normalizedKey)
            }
        }

        // 2. Try Primary: Photon
        var photonResults: List<GeocodingResult>? = null
        var photonError: Throwable? = null
        try {
            val results = photonGeocoder.search(query)
            if (results.isNotEmpty()) {
                photonResults = results
            }
        } catch (e: Throwable) {
            photonError = e
            Log.w(TAG, "Photon geocoding failed for '$query', attempting Nominatim fallback: ${e.message}")
        }

        if (!photonResults.isNullOrEmpty()) {
            cacheResults(normalizedKey, photonResults)
            return photonResults
        }

        // 3. Try Fallback: Nominatim
        var nominatimResults: List<GeocodingResult>? = null
        var nominatimError: Throwable? = null
        try {
            val results = nominatimGeocoder.search(query)
            if (results.isNotEmpty()) {
                nominatimResults = results
            }
        } catch (e: Throwable) {
            nominatimError = e
            Log.w(TAG, "Nominatim geocoding failed for '$query': ${e.message}")
        }

        if (!nominatimResults.isNullOrEmpty()) {
            cacheResults(normalizedKey, nominatimResults)
            return nominatimResults
        }

        // 4. Both failed or returned empty
        val isNetworkFailure = (photonError is GeocodingException && photonError.errorType == GeocodingErrorType.NETWORK_ERROR) &&
                (nominatimError is GeocodingException && nominatimError.errorType == GeocodingErrorType.NETWORK_ERROR)

        val isTimeout = (photonError is GeocodingException && photonError.errorType == GeocodingErrorType.TIMEOUT) &&
                (nominatimError is GeocodingException && nominatimError.errorType == GeocodingErrorType.TIMEOUT)

        if (isNetworkFailure) {
            throw GeocodingException(
                errorType = GeocodingErrorType.NETWORK_ERROR,
                message = "Unable to connect to geocoding services. Check your internet connection."
            )
        }

        if (isTimeout) {
            throw GeocodingException(
                errorType = GeocodingErrorType.TIMEOUT,
                message = "Geocoding requests timed out. Please try again."
            )
        }

        throw GeocodingException(
            errorType = GeocodingErrorType.NO_RESULTS,
            message = "No matching location found for \"$query\"."
        )
    }

    private fun cacheResults(key: String, results: List<GeocodingResult>) {
        memoryCache[key] = CacheEntry(results = results, timestamp = System.currentTimeMillis())
    }

    fun clearCache() {
        memoryCache.clear()
    }
}

