package com.visionnav.routing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeocodingClientTest {

    private class FakeGeocoder(
        private val resultsToReturn: List<GeocodingResult> = emptyList(),
        private val shouldThrow: Throwable? = null
    ) : IGeocodingClient {
        var callCount = 0

        override suspend fun search(query: String): List<GeocodingResult> {
            callCount++
            if (shouldThrow != null) throw shouldThrow
            return resultsToReturn
        }
    }

    @Test
    fun `TEST 7 - Primary geocoder returns valid GeocodingResult`() = runBlocking {
        val expected = listOf(
            GeocodingResult(
                id = "photon_1",
                displayName = "Chennai Central, Periyamet, Chennai",
                latitude = 13.0827,
                longitude = 80.2707,
                provider = "Photon"
            )
        )
        val primary = FakeGeocoder(resultsToReturn = expected)
        val fallback = FakeGeocoder(resultsToReturn = emptyList())
        val composite = CompositeGeocodingClient(primary, fallback)

        val results = composite.search("Chennai Central")
        assertEquals(1, results.size)
        assertEquals("Chennai Central, Periyamet, Chennai", results[0].displayName)
        assertEquals(13.0827, results[0].latitude, 0.0001)
        assertEquals(1, primary.callCount)
        assertEquals(0, fallback.callCount) // Fallback was not needed
    }

    @Test
    fun `TEST 8 - When primary fails, fallback provider is executed`() = runBlocking {
        val fallbackResults = listOf(
            GeocodingResult(
                id = "nominatim_1",
                displayName = "Chennai Central, Park Town, Chennai",
                latitude = 13.0827,
                longitude = 80.2707,
                provider = "Nominatim"
            )
        )
        val primaryFailing = FakeGeocoder(
            shouldThrow = GeocodingException(GeocodingErrorType.SERVER_ERROR, "Photon 500 error")
        )
        val fallback = FakeGeocoder(resultsToReturn = fallbackResults)
        val composite = CompositeGeocodingClient(primaryFailing, fallback)

        val results = composite.search("Chennai Central")
        assertEquals(1, results.size)
        assertEquals("Nominatim", results[0].provider)
        assertEquals(1, primaryFailing.callCount)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `TEST 9 - When both providers fail, structured exception is thrown`() = runBlocking {
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
    fun `TEST 10 - Local cache returns cached result on repeat search without calling network`() = runBlocking {
        val mockResults = listOf(
            GeocodingResult(
                id = "photon_1",
                displayName = "Public Library, Chennai",
                latitude = 13.0645,
                longitude = 80.2600,
                provider = "Photon"
            )
        )
        val primary = FakeGeocoder(resultsToReturn = mockResults)
        val fallback = FakeGeocoder()
        val composite = CompositeGeocodingClient(primary, fallback)

        // 1st Search -> Network called
        val res1 = composite.search("Public Library")
        assertEquals(1, primary.callCount)

        // 2nd Search for same query -> Cache HIT, no new network call
        val res2 = composite.search("Public Library")
        assertEquals(1, primary.callCount)
        assertEquals(res1[0].displayName, res2[0].displayName)
    }

    @Test
    fun `TEST 11 - Offline MockGeocodingClient provides simulated destinations without network`() = runBlocking {
        val mockClient = MockGeocodingClient()
        val results = mockClient.search("Chennai Central")

        assertTrue(results.isNotEmpty())
        assertEquals("SIMULATOR_MOCK", results[0].provider)
        assertTrue(results[0].displayName.contains("Simulated"))
    }

    @Test
    fun `TEST 12 - Multiple search results returned for ambiguous destinations`() = runBlocking {
        val mockClient = MockGeocodingClient()
        // Querying "City Hospital" matches multiple branch locations in mock data
        val results = mockClient.search("City Hospital")

        assertTrue("Expected multiple results for ambiguous query 'City Hospital', found ${results.size}", results.size > 1)
        assertTrue(results.any { it.displayName.contains("Anna Nagar") })
        assertTrue(results.any { it.displayName.contains("Tambaram") })
    }
}

