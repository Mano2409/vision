package com.visionnav.environment

import com.visionnav.vision.DistanceCategory

/**
 * High-level classification of the dominant physical environment surrounding the pedestrian.
 */
enum class SceneCategory {
    UNKNOWN,
    CLEAR_PATH,
    PEDESTRIAN_AREA,
    VEHICULAR_ROADWAY,
    MIXED_TRAFFIC,
    OBSTRUCTED_PATH
}

/**
 * Spatial information regarding the user's forward walking corridor.
 */
data class PathInformation(
    val isCorridorClear: Boolean = true,
    val nearestObstacleDistance: DistanceCategory? = null,
    val blockingObjects: List<EnvironmentObject> = emptyList()
)

/**
 * Immutable snapshot of the pedestrian's physical environment at a specific moment in time.
 *
 * Provides a structured semantic representation of visible objects, corridor path status,
 * dominant scene characteristics, and camera freshness.
 */
data class EnvironmentSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val visibleObjects: List<EnvironmentObject> = emptyList(),
    val dominantScene: SceneCategory = SceneCategory.UNKNOWN,
    val pathInformation: PathInformation = PathInformation(),
    val nearbyUsefulObjects: List<EnvironmentObject> = emptyList(),
    val overallConfidence: Float = 1.0f,
    val isCameraActive: Boolean = true,
    val isStale: Boolean = false
) {
    /**
     * Checks if this snapshot is fresh relative to the provided current time and timeout.
     *
     * @param currentTimeMs Current time in milliseconds.
     * @param timeoutMs Maximum allowed age before snapshot is considered stale (default 3000ms).
     * @return True if snapshot is non-stale and within timeout window.
     */
    fun isFresh(
        currentTimeMs: Long = System.currentTimeMillis(),
        timeoutMs: Long = 3000L
    ): Boolean {
        return !isStale && (currentTimeMs - timestampMs) <= timeoutMs
    }

    companion object {
        /**
         * Represents an empty environment where no obstacles or objects are detected.
         */
        val EMPTY = EnvironmentSnapshot(
            dominantScene = SceneCategory.CLEAR_PATH,
            pathInformation = PathInformation(isCorridorClear = true)
        )

        /**
         * Represents an expired, stale, or unavailable environmental state.
         */
        val STALE = EnvironmentSnapshot(
            dominantScene = SceneCategory.UNKNOWN,
            isStale = true,
            pathInformation = PathInformation(isCorridorClear = false)
        )
    }
}

