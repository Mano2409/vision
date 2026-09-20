package com.visionnav.vision

/**
 * Runtime execution engines supported for local on-device YOLO inference.
 */
enum class YoloRuntime {
    TFLITE,
    ONNX,
    MOCK_SIMULATOR
}

/**
 * Configuration and metadata specification for local YOLO model inference.
 *
 * Supported model families:
 * - YOLOv8n / YOLOv11n (Float32 or INT8 quantized)
 * - Input size: typically 640x640x3 or 320x320x3 normalized RGB [0.0, 1.0]
 * - Output format: [1, 84, 8400] (4 box coordinates + 80 class probabilities) or post-NMS tensors
 *
 * Supported classes for pedestrian safety:
 * - person, bicycle, motorcycle, car, bus, truck
 */
data class YoloModelConfig(
    val modelName: String = "yolov8n_pedestrian.tflite",
    val runtime: YoloRuntime = YoloRuntime.MOCK_SIMULATOR,
    val inputWidth: Int = 640,
    val inputHeight: Int = 640,
    val confidenceThreshold: Float = 0.45f,
    val iouThreshold: Float = 0.45f,
    val supportedClasses: Set<DetectionClass> = setOf(
        DetectionClass.PERSON,
        DetectionClass.BICYCLE,
        DetectionClass.MOTORCYCLE,
        DetectionClass.CAR,
        DetectionClass.BUS,
        DetectionClass.TRUCK
    ),
    val numThreads: Int = 4,
    val useGpu: Boolean = false,
    val useNnapi: Boolean = false
)

