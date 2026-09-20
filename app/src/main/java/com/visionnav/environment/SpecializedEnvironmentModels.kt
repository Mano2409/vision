package com.visionnav.environment

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DistanceCategory

/**
 * Supported specialized environmental object categories.
 */
enum class SpecializedType(val label: String) {
    DOOR("door"),
    ENTRANCE("entrance"),
    POSSIBLE_ENTRANCE("possible entrance"),
    STAIRS_UP("stairs going up"),
    STAIRS_DOWN("stairs going down"),
    STAIRS_UNKNOWN("possible stairs"),
    POSSIBLE_CROSSWALK("possible crosswalk");

    val isEntranceOrDoor: Boolean
        get() = this == DOOR || this == ENTRANCE || this == POSSIBLE_ENTRANCE

    val isStairs: Boolean
        get() = this == STAIRS_UP || this == STAIRS_DOWN || this == STAIRS_UNKNOWN

    val isCrosswalk: Boolean
        get() = this == POSSIBLE_CROSSWALK
}

/**
 * Screen-relative directional bearing for detected environmental features.
 */
enum class ScreenDirection(val spokenLabel: String) {
    LEFT("left"),
    CENTER("ahead"),
    RIGHT("right"),
    UNKNOWN("direction uncertain")
}

/**
 * Raw candidate detection input from a specialized feature detector.
 */
data class SpecializedCandidate(
    val type: SpecializedType,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Fully evaluated specialized environmental entity.
 */
data class SpecializedEnvironmentObject(
    val id: Int,
    val type: SpecializedType,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val coarsePosition: ScreenDirection,
    val coarseDistance: DistanceCategory,
    val timestampMs: Long = System.currentTimeMillis(),
    val uncertainty: Float = (1.0f - confidence).coerceIn(0f, 1f)
) {
    val isEntranceOrDoor: Boolean get() = type.isEntranceOrDoor
    val isStairs: Boolean get() = type.isStairs
    val isCrosswalk: Boolean get() = type.isCrosswalk
}

/**
 * Result returned by entrance awareness queries such as [SpecializedEnvironmentDetector.findNearestEntrance].
 */
data class EntranceSearchResult(
    val candidate: SpecializedEnvironmentObject?,
    val direction: ScreenDirection,
    val coarseDistance: DistanceCategory,
    val confidence: Float,
    val isFresh: Boolean,
    val spokenDescription: String
)

