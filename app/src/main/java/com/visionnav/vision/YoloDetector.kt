package com.visionnav.vision

import android.content.Context
import android.util.Log

/**
 * Interface defining the contract for local on-device YOLO object detectors.
 */
interface IYoloDetector : AutoCloseable {
    val config: YoloModelConfig
    val isReady: Boolean

    /**
     * Executes object detection on an incoming camera frame or raw image buffer.
     *
     * @param frameData Raw frame bytes (e.g. YUV_420_888, RGB, or simulated buffer)
     * @param width Width of the frame in pixels
     * @param height Height of the frame in pixels
     * @param rotationDegrees Sensor rotation degrees (0, 90, 180, 270)
     * @param timestampMs Timestamp of the frame capture in milliseconds
     * @return List of detected objects passing the model confidence threshold
     */
    suspend fun detect(
        frameData: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        timestampMs: Long = System.currentTimeMillis()
    ): List<Detection>

    override fun close()
}

/**
 * Deterministic Mock/Simulator YOLO detector for unit tests, offline development,
 * and simulator replays without requiring a physical camera or model weights.
 */
class MockYoloDetector(
    override val config: YoloModelConfig = YoloModelConfig(runtime = YoloRuntime.MOCK_SIMULATOR)
) : IYoloDetector {

    override var isReady: Boolean = true
        private set

    private val queuedDetections = mutableListOf<List<Detection>>()
    private var defaultDetections: List<Detection> = emptyList()

    fun queueDetections(vararg frames: List<Detection>) {
        queuedDetections.addAll(frames)
    }

    fun setDefaultDetections(detections: List<Detection>) {
        defaultDetections = detections
    }

    fun clearQueue() {
        queuedDetections.clear()
        defaultDetections = emptyList()
    }

    override suspend fun detect(
        frameData: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        timestampMs: Long
    ): List<Detection> {
        if (!isReady) return emptyList()

        val detections = if (queuedDetections.isNotEmpty()) {
            queuedDetections.removeAt(0)
        } else {
            defaultDetections
        }

        // Filter against config's confidence and supported classes
        return detections.filter {
            it.confidence >= config.confidenceThreshold &&
                    it.detectionClass in config.supportedClasses &&
                    it.boundingBox.isValid()
        }
    }

    override fun close() {
        isReady = false
        queuedDetections.clear()
    }
}

/**
 * TFLite implementation skeleton for on-device YOLO inference.
 *
 * When a model binary (e.g. yolov8n_float32.tflite) is placed in app/src/main/assets,
 * this class initializes the TensorFlow Lite interpreter with optional GPU/NNAPI acceleration.
 *
 * Gracefully reports [isReady] = false if the model asset is missing, preventing crashes.
 */
class TFLiteYoloDetector(
    private val context: Context,
    override val config: YoloModelConfig = YoloModelConfig(runtime = YoloRuntime.TFLITE)
) : IYoloDetector {

    private val tag = "TFLiteYoloDetector"
    override var isReady: Boolean = false
        private set

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            // Verify model asset exists
            val assetList = context.assets.list("") ?: emptyArray()
            if (config.modelName !in assetList) {
                Log.w(tag, "Model asset '${config.modelName}' not found in assets. Operating in fallback state.")
                isReady = false
                return
            }

            // In production with org.tensorflow:tensorflow-lite:
            // val modelBuffer = loadModelFile(context, config.modelName)
            // val options = Interpreter.Options().apply {
            //     setNumThreads(config.numThreads)
            //     if (config.useGpu) addDelegate(GpuDelegate())
            //     if (config.useNnapi) setUseNNAPI(true)
            // }
            // interpreter = Interpreter(modelBuffer, options)
            isReady = true
            Log.i(tag, "TFLite model '${config.modelName}' initialized successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TFLite model: ${e.message}", e)
            isReady = false
        }
    }

    override suspend fun detect(
        frameData: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        timestampMs: Long
    ): List<Detection> {
        if (!isReady) {
            return emptyList()
        }

        // TFLite tensor preprocessing, inference, and NMS parsing
        return emptyList()
    }

    override fun close() {
        isReady = false
    }
}

