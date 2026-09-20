package com.visionnav.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Classification of an object's spatial overlap with the user's projected walking corridor.
 */
enum class CorridorOverlap {
    /** Object bounding box directly intersects the active walking corridor. */
    INSIDE_CORRIDOR,

    /** Object is within the immediate buffer zone flanking the walking corridor. */
    NEAR_CORRIDOR,

    /** Object is well to the left or right of the user's path. */
    OUTSIDE_CORRIDOR;

    val isHazardousPath: Boolean
        get() = this == INSIDE_CORRIDOR || this == NEAR_CORRIDOR
}

/**
 * Probabilistic walking corridor analyzer.
 *
 * Models a perspective-aware trapezoidal or rectangular pedestrian corridor.
 * Objects outside this corridor (e.g. cars parked safely at the curb) produce lower risk,
 * whereas objects occupying or approaching the central corridor produce elevated risk.
 */
class WalkingCorridorAnalyzer(
    val corridorLeftTop: Float = 0.35f,
    val corridorRightTop: Float = 0.65f,
    val corridorLeftBottom: Float = 0.25f,
    val corridorRightBottom: Float = 0.75f,
    val nearBufferWidth: Float = 0.12f
) {
    /**
     * Evaluates where a bounding box lies relative to the walking corridor.
     */
    fun evaluate(box: BoundingBox): CorridorOverlap {
        if (!box.isValid()) return CorridorOverlap.OUTSIDE_CORRIDOR

        // Calculate corridor bounds at the object's bottom Y (where it touches the ground)
        val groundY = box.bottom.coerceIn(0f, 1f)

        // Linear interpolation between top and bottom corridor boundaries
        val corridorLeft = corridorLeftTop + (corridorLeftBottom - corridorLeftTop) * groundY
        val corridorRight = corridorRightTop + (corridorRightBottom - corridorRightTop) * groundY

        val bufferLeft = max(0f, corridorLeft - nearBufferWidth)
        val bufferRight = min(1f, corridorRight + nearBufferWidth)

        // Check horizontal intersection
        val interLeft = max(box.left, corridorLeft)
        val interRight = min(box.right, corridorRight)
        val hasCorridorOverlap = interLeft < interRight

        if (hasCorridorOverlap) {
            return CorridorOverlap.INSIDE_CORRIDOR
        }

        // Check buffer intersection
        val bufferInterLeft = max(box.left, bufferLeft)
        val bufferInterRight = min(box.right, bufferRight)
        val hasBufferOverlap = bufferInterLeft < bufferInterRight

        return if (hasBufferOverlap) {
            CorridorOverlap.NEAR_CORRIDOR
        } else {
            CorridorOverlap.OUTSIDE_CORRIDOR
        }
    }
}

