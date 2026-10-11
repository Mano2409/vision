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
        const val MAX_LOCAL_REASONABLE_DISTANCE_METERS = 1_000_000.0 // 1000 km threshold for local candidate expectation

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
     * Uses the most relevant (first valid) result after proximity ranking.
     * Throws GeocodingException on failure.
     */
    suspend fun resolveDestination(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): Destination {
        val results = search(query, userLat, userLon)
        return results.firstNotNullOfOrNull { it.toDestination() }
            ?: throw GeocodingException(
                errorType = GeocodingErrorType.NO_RESULTS,
                message = "No matching location found for \"$query\"."
            )
    }

    override suspend fun search(
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<GeocodingResult> {
        val normalizedKey = normalizeQuery(query)
        if (normalizedKey.isBlank()) return emptyList()

        val hasUserLocation = userLat != null && userLon != null && Destination.isValidCoordinate(userLat, userLon)
        val cacheKey = if (hasUserLocation) {
            val latBucket = (userLat!! * 10).toInt()
            val lonBucket = (userLon!! * 10).toInt()
            "${normalizedKey}_${latBucket}_${lonBucket}"
        } else {
            normalizedKey
        }

        // 1. Check local in-memory cache
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.timestamp
            if (age < cacheTtlMs && cached.results.isNotEmpty()) {
                Log.d(TAG, "Cache HIT for '$query' (age: ${age / 1000}s)")
                return cached.results
            } else {
                memoryCache.remove(cacheKey)
            }
        }

        // 2. Try Primary: Photon
        var photonResults: List<GeocodingResult>? = null
        var photonError: Throwable? = null
        try {
            val results = photonGeocoder.search(query, userLat, userLon)
            if (results.isNotEmpty()) {
                photonResults = rankCandidates(results, userLat, userLon)
            }
        } catch (e: Throwable) {
            photonError = e
            Log.w(TAG, "Photon geocoding failed for '$query', attempting Nominatim fallback: ${e.message}")
        }

        // Check if Photon returned a reasonable candidate
        // If user location is known and all Photon candidates are distant (> 1000 km),
        // we should try Nominatim before settling on distant results.
        val hasReasonablePhotonCandidate = if (hasUserLocation && !photonResults.isNullOrEmpty()) {
            val minDistance = photonResults.minOfOrNull { it.distanceMeters ?: Double.MAX_VALUE } ?: Double.MAX_VALUE
            minDistance <= MAX_LOCAL_REASONABLE_DISTANCE_METERS
        } else {
            !photonResults.isNullOrEmpty()
        }

        if (!photonResults.isNullOrEmpty() && hasReasonablePhotonCandidate) {
            cacheResults(cacheKey, photonResults)
            return photonResults
        }

        // 3. Try Fallback: Nominatim
        var nominatimResults: List<GeocodingResult>? = null
        var nominatimError: Throwable? = null
        try {
            val results = nominatimGeocoder.search(query, userLat, userLon)
            if (results.isNotEmpty()) {
                nominatimResults = rankCandidates(results, userLat, userLon)
            }
        } catch (e: Throwable) {
            nominatimError = e
            Log.w(TAG, "Nominatim geocoding failed for '$query': ${e.message}")
        }

        // Combine or select best candidates
        val finalResults = when {
            !nominatimResults.isNullOrEmpty() && !photonResults.isNullOrEmpty() -> {
                // If Nominatim found closer candidates, prioritize them; otherwise combine and rank
                val combined = (nominatimResults + photonResults)
                    .distinctBy { "${it.latitude.toFloat()}_${it.longitude.toFloat()}" }
                rankCandidates(combined, userLat, userLon)
            }
            !nominatimResults.isNullOrEmpty() -> nominatimResults
            !photonResults.isNullOrEmpty() -> photonResults // Controlled expansion: return distant Photon results if Nominatim also found nothing
            else -> emptyList()
        }

        if (finalResults.isNotEmpty()) {
            cacheResults(cacheKey, finalResults)
            return finalResults
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

    /**
     * Ranks candidates prioritizing proximity to user location and regional relevance.
     */
    fun rankCandidates(
        candidates: List<GeocodingResult>,
        userLat: Double?,
        userLon: Double?
    ): List<GeocodingResult> {
        if (userLat == null || userLon == null || !Destination.isValidCoordinate(userLat, userLon)) {
            return candidates
        }

        return candidates.map { it.withDistanceTo(userLat, userLon) }
            .sortedWith(
                compareBy<GeocodingResult> { candidate ->
                    val dist = candidate.distanceMeters ?: Double.MAX_VALUE
                    when {
                        dist <= 50_000.0 -> 0      // Local / walking / city (< 50 km)
                        dist <= 300_000.0 -> 1     // Regional / nearby cities (< 300 km)
                        dist <= 1_500_000.0 -> 2   // Same sub-continent / national (< 1500 km)
                        else -> 3                  // Distant / international (> 1500 km)
                    }
                }.thenBy { it.distanceMeters ?: Double.MAX_VALUE }
            )
    }

    private fun cacheResults(key: String, results: List<GeocodingResult>) {
        memoryCache[key] = CacheEntry(results = results, timestamp = System.currentTimeMillis())
    }

    fun clearCache() {
        memoryCache.clear()
    }
}

