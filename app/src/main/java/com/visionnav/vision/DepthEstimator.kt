package com.visionnav.vision

/**
 * Coarse distance categorization for pedestrian safety.
 *
 * NOTE: Monocular camera distance estimation is inherently approximate.
 * This class NEVER provides false centimeter-level precision and returns [UNKNOWN]
 * when confidence is insufficient.
 */
enum class DistanceCategory(val label: String, val description: String) {
    VERY_NEAR("Very Near", "< 2 meters"),
    NEAR("Near", "2 - 5 meters"),
    MEDIUM("Medium", "5 - 10 meters"),
    FAR("Far", "> 10 meters"),
    UNKNOWN("Unknown", "Distance cannot be reliably determined");

    val isHazardous: Boolean
        get() = this == VERY_NEAR || this == NEAR
}

/**
 * Coarse depth estimation result.
 */
data class DepthEstimate(
    val category: DistanceCategory,
    val estimatedDistanceMeters: Float? = null,
    val confidence: Float = 0.8f
)

/**
 * Estimates approximate distance category based on object geometry, class priors,
 * and perspective projection heuristics.
 */
class DepthEstimator(
    val minReliableBoxHeight: Float = 0.03f,
    val minReliableConfidence: Float = 0.40f
) {
    /**
     * Estimates distance category for a tracked object.
     */
    fun estimate(trackedObject: TrackedObject): DepthEstimate {
        val box = trackedObject.currentBox

        // 1. If detection is too weak or box dimensions are corrupted -> UNKNOWN
        if (trackedObject.currentConfidence < minReliableConfidence ||
            box.height < minReliableBoxHeight ||
            !box.isValid()
        ) {
            return DepthEstimate(
                category = DistanceCategory.UNKNOWN,
                estimatedDistanceMeters = null,
                confidence = 0f
            )
        }

        val boxHeight = box.height
        val boxWidth = box.width

        // 2. Class-specific perspective projection heuristics
        return when (trackedObject.detectionClass) {
            DetectionClass.PERSON -> {
                when {
                    boxHeight >= 0.65f -> DepthEstimate(DistanceCategory.VERY_NEAR, 1.5f, 0.85f)
                    boxHeight >= 0.35f -> DepthEstimate(DistanceCategory.NEAR, 3.5f, 0.80f)
                    boxHeight >= 0.15f -> DepthEstimate(DistanceCategory.MEDIUM, 7.5f, 0.75f)
                    boxHeight >= 0.04f -> DepthEstimate(DistanceCategory.FAR, 15.0f, 0.70f)
                    else -> DepthEstimate(DistanceCategory.UNKNOWN, null, 0.3f)
                }
            }

            DetectionClass.CAR, DetectionClass.BUS, DetectionClass.TRUCK -> {
                when {
                    boxHeight >= 0.50f || boxWidth >= 0.60f -> DepthEstimate(DistanceCategory.VERY_NEAR, 2.0f, 0.90f)
                    boxHeight >= 0.25f || boxWidth >= 0.35f -> DepthEstimate(DistanceCategory.NEAR, 5.0f, 0.85f)
                    boxHeight >= 0.10f || boxWidth >= 0.15f -> DepthEstimate(DistanceCategory.MEDIUM, 10.0f, 0.80f)
                    boxHeight >= 0.04f -> DepthEstimate(DistanceCategory.FAR, 20.0f, 0.75f)
                    else -> DepthEstimate(DistanceCategory.UNKNOWN, null, 0.4f)
                }
            }

            DetectionClass.BICYCLE, DetectionClass.MOTORCYCLE -> {
                when {
                    boxHeight >= 0.55f || boxWidth >= 0.45f -> DepthEstimate(DistanceCategory.VERY_NEAR, 1.8f, 0.85f)
                    boxHeight >= 0.30f || boxWidth >= 0.25f -> DepthEstimate(DistanceCategory.NEAR, 4.0f, 0.80f)
                    boxHeight >= 0.12f -> DepthEstimate(DistanceCategory.MEDIUM, 8.0f, 0.75f)
                    boxHeight >= 0.04f -> DepthEstimate(DistanceCategory.FAR, 15.0f, 0.70f)
                    else -> DepthEstimate(DistanceCategory.UNKNOWN, null, 0.3f)
                }
            }
        }
    }
}

