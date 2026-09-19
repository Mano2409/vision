# Vision Subsystem (Phase 6 & 7)

This package contains the on-device ML inference and spatial analysis engines:
- `ObjectDetector.kt`: Stable detector interface allowing YOLO26n / LiteRT / NCNN replacement.
- `YoloDetector.kt`: LiteRT model inference implementation.
- `SpatialAnalyzer.kt`: Classifies bounding box centroids into Left (0-33%), Center (34-66%), or Right (67-100%).
- `ProximityEstimator.kt`: Computes coarse proximity categories (Far, Medium, Near, Critical).
- `ObjectTracker.kt`: Temporal smoothing to prevent flickering alerts.

