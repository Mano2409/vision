package com.visionnav.vision

import com.visionnav.safety.SafetyLevel
import com.visionnav.safety.SafetyRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSafetyArchitectureTest {

    @Test
    fun testNoYoloComponentDirectlyInvokesTts() {
        // Verify via reflection that IYoloDetector and implementations have no TTS or AudioQueue dependencies
        val yoloClasses = listOf(
            IYoloDetector::class.java,
            MockYoloDetector::class.java,
            TFLiteYoloDetector::class.java,
            DetectionFilter::class.java,
            ObjectTracker::class.java
        )

        val forbiddenClassNames = listOf(
            "com.visionnav.audio.VoiceSpeaker",
            "android.speech.tts.TextToSpeech",
            "com.visionnav.audio.AudioQueueManager"
        )

        for (clazz in yoloClasses) {
            for (field in clazz.declaredFields) {
                for (forbidden in forbiddenClassNames) {
                    assertFalse(
                        "${clazz.simpleName} must not have a field of type $forbidden",
                        field.type.name.contains(forbidden)
                    )
                }
            }

            for (method in clazz.declaredMethods) {
                for (param in method.parameterTypes) {
                    for (forbidden in forbiddenClassNames) {
                        assertFalse(
                            "${clazz.simpleName}.${method.name} must not accept $forbidden",
                            param.name.contains(forbidden)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testOfflineVisionArchitectureRemainsIndependent() {
        // Vision safety pipeline runs locally without any network or geocoding dependencies
        val engine = SafetyRiskEngine()
        val box = BoundingBox(0.4f, 0.35f, 0.6f, 0.75f) // height = 0.40 -> NEAR
        val track = TrackedObject(
            trackId = 1,
            detectionClass = DetectionClass.PERSON,
            currentBox = box,
            currentConfidence = 0.9f,
            firstSeenMs = 1000L,
            lastSeenMs = 1000L,
            totalDetections = 2,
            boxHistory = mutableListOf(Pair(900L, box), Pair(1000L, box))
        )

        engine.evaluateFrame(listOf(track), userSpeedMps = 1.0f, timestampMs = 1000L)
        assertTrue(engine.safetyState.value.isVisionAvailable)
        assertEquals(SafetyLevel.CAUTION, engine.safetyState.value.overallLevel)
    }

    @Test
    fun testWeakDetectionsDoNotImmediatelyTriggerCriticalWarnings() {
        val filter = DetectionFilter(minConfidenceThreshold = 0.50f)
        val engine = SafetyRiskEngine()

        // Single weak detection (0.42)
        val weak = Detection(DetectionClass.CAR, 0.42f, BoundingBox(0.3f, 0.3f, 0.7f, 0.7f))
        val stable = filter.filter(listOf(weak), 1000L)

        // Must be filtered out
        assertTrue("Weak detection must be dropped", stable.isEmpty())

        // Safety engine receives empty list -> remains NONE
        engine.evaluateFrame(emptyList(), timestampMs = 1000L)
        assertEquals(SafetyLevel.NONE, engine.safetyState.value.overallLevel)
    }

    @Test
    fun testStaleWhatsAheadDataIsRejected() {
        val engine = SafetyRiskEngine()
        val box = BoundingBox(0.4f, 0.4f, 0.6f, 0.7f)
        val track = TrackedObject(
            trackId = 1,
            detectionClass = DetectionClass.PERSON,
            currentBox = box,
            currentConfidence = 0.9f,
            firstSeenMs = 1000L,
            lastSeenMs = 1000L,
            totalDetections = 2,
            boxHistory = mutableListOf(Pair(900L, box), Pair(1000L, box))
        )

        // Evaluate frame at t = 1000ms
        engine.evaluateFrame(listOf(track), timestampMs = 1000L)

        // At t = 2000ms (1s later), data is fresh (< 3s timeout)
        assertTrue("Data should be fresh within timeout", engine.isDataFresh(nowMs = 2000L, timeoutMs = 3000L))

        // At t = 5000ms (4s later), data is STALE (> 3s timeout)
        assertFalse("Stale data must be rejected", engine.isDataFresh(nowMs = 5000L, timeoutMs = 3000L))
    }
}
