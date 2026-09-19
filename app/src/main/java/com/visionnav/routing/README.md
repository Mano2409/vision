# Routing Subsystem (Phase 4)

This package handles OpenStreetMap geocoding and Valhalla pedestrian routing:
- `ValhallaClient.kt`: HTTP client requesting pedestrian costing routes.
- `GeocodingClient.kt`: OSM Nominatim / Photon client converting spoken destination names into GPS coordinates.
- `RouteGeometryParser.kt`: Polyline decoding and maneuver list extraction.
