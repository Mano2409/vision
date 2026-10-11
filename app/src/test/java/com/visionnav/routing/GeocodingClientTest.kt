package com.visionnav.routing

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class GeocodingClientTest {

    private fun createMockHttpClient(
        responseCode: Int = 200,
        responseBody: String = "",
        exceptionToThrow: IOException? = null
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            if (exceptionToThrow != null) {
                throw exceptionToThrow
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("Mock Response")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    private class FakeGeocoder(
        private val resultsToReturn: List<GeocodingResult> = emptyList(),
        private val shouldThrow: Throwable? = null
    ) : IGeocodingClient {
        var callCount = 0
        var lastUserLat: Double? = null
        var lastUserLon: Double? = null

        override suspend fun search(query: String, userLat: Double?, userLon: Double?): List<GeocodingResult> {
            callCount++
            lastUserLat = userLat
            lastUserLon = userLon
            if (shouldThrow != null) throw shouldThrow
            return resultsToReturn
        }
    }

    // ==========================================
    // PHOTON TESTS (8 - 12)
    // ==========================================

    @Test
    fun `TEST 8 - Photon successful response extracts coordinates and display name`() = runBlocking {
        val validGeoJson = """
            {
              "features": [
                {
                  "geometry": {
                    "coordinates": [80.2707, 13.0827]
                  },
                  "properties": {
                    "osm_id": 12345,
                    "name": "Chennai Central",
                    "street": "EVR Periyar Salai",
                    "city": "Chennai",
                    "state": "Tamil Nadu",
                    "country": "India",
                    "osm_value": "station"
                  }
                }
              ]
            }
        """.trimIndent()

        val geocoder = PhotonGeocoder(createMockHttpClient(responseBody = validGeoJson))
        val results = geocoder.search("Chennai Central")

        assertEquals(1, results.size)
        assertEquals(13.0827, results[0].latitude, 0.0001)
        assertEquals(80.2707, results[0].longitude, 0.0001)
        assertTrue(results[0].displayName.contains("Chennai Central"))
        assertEquals("Photon", results[0].provider)

        val destination = results[0].toDestination()
        assertNotNull(destination)
        assertEquals(GeocodingSource.PHOTON, destination!!.source)
    }

    @Test
    fun `TEST 9 - Photon empty response returns empty list without error`() = runBlocking {
        val emptyGeoJson = """{ "features": [] }"""
        val geocoder = PhotonGeocoder(createMockHttpClient(responseBody = emptyGeoJson))
        val results = geocoder.search("NonExistentPlace12345")

        assertTrue("Empty feature list should return empty result", results.isEmpty())
    }

    @Test
    fun `TEST 10 - Photon malformed JSON response throws structured INVALID_RESULT error`() = runBlocking {
        val malformedJson = """{ "features": [ { "geometry": { "coordinates": "not_an_array" } } ] }"""
        val geocoder = PhotonGeocoder(createMockHttpClient(responseBody = malformedJson))

        try {
            geocoder.search("Malformed Test")
            fail("Expected GeocodingException for malformed JSON")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.INVALID_RESULT, e.errorType)
        }
    }

    @Test
    fun `TEST 11 - Photon network failure throws structured NETWORK_ERROR`() = runBlocking {
        val geocoder = PhotonGeocoder(createMockHttpClient(exceptionToThrow = IOException("Connection reset")))

        try {
            geocoder.search("Network Failure Test")
            fail("Expected GeocodingException for network failure")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.NETWORK_ERROR, e.errorType)
        }
    }

    @Test
    fun `TEST 12 - Photon timeout throws structured TIMEOUT error`() = runBlocking {
        val geocoder = PhotonGeocoder(createMockHttpClient(exceptionToThrow = SocketTimeoutException("Read timed out")))

        try {
            geocoder.search("Timeout Test")
            fail("Expected GeocodingException for timeout")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.TIMEOUT, e.errorType)
        }
    }

    // ==========================================
    // NOMINATIM TESTS (13 - 16)
    // ==========================================

    @Test
    fun `TEST 13 - Photon failure triggers Nominatim fallback`() = runBlocking {
        val fallbackResults = listOf(
            GeocodingResult(
                id = "nominatim_1",
                displayName = "Marina Beach, Chennai",
                latitude = 13.0500,
                longitude = 80.2824,
                provider = "Nominatim"
            )
        )
        val primaryFailing = FakeGeocoder(shouldThrow = GeocodingException(GeocodingErrorType.SERVER_ERROR, "Photon 500"))
        val fallback = FakeGeocoder(resultsToReturn = fallbackResults)
        val composite = CompositeGeocodingClient(primaryFailing, fallback)

        val results = composite.search("Marina Beach")
        assertEquals(1, results.size)
        assertEquals("Nominatim", results[0].provider)
        assertEquals(1, primaryFailing.callCount)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `TEST 14 - Successful Nominatim response parsed into valid GeocodingResult`() = runBlocking {
        val nominatimJson = """
            [
              {
                "place_id": 998877,
                "lat": "13.0827",
                "lon": "80.2707",
                "display_name": "Chennai Central Railway Station, Kannappar Thidal, Chennai",
                "type": "station"
              }
            ]
        """.trimIndent()

        val geocoder = NominatimGeocoder(createMockHttpClient(responseBody = nominatimJson))
        val results = geocoder.search("Chennai Central")

        assertEquals(1, results.size)
        assertEquals(13.0827, results[0].latitude, 0.0001)
        assertEquals(80.2707, results[0].longitude, 0.0001)
        assertEquals("Nominatim", results[0].provider)

        val destination = results[0].toDestination()
        assertNotNull(destination)
        assertEquals(GeocodingSource.NOMINATIM, destination!!.source)
    }

    @Test
    fun `TEST 15 - When both Photon and Nominatim fail, structured error is returned`() = runBlocking {
        val primary = FakeGeocoder(shouldThrow = GeocodingException(GeocodingErrorType.NETWORK_ERROR, "No connection"))
        val fallback = FakeGeocoder(shouldThrow = GeocodingException(GeocodingErrorType.NETWORK_ERROR, "No connection"))
        val composite = CompositeGeocodingClient(primary, fallback)

        try {
            composite.search("Chennai Central")
            fail("Expected GeocodingException but search completed")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.NETWORK_ERROR, e.errorType)
        }
    }

    @Test
    fun `TEST 16 - Nominatim rate-limit and server error handling`() = runBlocking {
        // HTTP 429
        val rateLimitGeocoder = NominatimGeocoder(createMockHttpClient(responseCode = 429))
        try {
            rateLimitGeocoder.search("Rate Limit Test")
            fail("Expected 429 RATE_LIMITED")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.RATE_LIMITED, e.errorType)
        }

        // HTTP 500
        val serverErrorGeocoder = NominatimGeocoder(createMockHttpClient(responseCode = 500))
        try {
            serverErrorGeocoder.search("Server Error Test")
            fail("Expected 500 SERVER_ERROR")
        } catch (e: GeocodingException) {
            assertEquals(GeocodingErrorType.SERVER_ERROR, e.errorType)
        }
    }

    // ==========================================
    // VALIDATION TESTS (17 - 19)
    // ==========================================

    @Test
    fun `TEST 17 - Invalid coordinates rejected from geocoding results`() = runBlocking {
        val outOfBoundsGeoJson = """
            {
              "features": [
                {
                  "geometry": { "coordinates": [200.0, 95.0] },
                  "properties": { "name": "Invalid Coordinate Place" }
                }
              ]
            }
        """.trimIndent()

        val geocoder = PhotonGeocoder(createMockHttpClient(responseBody = outOfBoundsGeoJson))
        val results = geocoder.search("Invalid Coord Place")

        assertTrue("Invalid coordinates must be filtered out", results.isEmpty())
        assertFalse(Destination.isValidCoordinate(95.0, 200.0))
    }

    @Test
    fun `TEST 18 - Valid coordinates accepted and verified`() {
        val validDest = Destination(
            latitude = 13.0827,
            longitude = 80.2707,
            displayName = "Chennai Central",
            source = GeocodingSource.PHOTON
        )
        assertTrue(validDest.isValid)
        assertTrue(Destination.isValidCoordinate(13.0827, 80.2707))
    }

    @Test
    fun `TEST 19 - Unsuitable result with blank display name rejected`() {
        val result = GeocodingResult(
            id = "1",
            displayName = "   ",
            latitude = 13.0827,
            longitude = 80.2707,
            provider = "Photon"
        )
        assertNull("Blank display name must reject Destination conversion", result.toDestination())
    }

    // ==========================================
    // CACHE TESTS (20 - 22)
    // ==========================================

    @Test
    fun `TEST 20 - Cache hit returns cached results without calling network`() = runBlocking {
        val mockResults = listOf(
            GeocodingResult(
                id = "photon_1",
                displayName = "Guindy Railway Station, Chennai",
                latitude = 13.0067,
                longitude = 80.2128,
                provider = "Photon"
            )
        )
        val primary = FakeGeocoder(resultsToReturn = mockResults)
        val composite = CompositeGeocodingClient(primary, FakeGeocoder())

        // 1st Search -> Network called
        val res1 = composite.search("Guindy Station")
        assertEquals(1, primary.callCount)

        // 2nd Search -> Cache HIT
        val res2 = composite.search("Guindy Station")
        assertEquals(1, primary.callCount)
        assertEquals(res1[0].displayName, res2[0].displayName)
    }

    @Test
    fun `TEST 21 - Cache miss triggers network lookup`() = runBlocking {
        val primary = FakeGeocoder(
            resultsToReturn = listOf(
                GeocodingResult(id = "1", displayName = "Airport", latitude = 12.99, longitude = 80.17, provider = "Photon")
            )
        )
        val composite = CompositeGeocodingClient(primary, FakeGeocoder())

        composite.search("Chennai Airport")
        assertEquals(1, primary.callCount)

        composite.search("Central Railway Station")
        assertEquals(2, primary.callCount)
    }

    @Test
    fun `TEST 22 - Normalized query with spaces and casing produces same cache key`() = runBlocking {
        val mockResults = listOf(
            GeocodingResult(
                id = "photon_1",
                displayName = "Marina Beach, Chennai",
                latitude = 13.0500,
                longitude = 80.2824,
                provider = "Photon"
            )
        )
        val primary = FakeGeocoder(resultsToReturn = mockResults)
        val composite = CompositeGeocodingClient(primary, FakeGeocoder())

        // Search with irregular spacing and mixed case
        composite.search("  Marina   Beach  ")
        assertEquals(1, primary.callCount)

        // Search with normalized form should HIT cache
        val cached = composite.search("marina beach")
        assertEquals(1, primary.callCount)
        assertEquals(1, cached.size)

        // Verify normalization function directly
        assertEquals("chennai central", CompositeGeocodingClient.normalizeQuery("  Chennai   Central  "))
    }

    // ==========================================
    // LOCATION-AWARE GEOCODING TESTS (23 - 29)
    // ==========================================

    @Test
    fun `TEST 23 - Current GPS coordinates are passed to geocoders`() = runBlocking {
        val primary = FakeGeocoder(resultsToReturn = listOf(
            GeocodingResult("1", "Local Place", 13.0827, 80.2707, provider = "Photon")
        ))
        val composite = CompositeGeocodingClient(primary, FakeGeocoder())

        composite.search("Local Place", userLat = 13.0827, userLon = 80.2707)
        assertEquals(13.0827, primary.lastUserLat!!, 0.0001)
        assertEquals(80.2707, primary.lastUserLon!!, 0.0001)
    }

    @Test
    fun `TEST 24 - Photon receives geographic context when coordinates provided`() = runBlocking {
        var requestedUrl: String? = null
        val interceptor = Interceptor { chain ->
            requestedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"features":[]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val geocoder = PhotonGeocoder(client)

        geocoder.search("Central Station", userLat = 13.0827, userLon = 80.2707)
        assertNotNull(requestedUrl)
        assertTrue(requestedUrl!!.contains("lat=13.0827"))
        assertTrue(requestedUrl!!.contains("lon=80.2707"))
    }

    @Test
    fun `TEST 25 - Nominatim receives viewbox when coordinates provided`() = runBlocking {
        var requestedUrl: String? = null
        val interceptor = Interceptor { chain ->
            requestedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""[]""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val geocoder = NominatimGeocoder(client)

        geocoder.search("Central Station", userLat = 13.0827, userLon = 80.2707)
        assertNotNull(requestedUrl)
        assertTrue(requestedUrl!!.contains("viewbox="))
        assertTrue(requestedUrl!!.contains("bounded=0"))
    }

    @Test
    fun `TEST 26 - Nearby candidate beats distant candidate in proximity ranking`() = runBlocking {
        // User at Chennai (13.0827, 80.2707)
        val userLat = 13.0827
        val userLon = 80.2707

        val distantGeneva = GeocodingResult("1", "Geneva, Switzerland", 46.2102, 6.1426, provider = "Photon")
        val distantVancouver = GeocodingResult("2", "Vancouver, Canada", 49.2856, -123.1116, provider = "Photon")
        val distantDelhi = GeocodingResult("3", "Delhi, India", 28.6734, 77.2897, provider = "Photon")
        val nearbyCandidate = GeocodingResult("4", "Chennai Metro Station", 13.0820, 80.2710, provider = "Photon")

        val candidates = listOf(distantGeneva, distantVancouver, distantDelhi, nearbyCandidate)
        val composite = CompositeGeocodingClient(FakeGeocoder(), FakeGeocoder())
        val ranked = composite.rankCandidates(candidates, userLat, userLon)

        assertEquals("Chennai Metro Station", ranked.first().displayName)
        assertNotNull(ranked.first().distanceMeters)
        assertTrue(ranked.first().distanceMeters!! < 1000.0) // < 1 km
        assertEquals("Vancouver, Canada", ranked.last().displayName)
    }

    @Test
    fun `TEST 27 - Photon returning only distant candidates triggers Nominatim fallback`() = runBlocking {
        val userLat = 13.0827
        val userLon = 80.2707

        // Photon returns only distant results (> 1000 km, e.g. Geneva at 7500 km)
        val photonDistant = listOf(
            GeocodingResult("1", "Geneva Station", 46.2102, 6.1426, provider = "Photon"),
            GeocodingResult("2", "Vancouver Station", 49.2856, -123.1116, provider = "Photon")
        )
        // Nominatim returns a local result (1 km away)
        val nominatimLocal = listOf(
            GeocodingResult("3", "Chennai Central Metro", 13.0825, 80.2763, provider = "Nominatim")
        )

        val primary = FakeGeocoder(resultsToReturn = photonDistant)
        val fallback = FakeGeocoder(resultsToReturn = nominatimLocal)
        val composite = CompositeGeocodingClient(primary, fallback)

        val results = composite.search("Metro Station", userLat, userLon)

        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount) // Nominatim WAS triggered despite non-empty Photon!
        assertEquals("Chennai Central Metro", results.first().displayName)
        assertEquals("Nominatim", results.first().provider)
    }

    @Test
    fun `TEST 28 - Geocoding without GPS coordinates succeeds without crashing`() = runBlocking {
        val mockResults = listOf(
            GeocodingResult("1", "Eiffel Tower", 48.8584, 2.2945, provider = "Photon")
        )
        val primary = FakeGeocoder(resultsToReturn = mockResults)
        val composite = CompositeGeocodingClient(primary, FakeGeocoder())

        val results = composite.search("Eiffel Tower", userLat = null, userLon = null)
        assertEquals(1, results.size)
        assertEquals("Eiffel Tower", results.first().displayName)
    }

    @Test
    fun `TEST 29 - Destination sanity check detects distant anomalies`() {
        // User at Chennai (13.0827, 80.2707)
        val userLat = 13.0827
        val userLon = 80.2707

        val nearbyDest = Destination(13.0825, 80.2763, "Chennai Central", GeocodingSource.PHOTON)
        val genevaDest = Destination(46.2102, 6.1426, "Geneva", GeocodingSource.PHOTON)

        assertTrue("Nearby destination should be reasonable", nearbyDest.isReasonableForOrigin(userLat, userLon))
        assertFalse("Geneva (7500 km) should NOT be reasonable for Chennai pedestrian origin",
            genevaDest.isReasonableForOrigin(userLat, userLon, maxDistanceMeters = 1_000_000.0))
    }
}
