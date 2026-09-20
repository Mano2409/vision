package com.visionnav.environment

import com.visionnav.vision.BoundingBox
import com.visionnav.vision.CorridorOverlap
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState

/**
 * Representation of a detected, tracked, and spatially analyzed entity in the pedestrian's environment.
 *
 * Combines classification, tracking ID, geometric boundary, estimated proximity,
 * motion vector, and walking corridor relationship into a single cohesive model.
 */
data class EnvironmentObject(
    val objectId: Int,
    val category: EnvironmentCategory,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val distance: DistanceCategory,
    val motion: MotionState? = null,
    val corridorRelationship: CorridorOverlap = CorridorOverlap.OUTSIDE_CORRIDOR,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Whether this object is physically inside the user's immediate walking corridor.
     */
    val isInCorridor: Boolean
        get() = corridorRelationship == CorridorOverlap.INSIDE_CORRIDOR

    /**
     * Whether this object is near or bordering the user's walking corridor.
     */
    val isNearCorridor: Boolean
        get() = corridorRelationship == CorridorOverlap.NEAR_CORRIDOR

    /**
     * Whether this object is moving toward the user.
     */
    val isApproaching: Boolean
        get() = motion == MotionState.APPROACHING

    /**
     * Whether this object is moving away from the user.
     */
    val isMovingAway: Boolean
        get() = motion == MotionState.MOVING_AWAY

    /**
     * Whether this object is crossing laterally in front of the user.
     */
    val isCrossing: Boolean
        get() = motion == MotionState.CROSSING

    /**
     * Whether this object represents a vehicle (car, bus, truck, motorcycle, bicycle).
     */
    val isVehicle: Boolean
        get() = category.isVehicle

    /**
     * Whether this object represents a pedestrian.
     */
    val isPedestrian: Boolean
        get() = category.isPedestrian

    /**
     * Whether this object poses an active spatial obstruction to the user's forward movement.
     */
    val isBlockingPath: Boolean
        get() = (isInCorridor && (distance == DistanceCategory.VERY_NEAR || distance == DistanceCategory.NEAR)) ||
                (isNearCorridor && isApproaching && distance == DistanceCategory.VERY_NEAR)
}

