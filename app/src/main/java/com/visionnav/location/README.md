# Location Subsystem (Phase 3)

This package handles Android device positioning:
- `LocationProvider.kt`: FusedLocationProviderClient wrapper for GPS coordinates.
- `HeadingTracker.kt`: Compass/azimuth tracking.
- `AccuracyFilter.kt`: Rejects poor GPS fixes to avoid route jumping.

