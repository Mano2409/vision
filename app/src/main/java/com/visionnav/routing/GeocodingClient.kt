package com.visionnav.routing

/**
 * Categorized error types for geocoding failures.
 */
enum class GeocodingErrorType {
    NO_RESULTS,
    NETWORK_ERROR,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    INVALID_RESULT
}

/**
 * Domain exception for geocoding operations with user-friendly error types.
 */
class GeocodingException(
    val errorType: GeocodingErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Abstraction for converting human-spoken place names into latitude/longitude coordinates.
 */
interface IGeocodingClient {
    /**
     * Resolves a place query string (e.g. "Chennai Central") into a list of candidate results.
     * Returns empty list or throws GeocodingException on failure.
     */
    suspend fun search(query: String): List<GeocodingResult>
}

