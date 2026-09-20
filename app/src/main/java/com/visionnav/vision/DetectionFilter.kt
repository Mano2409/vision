package com.visionnav.vision

/**
 * Filters raw object detections to eliminate noisy, low-confidence, or spurious single-frame detections.
 *
 * Implements temporal consistency:
 * - High-confidence detections (>= [highConfidenceImmediateThreshold]) pass immediately.
 * - Moderate-confidence detections (>= [minConfidenceThreshold]) must be observed across at least
 *   [requiredConsistentFrames] frames within [historyWindowMs] to be considered stable.
 * - Weak detections (< [minConfidenceThreshold]) are dropped immediately.
 */
class DetectionFilter(
    val minConfidenceThreshold: Float = 0.50f,
    val highConfidenceImmediateThreshold: Float = 0.85f,
    val requiredConsistentFrames: Int = 2,
    val minBoundingBoxArea: Float = 0.002f,
    val maxBoundingBoxArea: Float = 0.98f,
    val historyWindowMs: Long = 1000L,
    val iouMatchingThreshold: Float = 0.30f
) {
    private data class HistoricalDetection(
        val detectionClass: DetectionClass,
        val boundingBox: BoundingBox,
        val confidence: Float,
        val timestampMs: Long
    )

    private val recentHistory = mutableListOf<HistoricalDetection>()

    /**
     * Filters a list of raw detections from the current frame.
     *
     * @return List of temporally validated, stable detections.
     */
    fun filter(detections: List<Detection>, timestampMs: Long = System.currentTimeMillis()): List<Detection> {
        // 1. Prune expired detections from history
        recentHistory.removeAll { (timestampMs - it.timestampMs) > historyWindowMs }

        val stableDetections = mutableListOf<Detection>()

        for (detection in detections) {
            // Check bounding box validity and size limits
            if (!detection.boundingBox.isValid()) continue
            val area = detection.boundingBox.area
            if (area < minBoundingBoxArea || area > maxBoundingBoxArea) continue

            // Drop detections below the minimum confidence threshold
            if (detection.confidence < minConfidenceThreshold) continue

            // Record into history
            recentHistory.add(
                HistoricalDetection(
                    detectionClass = detection.detectionClass,
                    boundingBox = detection.boundingBox,
                    confidence = detection.confidence,
                    timestampMs = timestampMs
                )
            )

            // High-confidence detections pass immediately for emergency reactivity
            if (detection.confidence >= highConfidenceImmediateThreshold) {
                stableDetections.add(detection)
                continue
            }

            // Count matching detections in the history window
            val matchCount = recentHistory.count { hist ->
                hist.detectionClass == detection.detectionClass &&
                        hist.boundingBox.iou(detection.boundingBox) >= iouMatchingThreshold
            }

            if (matchCount >= requiredConsistentFrames) {
                stableDetections.add(detection)
            }
        }

        return stableDetections
    }

    fun reset() {
        recentHistory.clear()
    }
}

