package com.visionnav.vision

import kotlin.math.sqrt

/**
 * Representation of an object tracked across multiple consecutive frames.
 */
data class TrackedObject(
    val trackId: Int,
    val detectionClass: DetectionClass,
    var currentBox: BoundingBox,
    var currentConfidence: Float,
    val firstSeenMs: Long,
    var lastSeenMs: Long,
    var totalDetections: Int,
    val boxHistory: MutableList<Pair<Long, BoundingBox>> = mutableListOf(),
    val confidenceHistory: MutableList<Float> = mutableListOf()
) {
    fun ageMs(nowMs: Long = System.currentTimeMillis()): Long = nowMs - firstSeenMs
    fun timeSinceLastSeenMs(nowMs: Long = System.currentTimeMillis()): Long = nowMs - lastSeenMs

    /**
     * Calculates lateral (X) and longitudinal (height/scale) rate of change per second over the given time window.
     */
    fun calculateVelocity(timeWindowMs: Long = 1000L): Pair<Float, Float> {
        if (boxHistory.size < 2) return Pair(0f, 0f)
        val cutoff = lastSeenMs - timeWindowMs
        val recent = boxHistory.filter { it.first >= cutoff }
        if (recent.size < 2) return Pair(0f, 0f)

        val oldest = recent.first()
        val newest = recent.last()
        val dtSec = (newest.first - oldest.first) / 1000f
        if (dtSec <= 0f) return Pair(0f, 0f)

        val dxPerSec = (newest.second.centerX - oldest.second.centerX) / dtSec
        val dHeightPerSec = (newest.second.height - oldest.second.height) / dtSec
        return Pair(dxPerSec, dHeightPerSec)
    }
}

/**
 * Multi-object tracker using greedy IoU and centroid-distance association.
 *
 * Provides persistent IDs across frames, trajectory histories, and track lifecycle management.
 */
class ObjectTracker(
    val iouThreshold: Float = 0.25f,
    val maxCentroidDistance: Float = 0.20f,
    val trackTimeoutMs: Long = 1000L,
    val maxHistoryPerTrack: Int = 30
) {
    private var nextTrackId = 1
    private val activeTracks = mutableListOf<TrackedObject>()

    /**
     * Updates active tracks with a new list of detections from the current frame.
     *
     * @param detections List of stable detections from the current frame
     * @param timestampMs Frame timestamp in milliseconds
     * @return List of currently active tracked objects
     */
    fun update(
        detections: List<Detection>,
        timestampMs: Long = System.currentTimeMillis()
    ): List<TrackedObject> {
        val unmatchedDetections = detections.toMutableList()
        val matchedTracks = mutableSetOf<Int>()

        // 1. Associate existing tracks with detections by highest IoU
        for (track in activeTracks) {
            var bestDetection: Detection? = null
            var bestScore = 0f

            for (det in unmatchedDetections) {
                if (det.detectionClass != track.detectionClass) continue

                val iou = track.currentBox.iou(det.boundingBox)
                val dist = centroidDistance(track.currentBox, det.boundingBox)

                // Score favoring high IoU or close centroid
                val score = if (iou > 0f) iou else (1f - dist).coerceAtLeast(0f)

                if (iou >= iouThreshold || dist <= maxCentroidDistance) {
                    if (score > bestScore) {
                        bestScore = score
                        bestDetection = det
                    }
                }
            }

            if (bestDetection != null) {
                // Update track with matched detection
                track.currentBox = bestDetection.boundingBox
                track.currentConfidence = bestDetection.confidence
                track.lastSeenMs = timestampMs
                track.totalDetections++
                track.boxHistory.add(Pair(timestampMs, bestDetection.boundingBox))
                track.confidenceHistory.add(bestDetection.confidence)

                if (track.boxHistory.size > maxHistoryPerTrack) {
                    track.boxHistory.removeAt(0)
                }
                if (track.confidenceHistory.size > maxHistoryPerTrack) {
                    track.confidenceHistory.removeAt(0)
                }

                matchedTracks.add(track.trackId)
                unmatchedDetections.remove(bestDetection)
            }
        }

        // 2. Create new tracks for unmatched detections
        for (det in unmatchedDetections) {
            val newTrack = TrackedObject(
                trackId = nextTrackId++,
                detectionClass = det.detectionClass,
                currentBox = det.boundingBox,
                currentConfidence = det.confidence,
                firstSeenMs = timestampMs,
                lastSeenMs = timestampMs,
                totalDetections = 1,
                boxHistory = mutableListOf(Pair(timestampMs, det.boundingBox)),
                confidenceHistory = mutableListOf(det.confidence)
            )
            activeTracks.add(newTrack)
        }

        // 3. Remove stale tracks that have not been observed recently
        activeTracks.removeAll { (timestampMs - it.lastSeenMs) > trackTimeoutMs }

        return activeTracks.toList()
    }

    private fun centroidDistance(box1: BoundingBox, box2: BoundingBox): Float {
        val dx = box1.centerX - box2.centerX
        val dy = box1.centerY - box2.centerY
        return sqrt(dx * dx + dy * dy)
    }

    fun getActiveTracks(): List<TrackedObject> = activeTracks.toList()

    fun reset() {
        activeTracks.clear()
        nextTrackId = 1
    }
}

