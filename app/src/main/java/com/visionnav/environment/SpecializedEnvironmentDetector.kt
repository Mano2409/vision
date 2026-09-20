package com.visionnav.environment

import com.visionnav.audio.AudioMessage
import com.visionnav.audio.AudioPriority
import com.visionnav.audio.AudioQueueManager
import com.visionnav.vision.BoundingBox
import com.visionnav.vision.DistanceCategory

/**
 * Specialized environmental feature detector for doors, entrances, stairs, and crosswalks (Phase 6C).
 *
 * Evaluates candidate detections, computes screen-relative bearings with uncertainty,
 * provides entrance discovery queries ([findNearestEntrance]), and routes non-intrusive
 * contextual announcements through [AudioQueueManager] with strict anti-spam cooldowns.
 *
 * This layer does NOT replace or modify Phase 5's emergency SafetyRiskEngine.
 */
class SpecializedEnvironmentDetector(
    private val audioQueueManager: AudioQueueManager? = null,
    val minConfidenceThreshold: Float = 0.50f,
    val directionConfidenceThreshold: Float = 0.60f,
    val announcementCooldownMs: Long = 10000L,
    val freshnessTimeoutMs: Long = 3000L
) {
    private var nextId = 1
    private val activeObjects = mutableListOf<SpecializedEnvironmentObject>()
    private val lastAnnouncedTimestamps = mutableMapOf<SpecializedType, Long>()

    /**
     * Updates active candidates with new detector inputs for the current frame.
     *
     * @param candidates Raw candidate inputs from a specialized feature detector.
     * @param timestampMs Timestamp of the current frame in milliseconds.
     * @return List of validated and processed SpecializedEnvironmentObjects.
     */
    fun updateCandidates(
        candidates: List<SpecializedCandidate>,
        timestampMs: Long = System.currentTimeMillis()
    ): List<SpecializedEnvironmentObject> {
        // 1. Filter out invalid boxes and low-confidence candidates
        val validCandidates = candidates.filter {
            it.boundingBox.isValid() && it.confidence >= minConfidenceThreshold
        }

        // 2. Transform into SpecializedEnvironmentObjects
        val processed = validCandidates.map { candidate ->
            val direction = computeDirection(candidate.boundingBox, candidate.confidence)
            val distance = estimateCoarseDistance(candidate.boundingBox)

            SpecializedEnvironmentObject(
                id = nextId++,
                type = candidate.type,
                confidence = candidate.confidence,
                boundingBox = candidate.boundingBox,
                coarsePosition = direction,
                coarseDistance = distance,
                timestampMs = timestampMs
            )
        }

        activeObjects.clear()
        activeObjects.addAll(processed)

        // 3. Process contextual announcements with anti-spam cooldown
        processContextualAnnouncements(processed, timestampMs)

        return processed
    }

    /**
     * Searches for the nearest fresh door or entrance candidate.
     *
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return [EntranceSearchResult] detailing the nearest candidate, direction, distance, and description.
     */
    fun findNearestEntrance(currentTimeMs: Long = System.currentTimeMillis()): EntranceSearchResult {
        val freshEntrances = activeObjects.filter {
            it.isEntranceOrDoor && (currentTimeMs - it.timestampMs) <= freshnessTimeoutMs
        }

        if (freshEntrances.isEmpty()) {
            return EntranceSearchResult(
                candidate = null,
                direction = ScreenDirection.UNKNOWN,
                coarseDistance = DistanceCategory.UNKNOWN,
                confidence = 0f,
                isFresh = false,
                spokenDescription = "No entrance detected nearby."
            )
        }

        // Select nearest entrance (lowest distance ordinal, highest confidence)
        val nearest = freshEntrances.minWithOrNull(
            compareBy<SpecializedEnvironmentObject> { it.coarseDistance.ordinal }
                .thenByDescending { it.confidence }
        ) ?: freshEntrances.first()

        val description = buildEntranceDescription(nearest)

        return EntranceSearchResult(
            candidate = nearest,
            direction = nearest.coarsePosition,
            coarseDistance = nearest.coarseDistance,
            confidence = nearest.confidence,
            isFresh = true,
            spokenDescription = description
        )
    }

    /**
     * Retrieves all fresh specialized objects.
     */
    fun getFreshCandidates(currentTimeMs: Long = System.currentTimeMillis()): List<SpecializedEnvironmentObject> {
        return activeObjects.filter { (currentTimeMs - it.timestampMs) <= freshnessTimeoutMs }
    }

    private fun computeDirection(box: BoundingBox, confidence: Float): ScreenDirection {
        if (confidence < directionConfidenceThreshold) {
            return ScreenDirection.UNKNOWN
        }

        val centerX = box.centerX
        return when {
            centerX < 0.35f -> ScreenDirection.LEFT
            centerX > 0.65f -> ScreenDirection.RIGHT
            else -> ScreenDirection.CENTER
        }
    }

    private fun estimateCoarseDistance(box: BoundingBox): DistanceCategory {
        val h = box.height
        return when {
            h >= 0.60f -> DistanceCategory.VERY_NEAR
            h >= 0.35f -> DistanceCategory.NEAR
            h >= 0.18f -> DistanceCategory.MEDIUM
            h >= 0.05f -> DistanceCategory.FAR
            else -> DistanceCategory.UNKNOWN
        }
    }

    private fun buildEntranceDescription(obj: SpecializedEnvironmentObject): String {
        val noun = if (obj.type == SpecializedType.POSSIBLE_ENTRANCE || obj.confidence < 0.70f) {
            "Possible entrance"
        } else {
            "Entrance"
        }

        return when (obj.coarsePosition) {
            ScreenDirection.CENTER -> "$noun ahead."
            ScreenDirection.RIGHT -> "$noun slightly right."
            ScreenDirection.LEFT -> "$noun slightly left."
            ScreenDirection.UNKNOWN -> "$noun nearby."
        }
    }

    private fun processContextualAnnouncements(
        objects: List<SpecializedEnvironmentObject>,
        timestampMs: Long
    ) {
        if (audioQueueManager == null) return

        // Group by specialized type to announce each feature category at most once per cooldown
        val grouped = objects.groupBy { it.type }

        for ((type, items) in grouped) {
            val lastTime = lastAnnouncedTimestamps[type] ?: 0L
            if ((timestampMs - lastTime) >= announcementCooldownMs) {
                val best = items.maxByOrNull { it.confidence } ?: continue
                val message = buildAnnouncementMessage(best)

                if (message.isNotBlank()) {
                    audioQueueManager.post(
                        AudioMessage(
                            text = message,
                            priority = AudioPriority.SYSTEM_FEEDBACK
                        )
                    )
                    lastAnnouncedTimestamps[type] = timestampMs
                }
            }
        }
    }

    private fun buildAnnouncementMessage(obj: SpecializedEnvironmentObject): String {
        return when (obj.type) {
            SpecializedType.POSSIBLE_ENTRANCE -> {
                when (obj.coarsePosition) {
                    ScreenDirection.RIGHT -> "Possible entrance slightly right."
                    ScreenDirection.LEFT -> "Possible entrance slightly left."
                    else -> "Possible entrance ahead."
                }
            }
            SpecializedType.ENTRANCE, SpecializedType.DOOR -> {
                when (obj.coarsePosition) {
                    ScreenDirection.RIGHT -> "Entrance slightly right."
                    ScreenDirection.LEFT -> "Entrance slightly left."
                    else -> "Entrance ahead."
                }
            }
            SpecializedType.STAIRS_UP -> "Possible stairs going up ahead."
            SpecializedType.STAIRS_DOWN -> "Possible stairs going down ahead."
            SpecializedType.STAIRS_UNKNOWN -> "Possible stairs ahead."
            SpecializedType.POSSIBLE_CROSSWALK -> "Possible crosswalk ahead."
        }
    }

    /**
     * Resets active objects and cooldown history.
     */
    fun clear() {
        activeObjects.clear()
        lastAnnouncedTimestamps.clear()
    }
}

