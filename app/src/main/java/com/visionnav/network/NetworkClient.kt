package com.visionnav.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP Network Client for VisionNav.
 *
 * Configures connection pooling, standard 15-second timeouts, and
 * enforces an identifiable User-Agent in accordance with OpenStreetMap
 * Nominatim and external API usage policies.
 */
object NetworkClient {

    private const val USER_AGENT = "VisionNav-AssistiveApp/1.0 (Android; BVI Assistive Navigation)"
    private const val TIMEOUT_SECONDS = 15L

    private val userAgentInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(newRequest)
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }
}

