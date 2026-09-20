package com.visionnav.environment

import com.visionnav.vision.DepthEstimator
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionEstimator
import com.visionnav.vision.TrackedObject
import com.visionnav.vision.WalkingCorridorAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Core engine transforming real-time vision tracking streams into structured environmental snapshots.
 *
 * Consumes existing Phase 5 tracking data, evaluates spatial depth, motion, and corridor relations,
 * filters out invalid detections, deduplicates overlapping objects, and maintains an immutable,
 * thread-safe EnvironmentSnapshot.
 *
 * Does NOT directly invoke TTS or modify safety risk logic.
 */
class EnvironmentUnderstandingEngine(
    val depthEstimator: DepthEstimator = DepthEstimator(),
    val motionEstimator: MotionEstimator = MotionEstimator(),
    val corridorAnalyzer: WalkingCorridorAnalyzer = WalkingCorridorAnalyzer(),
    val freshnessTimeoutMs: Long = 3000L,
    val minConfidenceThreshold: Float = 0.40f,
    val duplicateIouThreshold: Float = 0.70f
) {
    private val _snapshot = MutableStateFlow(EnvironmentSnapshot.EMPTY)
    val snapshot: StateFlow<EnvironmentSnapshot> = _snapshot.asStateFlow()

    private var lastFrameTimestampMs = 0L

    /**
     * Evaluates active tracked objects from the vision pipeline for a new frame.
     *
     * @param trackedObjects List of persistent tracked objects from the ObjectTracker.
     * @param userSpeedMps Optional walking speed of the user in meters per second.
     * @param timestampMs Timestamp of the evaluated frame in milliseconds.
     * @return The updated EnvironmentSnapshot.
     */
    fun evaluateFrame(
        trackedObjects: List<TrackedObject>,
        userSpeedMps: Float? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): EnvironmentSnapshot {
        lastFrameTimestampMs = timestampMs

        // 1. Filter out invalid or low-confidence objects
        val validTracked = trackedObjects.filter { obj ->
            obj.currentBox.isValid() && obj.currentConfidence >= minConfidenceThreshold
        }

        // 2. Spatial deduplication: If multiple tracks overlap heavily, keep higher confidence
        val deduplicated = deduplicateObjects(validTracked)

        // 3. Transform into EnvironmentObjects with depth, motion, and corridor analysis
        val envObjects = deduplicated.map { obj ->
            val corridor = corridorAnalyzer.evaluate(obj.currentBox)
            val depth = depthEstimator.estimate(obj)
            val motion = motionEstimator.estimate(obj, userSpeedMps)

            EnvironmentObject(
                objectId = obj.trackId,
                category = EnvironmentCategory.fromDetectionClass(obj.detectionClass),
                confidence = obj.currentConfidence,
                boundingBox = obj.currentBox,
                distance = depth.category,
                motion = motion.state,
                corridorRelationship = corridor,
                timestampMs = timestampMs
            )
        }

        // 4. Partition into path-blocking obstacles vs. nearby useful objects
        val blockingObjects = envObjects.filter { it.isBlockingPath }
        val usefulObjects = envObjects.filter { !it.isBlockingPath }

        // 5. Determine nearest obstacle distance in the walking path
        val nearestDistance = blockingObjects
            .map { it.distance }
            .minByOrNull { it.ordinal }

        val pathInfo = PathInformation(
            isCorridorClear = blockingObjects.isEmpty(),
            nearestObstacleDistance = nearestDistance,
            blockingObjects = blockingObjects
        )

        // 6. Classify dominant scene category
        val dominantScene = determineDominantScene(envObjects, blockingObjects)

        // 7. Calculate overall confidence
        val overallConfidence = if (envObjects.isEmpty()) {
            1.0f
        } else {
            envObjects.map { it.confidence }.average().toFloat()
        }

        val newSnapshot = EnvironmentSnapshot(
            timestampMs = timestampMs,
            visibleObjects = envObjects,
            dominantScene = dominantScene,
            pathInformation = pathInfo,
            nearbyUsefulObjects = usefulObjects,
            overallConfidence = overallConfidence,
            isCameraActive = true,
            isStale = false
        )

        _snapshot.value = newSnapshot
        return newSnapshot
    }

    /**
     * Retrieves the latest snapshot, automatically marking it STALE if the freshness timeout has elapsed.
     */
    fun getLatestSnapshot(currentTimeMs: Long = System.currentTimeMillis()): EnvironmentSnapshot {
        val current = _snapshot.value
        val isStale = lastFrameTimestampMs == 0L || (currentTimeMs - lastFrameTimestampMs) > freshnessTimeoutMs

        return if (isStale) {
            current.copy(
                isStale = true,
                dominantScene = SceneCategory.UNKNOWN,
                pathInformation = current.pathInformation.copy(isCorridorClear = false)
            )
        } else {
            current
        }
    }

    /**
     * Resets the environmental snapshot to empty.
     */
    fun clear(timestampMs: Long = System.currentTimeMillis()) {
        lastFrameTimestampMs = timestampMs
        _snapshot.value = EnvironmentSnapshot.EMPTY.copy(timestampMs = timestampMs)
    }

    private fun deduplicateObjects(objects: List<TrackedObject>): List<TrackedObject> {
        val sorted = objects.sortedByDescending { it.currentConfidence }
        val kept = mutableListOf<TrackedObject>()

        for (obj in sorted) {
            val hasDuplicate = kept.any { existing ->
                existing.currentBox.iou(obj.currentBox) >= duplicateIouThreshold
            }
            if (!hasDuplicate) {
                kept.add(obj)
            }
        }
        return kept
    }

    private fun determineDominantScene(
        allObjects: List<EnvironmentObject>,
        blockingObjects: List<EnvironmentObject>
    ): SceneCategory {
        if (allObjects.isEmpty()) {
            return SceneCategory.CLEAR_PATH
        }

        if (blockingObjects.isNotEmpty()) {
            return SceneCategory.OBSTRUCTED_PATH
        }

        val hasVehicles = allObjects.any { it.isVehicle }
        val hasPedestrians = allObjects.any { it.isPedestrian }

        return when {
            hasVehicles && hasPedestrians -> SceneCategory.MIXED_TRAFFIC
            hasVehicles -> SceneCategory.VEHICULAR_ROADWAY
            hasPedestrians -> SceneCategory.PEDESTRIAN_AREA
            else -> SceneCategory.CLEAR_PATH
        }
    }
}

