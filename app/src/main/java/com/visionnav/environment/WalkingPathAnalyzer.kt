package com.visionnav.environment

import com.visionnav.vision.CorridorOverlap
import com.visionnav.vision.DistanceCategory
import com.visionnav.vision.MotionState
import com.visionnav.vision.WalkingCorridorAnalyzer
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced obstacle and walking corridor path analyzer for Phase 6B.
 *
 * Evaluates whether visible environment objects are relevant to the user's forward
 * walking corridor, determines path status ([PathStatus.OPEN], [PathStatus.PARTIALLY_BLOCKED],
 * [PathStatus.BLOCKED], [PathStatus.UNKNOWN], [PathStatus.STALE]), and applies temporal
 * consistency smoothing and movement compensation.
 *
 * This layer is informational and does NOT replace or modify Phase 5's emergency SafetyRiskEngine.
 */
class WalkingPathAnalyzer(
    val corridorAnalyzer: WalkingCorridorAnalyzer = WalkingCorridorAnalyzer(),
    val persistenceRequired: Int = 2,
    val blockedThresholdRatio: Float = 0.45f,
    val partiallyBlockedThresholdRatio: Float = 0.15f
) {
    private var lastConfirmedStatus: PathStatus = PathStatus.UNKNOWN
    private var candidateStatus: PathStatus = PathStatus.UNKNOWN
    private var candidateCount: Int = 0

    /**
     * Analyzes an EnvironmentSnapshot and returns the current forward PathAnalysisResult.
     *
     * @param snapshot The current environment snapshot produced by Phase 6A.
     * @param userSpeedMps Optional walking speed of the user in meters per second.
     * @param headingDeltaDegrees Optional change in heading (degrees) to compensate for camera turns.
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return PathAnalysisResult with path status, obstruction ratio, and analyzed objects.
     */
    fun analyze(
        snapshot: EnvironmentSnapshot,
        userSpeedMps: Float? = null,
        headingDeltaDegrees: Float? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): PathAnalysisResult {
        // 1. Check for stale snapshot
        if (snapshot.isStale || !snapshot.isFresh(currentTimeMs)) {
            lastConfirmedStatus = PathStatus.STALE
            candidateStatus = PathStatus.STALE
            candidateCount = 0
            return PathAnalysisResult(
                status = PathStatus.STALE,
                confidence = 0f,
                corridorObstructionRatio = 0f,
                summaryMessage = PathStatus.STALE.description,
                timestampMs = currentTimeMs
            )
        }

        // 2. Camera movement compensation:
        // When user turns right (+deg), forward path in the camera frame shifts left (-X)
        val lateralShift = if (headingDeltaDegrees != null) {
            -(headingDeltaDegrees / 60f).coerceIn(-0.25f, 0.25f)
        } else {
            0f
        }

        // 3. Analyze each visible object
        val analyzedObjects = snapshot.visibleObjects.map { obj ->
            analyzeObject(obj, lateralShift, userSpeedMps)
        }

        val blocking = analyzedObjects.filter { it.isPotentialObstruction }

        // 4. Calculate total corridor obstruction ratio
        val corridorLeft = (0.35f + lateralShift).coerceIn(0.05f, 0.45f)
        val corridorRight = (0.65f + lateralShift).coerceIn(0.55f, 0.95f)
        val corridorWidth = max(0.01f, corridorRight - corridorLeft)

        var totalOverlapWidth = 0f
        for (obj in blocking) {
            val box = obj.environmentObject.boundingBox
            val interLeft = max(box.left, corridorLeft)
            val interRight = min(box.right, corridorRight)
            if (interLeft < interRight) {
                totalOverlapWidth += (interRight - interLeft)
            }
        }
        val obstructionRatio = (totalOverlapWidth / corridorWidth).coerceIn(0f, 1f)

        // 5. Determine raw candidate status for current frame
        val rawStatus = determineRawStatus(blocking, obstructionRatio, analyzedObjects)

        // 6. Apply temporal consistency filtering
        val hasImmediateObstruction = blocking.any {
            it.environmentObject.distance == DistanceCategory.VERY_NEAR &&
                    it.relevance == ObjectRelevance.DIRECTLY_IN_CORRIDOR
        }
        val smoothedStatus = applyTemporalConsistency(rawStatus, hasImmediateObstruction)

        // 7. Determine nearest obstacle distance among blocking objects
        val nearestDistance = blocking
            .map { it.environmentObject.distance }
            .minByOrNull { it.ordinal }

        // 8. Aggregate confidence
        val confidence = if (analyzedObjects.isEmpty()) {
            snapshot.overallConfidence
        } else {
            analyzedObjects.map { it.environmentObject.confidence }.average().toFloat()
        }

        return PathAnalysisResult(
            status = smoothedStatus,
            confidence = confidence,
            corridorObstructionRatio = obstructionRatio,
            analyzedObjects = analyzedObjects,
            blockingObjects = blocking,
            nearestObstacleDistance = nearestDistance,
            summaryMessage = smoothedStatus.description,
            timestampMs = snapshot.timestampMs
        )
    }

    private fun analyzeObject(
        obj: EnvironmentObject,
        lateralShift: Float,
        userSpeedMps: Float?
    ): AnalyzedPathObject {
        val box = obj.boundingBox
        val shiftedCenterX = box.centerX - lateralShift

        // Determine regional partition
        val region = when {
            shiftedCenterX < 0.35f -> CorridorRegion.LEFT
            shiftedCenterX > 0.65f -> CorridorRegion.RIGHT
            else -> CorridorRegion.CENTER
        }

        // Determine relevance
        val relevance = when {
            obj.distance == DistanceCategory.UNKNOWN || obj.confidence < 0.50f -> {
                ObjectRelevance.UNCERTAIN
            }
            obj.corridorRelationship == CorridorOverlap.INSIDE_CORRIDOR -> {
                ObjectRelevance.DIRECTLY_IN_CORRIDOR
            }
            obj.corridorRelationship == CorridorOverlap.NEAR_CORRIDOR -> {
                ObjectRelevance.NEAR_CORRIDOR
            }
            else -> {
                ObjectRelevance.OUTSIDE_CORRIDOR
            }
        }

        // Calculate corridor overlap ratio
        val corridorLeft = (0.35f + lateralShift).coerceIn(0.05f, 0.45f)
        val corridorRight = (0.65f + lateralShift).coerceIn(0.55f, 0.95f)
        val corridorWidth = max(0.01f, corridorRight - corridorLeft)

        val interLeft = max(box.left, corridorLeft)
        val interRight = min(box.right, corridorRight)
        val overlapRatio = if (interLeft < interRight) {
            ((interRight - interLeft) / corridorWidth).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Determine whether this object is a potential obstruction
        val isPotentialObstruction = when (relevance) {
            ObjectRelevance.DIRECTLY_IN_CORRIDOR -> {
                when (obj.distance) {
                    DistanceCategory.VERY_NEAR, DistanceCategory.NEAR -> true
                    DistanceCategory.MEDIUM -> obj.isApproaching || (userSpeedMps != null && userSpeedMps > 1.0f)
                    DistanceCategory.FAR -> false
                    DistanceCategory.UNKNOWN -> true
                }
            }
            ObjectRelevance.NEAR_CORRIDOR -> {
                (obj.isApproaching || obj.isCrossing) &&
                        (obj.distance == DistanceCategory.VERY_NEAR || obj.distance == DistanceCategory.NEAR)
            }
            ObjectRelevance.UNCERTAIN -> {
                obj.isInCorridor && obj.distance == DistanceCategory.UNKNOWN
            }
            ObjectRelevance.OUTSIDE_CORRIDOR -> false
        }

        return AnalyzedPathObject(
            environmentObject = obj,
            relevance = relevance,
            region = region,
            corridorOverlapRatio = overlapRatio,
            isPotentialObstruction = isPotentialObstruction
        )
    }

    private fun determineRawStatus(
        blocking: List<AnalyzedPathObject>,
        obstructionRatio: Float,
        allAnalyzed: List<AnalyzedPathObject>
    ): PathStatus {
        // If an object directly in corridor has uncertain distance or low confidence
        if (allAnalyzed.any { it.relevance == ObjectRelevance.UNCERTAIN && it.environmentObject.isInCorridor }) {
            return PathStatus.UNKNOWN
        }

        return when {
            obstructionRatio >= blockedThresholdRatio ||
                    blocking.any { it.environmentObject.distance == DistanceCategory.VERY_NEAR && it.relevance == ObjectRelevance.DIRECTLY_IN_CORRIDOR } -> {
                PathStatus.BLOCKED
            }
            obstructionRatio >= partiallyBlockedThresholdRatio || blocking.isNotEmpty() -> {
                PathStatus.PARTIALLY_BLOCKED
            }
            else -> {
                PathStatus.OPEN
            }
        }
    }

    private fun applyTemporalConsistency(
        rawStatus: PathStatus,
        hasImmediateObstruction: Boolean
    ): PathStatus {
        // Initial frame sets baseline directly
        if (lastConfirmedStatus == PathStatus.UNKNOWN && rawStatus != PathStatus.UNKNOWN) {
            lastConfirmedStatus = rawStatus
            candidateStatus = rawStatus
            candidateCount = 1
            return lastConfirmedStatus
        }

        if (rawStatus == candidateStatus) {
            candidateCount++
        } else {
            candidateStatus = rawStatus
            candidateCount = 1
        }

        // Immediate transition for critical very-near blockages; otherwise require persistence
        if (candidateCount >= persistenceRequired || hasImmediateObstruction) {
            lastConfirmedStatus = candidateStatus
        }

        return lastConfirmedStatus
    }

    /**
     * Resets internal temporal smoothing history.
     */
    fun reset() {
        lastConfirmedStatus = PathStatus.UNKNOWN
        candidateStatus = PathStatus.UNKNOWN
        candidateCount = 0
    }
}

