package com.visionnav.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Pedestrian-safety-focused object classes supported by the VisionNav vision engine.
 */
enum class DetectionClass(val label: String) {
    PERSON("person"),
    BICYCLE("bicycle"),
    MOTORCYCLE("motorcycle"),
    CAR("car"),
    BUS("bus"),
    TRUCK("truck");

    val isVehicle: Boolean
        get() = this == CAR || this == BUS || this == TRUCK || this == MOTORCYCLE || this == BICYCLE

    val isHeavyVehicle: Boolean
        get() = this == CAR || this == BUS || this == TRUCK

    companion object {
        fun fromLabel(label: String): DetectionClass? {
            val normalized = label.trim().lowercase()
            return entries.firstOrNull { it.label == normalized || it.name.lowercase() == normalized }
        }
    }
}

/**
 * Normalized 2D bounding box representing an object's location in a camera frame [0.0, 1.0].
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    val area: Float get() = width * height

    fun isValid(): Boolean {
        return left in 0f..1f && right in 0f..1f &&
                top in 0f..1f && bottom in 0f..1f &&
                left < right && top < bottom &&
                area > 0f
    }

    /**
     * Computes Intersection over Union (IoU) with another bounding box.
     */
    fun iou(other: BoundingBox): Float {
        val interLeft = max(left, other.left)
        val interTop = max(top, other.top)
        val interRight = min(right, other.right)
        val interBottom = min(bottom, other.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val intersectionArea = interWidth * interHeight

        if (intersectionArea <= 0f) return 0f

        val unionArea = area + other.area - intersectionArea
        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    /**
     * Checks if this bounding box overlaps a normalized horizontal corridor [startX, endX].
     */
    fun overlapsHorizontalRange(startX: Float, endX: Float): Boolean {
        return max(left, startX) < min(right, endX)
    }
}

/**
 * Raw detection instance produced by an object detector for a single frame.
 */
data class Detection(
    val detectionClass: DetectionClass,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val timestampMs: Long = System.currentTimeMillis(),
    val id: String = "det_${System.currentTimeMillis()}_${(1000..9999).random()}"
)
