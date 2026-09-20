package com.visionnav.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Performance profiles for vision inference to balance thermal throttling and battery consumption.
 */
enum class VisionPerformanceMode(val targetFps: Int, val frameIntervalMs: Long) {
    NORMAL(targetFps = 10, frameIntervalMs = 100L),
    LOW_BATTERY(targetFps = 5, frameIntervalMs = 200L),
    THERMAL_LIMIT(targetFps = 2, frameIntervalMs = 500L),
    CAMERA_IDLE(targetFps = 0, frameIntervalMs = Long.MAX_VALUE)
}

/**
 * CameraX frame analyzer with latest-frame strategy, dynamic frame dropping,
 * configurable inference rate, and low-light quality monitoring.
 */
class CameraFrameAnalyzer(
    var performanceMode: VisionPerformanceMode = VisionPerformanceMode.NORMAL,
    val lowLightLumaThreshold: Double = 25.0,
    private val onFrameAvailable: (frameBytes: ByteArray, width: Int, height: Int, rotation: Int, isLowLight: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)
    private var lastProcessedTimestampMs = 0L
    private var droppedFramesCount = 0L

    var isLowLightDetected: Boolean = false
        private set

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()

        // 1. Check if camera is in IDLE mode
        if (performanceMode == VisionPerformanceMode.CAMERA_IDLE) {
            image.close()
            return
        }

        // 2. Throttle inference rate according to the active performance mode
        if (now - lastProcessedTimestampMs < performanceMode.frameIntervalMs) {
            droppedFramesCount++
            image.close()
            return
        }

        // 3. Drop old frame if previous inference is still actively computing
        if (!isProcessing.compareAndSet(false, true)) {
            droppedFramesCount++
            image.close()
            return
        }

        try {
            lastProcessedTimestampMs = now

            // 4. Extract Y plane (luminance) for low-light assessment and buffer extraction
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val yBuffer = planes[0].buffer
                val yBytes = ByteArray(yBuffer.remaining())
                yBuffer.get(yBytes)

                // Compute average luminance across a sample of pixels
                var lumaSum = 0L
                val sampleStep = 16 // Sample every 16th byte for fast evaluation
                var samples = 0
                for (i in yBytes.indices step sampleStep) {
                    lumaSum += (yBytes[i].toInt() and 0xFF)
                    samples++
                }
                val avgLuma = if (samples > 0) lumaSum.toDouble() / samples else 128.0
                isLowLightDetected = avgLuma < lowLightLumaThreshold

                onFrameAvailable(
                    yBytes,
                    image.width,
                    image.height,
                    image.imageInfo.rotationDegrees,
                    isLowLightDetected
                )
            }
        } catch (e: Exception) {
            // Analyzer error must not crash the app
        } finally {
            image.close()
            isProcessing.set(false)
        }
    }

    fun resetStats() {
        droppedFramesCount = 0L
        lastProcessedTimestampMs = 0L
    }
}

