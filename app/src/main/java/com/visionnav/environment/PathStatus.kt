package com.visionnav.environment

import com.visionnav.vision.DistanceCategory

/**
 * High-level status of the pedestrian's forward walking path.
 *
 * Implements assistive humility: [OPEN] is explicitly described as
 * "The path appears open" rather than "completely safe".
 */
enum class PathStatus(val description: String) {
    /** Forward path appears free of detected obstructions. */
    OPEN("The path appears open."),

    /** Forward path contains peripheral or minor obstructions that may require slight deviation. */
    PARTIALLY_BLOCKED("The path appears partially blocked."),

    /** Forward path is significantly obstructed by obstacles. */
    BLOCKED("The path appears blocked."),

    /** Environmental or distance uncertainty prevents reliable assessment. */
    UNKNOWN("Path status is uncertain."),

    /** Vision data is outdated or unavailable. */
    STALE("Path information is stale.")
}

/**
 * Object relevance relative to the user's forward walking corridor.
 */
enum class ObjectRelevance {
    /** Object bounding box directly occupies the forward walking corridor. */
    DIRECTLY_IN_CORRIDOR,

    /** Object is within the immediate margin/buffer flanking the walking corridor. */
    NEAR_CORRIDOR,

    /** Object is safely positioned outside the walking path (e.g. sidewalk edge, parked car). */
    OUTSIDE_CORRIDOR,

    /** Object distance, confidence, or position is uncertain. */
    UNCERTAIN
}

/**
 * Lateral region partition across the camera's horizontal field of view.
 */
enum class CorridorRegion {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Detailed analysis of an individual environmental object relative to the walking path.
 */
data class AnalyzedPathObject(
    val environmentObject: EnvironmentObject,
    val relevance: ObjectRelevance,
    val region: CorridorRegion,
    val corridorOverlapRatio: Float,
    val isPotentialObstruction: Boolean
)

/**
 * Comprehensive path analysis output produced by the WalkingPathAnalyzer.
 */
data class PathAnalysisResult(
    val status: PathStatus,
    val confidence: Float,
    val corridorObstructionRatio: Float,
    val analyzedObjects: List<AnalyzedPathObject> = emptyList(),
    val blockingObjects: List<AnalyzedPathObject> = emptyList(),
    val nearestObstacleDistance: DistanceCategory? = null,
    val summaryMessage: String = status.description,
    val timestampMs: Long = System.currentTimeMillis()
)

