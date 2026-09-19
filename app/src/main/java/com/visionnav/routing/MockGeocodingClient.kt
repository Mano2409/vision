package com.visionnav.routing

/**
 * Development & Offline Mock Geocoding Provider.
 *
 * Supplies realistic, verified coordinates for standard testing scenarios
 * without internet access. All results are explicitly tagged as simulated data.
 */
class MockGeocodingClient : IGeocodingClient {

    companion object {
        val MOCK_DESTINATIONS = listOf(
            GeocodingResult(
                id = "mock_chennai_central",
                displayName = "Chennai Central Railway Station (Simulated)",
                latitude = 13.0827,
                longitude = 80.2707,
                category = "railway_station",
                addressSnippet = "Kannappar Thidal, Periyamet, Chennai, Tamil Nadu",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_city_hospital",
                displayName = "City General Hospital (Simulated)",
                latitude = 13.0810,
                longitude = 80.2790,
                category = "hospital",
                addressSnippet = "EVR Periyar Salai, Park Town, Chennai",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_city_hospital_anna_nagar",
                displayName = "City Hospital — Anna Nagar (Simulated)",
                latitude = 13.0850,
                longitude = 80.2100,
                category = "hospital",
                addressSnippet = "2nd Avenue, Anna Nagar, Chennai",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_city_hospital_tambaram",
                displayName = "City Hospital — Tambaram (Simulated)",
                latitude = 12.9249,
                longitude = 80.1000,
                category = "hospital",
                addressSnippet = "GST Road, Tambaram, Chennai",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_public_library",
                displayName = "Connemara Public Library (Simulated)",
                latitude = 13.0645,
                longitude = 80.2600,
                category = "library",
                addressSnippet = "Pantheon Road, Egmore, Chennai",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_metro_station",
                displayName = "Central Metro Station Gate 2 (Simulated)",
                latitude = 13.0818,
                longitude = 80.2720,
                category = "subway_entrance",
                addressSnippet = "Poonamallee High Rd, Chennai",
                provider = "SIMULATOR_MOCK"
            ),
            GeocodingResult(
                id = "mock_pine_street",
                displayName = "Pine Street Pedestrian Crossing (Simulated)",
                latitude = 13.0805,
                longitude = 80.2695,
                category = "crossing",
                addressSnippet = "Pine Street Corridor, Chennai",
                provider = "SIMULATOR_MOCK"
            )
        )
    }

    override suspend fun search(query: String): List<GeocodingResult> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return emptyList()

        // Filter mock destinations by matching query terms
        val matches = MOCK_DESTINATIONS.filter { destination ->
            val name = destination.displayName.lowercase()
            val address = destination.addressSnippet?.lowercase() ?: ""
            val cat = destination.category?.lowercase() ?: ""
            name.contains(trimmed) || address.contains(trimmed) || cat.contains(trimmed) ||
                    trimmed.split(" ").any { word -> word.length > 2 && (name.contains(word) || address.contains(word)) }
        }

        if (matches.isEmpty()) {
            // If user searched for something generic in mock mode, return default test set
            return listOf(
                GeocodingResult(
                    id = "mock_generic_${System.currentTimeMillis()}",
                    displayName = "$query (Simulated Location)",
                    latitude = 13.0827,
                    longitude = 80.2707,
                    category = "simulated_point",
                    addressSnippet = "VisionNav Test Lab, Chennai",
                    provider = "SIMULATOR_MOCK"
                )
            )
        }

        return matches
    }
}

