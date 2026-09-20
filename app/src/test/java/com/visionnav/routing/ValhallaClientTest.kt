package com.visionnav.routing

import com.visionnav.location.CurrentLocation
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ValhallaClientTest {

    private fun createMockHttpClient(
        responseCode: Int = 200,
        responseBody: String = "",
        exceptionToThrow: IOException? = null,
        onRequestCaptured: ((String) -> Unit)? = null
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            onRequestCaptured?.invoke(buffer.readUtf8())

            if (exceptionToThrow != null) {
                throw exceptionToThrow
            }
            Response.Builder()
                .request(request)
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

    private val sampleValhallaJson = """
        {
          "trip": {
            "summary": {
              "length": 1.45,
              "time": 1080
            },
            "legs": [
              {
                "shape": "o`_~F_`~eO?_`~eO",
                "maneuvers": [
                  {
                    "type": 1,
                    "instruction": "Walk straight on Main Street",
                    "length": 0.8,
                    "time": 600,
                    "street_names": ["Main Street"],
                    "begin_shape_index": 0,
                    "end_shape_index": 1
                  },
                  {
                    "type": 4,
                    "instruction": "You have arrived at your destination",
                    "length": 0.65,
                    "time": 480,
                    "street_names": ["Destination Way"],
                    "begin_shape_index": 1,
                    "end_shape_index": 1
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `TEST 1 - Valid Valhalla response parses into NavigationRoute`() = runBlocking {
        val client = ValhallaClient(createMockHttpClient(responseBody = sampleValhallaJson))
        val route = client.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)

        assertNotNull(route)
        assertEquals(1450, route.totalDistanceMeters)
        assertEquals(1080, route.estimatedDurationSeconds)
        assertEquals(2, route.maneuvers.size)
        assertEquals("Main Street", route.maneuvers[0].streetName)
        assertEquals(ManeuverType.STRAIGHT, route.maneuvers[0].type)
        assertEquals(ManeuverType.DESTINATION, route.maneuvers[1].type)
    }

    @Test
    fun `TEST 2 - Empty route response throws EMPTY_ROUTE exception`() = runBlocking {
        val emptyTripJson = """{ "trip": { "summary": { "length": 0, "time": 0 }, "legs": [] } }"""
        val client = ValhallaClient(createMockHttpClient(responseBody = emptyTripJson))

        try {
            client.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)
            fail("Expected RoutingException for empty route legs")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.EMPTY_ROUTE, e.errorType)
        }
    }

    @Test
    fun `TEST 3 - Malformed JSON response throws MALFORMED_RESPONSE exception`() = runBlocking {
        val client = ValhallaClient(createMockHttpClient(responseBody = "invalid-not-json"))

        try {
            client.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)
            fail("Expected RoutingException for malformed JSON")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.MALFORMED_RESPONSE, e.errorType)
        }
    }

    @Test
    fun `TEST 4 - HTTP error returns SERVER_ERROR exception`() = runBlocking {
        val client = ValhallaClient(createMockHttpClient(responseCode = 500, responseBody = "Server Error"))

        try {
            client.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)
            fail("Expected RoutingException for HTTP 500")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.SERVER_ERROR, e.errorType)
        }
    }

    @Test
    fun `TEST 5 - Network failure and timeout throw structured exceptions`() = runBlocking {
        // Network failure
        val netClient = ValhallaClient(createMockHttpClient(exceptionToThrow = IOException("Connection refused")))
        try {
            netClient.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)
            fail("Expected NETWORK_ERROR")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.NETWORK_ERROR, e.errorType)
        }

        // Timeout
        val timeoutClient = ValhallaClient(createMockHttpClient(exceptionToThrow = SocketTimeoutException("Timed out")))
        try {
            timeoutClient.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)
            fail("Expected TIMEOUT")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.TIMEOUT, e.errorType)
        }
    }

    @Test
    fun `TEST 6 - Invalid coordinates rejected before network call`() = runBlocking {
        val client = ValhallaClient(createMockHttpClient(responseBody = sampleValhallaJson))

        try {
            // Latitude > 90
            client.getPedestrianRoute(95.0, 80.2707, 13.0840, 80.2730)
            fail("Expected INVALID_COORDINATES for lat > 90")
        } catch (e: RoutingException) {
            assertEquals(RoutingErrorType.INVALID_COORDINATES, e.errorType)
        }
    }

    @Test
    fun `TEST 7 - Pedestrian costing with sidewalk preference is requested`() = runBlocking {
        var capturedPayload = ""
        val client = ValhallaClient(
            createMockHttpClient(responseBody = sampleValhallaJson) { payload ->
                capturedPayload = payload
            }
        )

        val origin = CurrentLocation(13.0827, 80.2707, 5.0f)
        val destination = Destination(13.0840, 80.2730, "Market", GeocodingSource.PHOTON)
        client.getPedestrianRoute(origin, destination)

        assertTrue(capturedPayload.contains("\"costing\":\"pedestrian\""))
        assertTrue(capturedPayload.contains("\"use_sidewalk\":1"))
        assertTrue(capturedPayload.contains("\"step_penalty\":20"))
    }

    @Test
    fun `TEST 8 - Route summary uses actual distance and duration values`() = runBlocking {
        val client = ValhallaClient(createMockHttpClient(responseBody = sampleValhallaJson))
        val route = client.getPedestrianRoute(13.0827, 80.2707, 13.0840, 80.2730)

        val summary = route.summary
        assertEquals(1450, summary.distanceMeters)
        assertEquals(1080, summary.durationSeconds)
        assertTrue(summary.text.contains("1.5 kilometers") || summary.text.contains("1.4 kilometers"))
        assertTrue(summary.text.contains("18 minutes"))
    }
}

