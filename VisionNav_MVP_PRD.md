VisionNav
Product Requirements Document (PRD)
MVP v1.0  |  Android  |  Software-only prototype

## 1. Product Overview
VisionNav is a software-only Android prototype for blind and visually impaired users. It combines voice-first walking navigation with on-device computer vision to provide route guidance and timely obstacle warnings.
The MVP is an assistive prototype for controlled testing and demonstrations. It is not intended to replace a white cane, guide dog, or other established mobility aid, and it must not be treated as a safety-critical autonomous navigation system.
## 2. Problem Statement
Existing navigation apps can provide routes and turn-by-turn instructions, but route information alone does not describe immediate physical obstacles in the user's path. VisionNav aims to combine planned walking routes with local camera-based perception so the app can distinguish between a route instruction and an immediate obstacle that may interfere with the intended path.
## 3. MVP Goal
A user should be able to open the Android app, speak a destination, receive a walking route, follow voice instructions, and receive concise voice warnings when the camera detects a relevant obstacle.
## 4. Target User
- Primary: blind and visually impaired users participating in controlled prototype testing.
- Secondary: developers/researchers evaluating mobile computer vision and accessible navigation.
- Initial deployment: Android phone with camera, microphone, GPS, speaker/headphones, and USB debugging during development.
## 5. MVP Success Criteria
- A destination can be entered through speech.
- The destination is converted to coordinates.
- A pedestrian route is calculated through Valhalla using OpenStreetMap-based data.
- The app provides turn-by-turn voice instructions.
- YOLO26n runs locally on the Android device.
- Detected objects are classified as left, center, or right.
- The system provides coarse proximity categories rather than claiming precise distance.
- The decision engine suppresses repetitive detections and speaks only relevant events.
- The app can be installed and tested as an APK on a physical Android phone.
- Core vision inference continues without an internet connection.
## 6. Product Scope
### 6.1 Included in MVP
- Native Android application.
- Kotlin and Jetpack Compose UI.
- USB debugging and APK-based development.
- CameraX camera pipeline.
- YOLO26n object detection.
- On-device ML inference using a mobile runtime such as LiteRT or NCNN.
- Basic depth/proximity estimation, initially using a compatible depth model if device performance permits.
- Basic object tracking.
- GPS/location tracking.
- Speech-to-text using Android SpeechRecognizer.
- Text-to-speech using Android TextToSpeech.
- Destination geocoding through an OpenStreetMap-compatible service, subject to that service's usage policy.
- Valhalla pedestrian routing.
- Route-following state and rerouting behavior.
- Obstacle decision engine.
- Basic accessibility-focused voice interaction.
### 6.2 Explicitly Out of Scope
- Wearable hardware or smart glasses.
- Vibration motors or custom hardware.
- Cloud-based computer vision.
- LLM-based navigation decisions.
- User accounts and social features.
- Global offline map distribution.
- Autonomous road crossing or traffic judgment.
- Traffic prediction.
- Production-scale routing infrastructure.
- Custom YOLO training during the first implementation cycle.
- Claims of centimeter-level distance accuracy.
## 7. System Architecture
Android Phone
│
├── GPS ────────────────┐
│                       │
├── Microphone → STT ───┤
│                       │
├── Camera → YOLO26n ───┤
│               │       │
│             Depth     │
│               │       │
│            Tracking   │
│               │       │
└───────────────────────┤
                        ▼
                 Decision Engine
                  │           │
                  ▼           ▼
              Valhalla       TTS
              Routing      Voice Output
The vision layer, routing layer, voice layer, and decision engine must remain modular. The detector interface should allow YOLO26n to be replaced later without rewriting navigation logic.
## 8. Technology Requirements
## 9. Functional Requirements
### FR-01: Voice Destination Input
- The app shall prompt the user for a destination.
- The app shall convert spoken input into text.
- The app shall identify a navigation destination from the recognized text.
- The app shall ask the user to repeat or clarify when the destination cannot be resolved.
### FR-02: Location
- The app shall obtain current latitude and longitude.
- The app shall expose location accuracy to the navigation engine.
- The app shall track movement and heading when available.
- The app shall detect unavailable or poor-quality location data.
### FR-03: Geocoding
- The app shall convert a recognized place name into coordinates.
- The app shall handle multiple candidate locations.
- The app shall cache appropriate geocoding results where permitted.
### FR-04: Routing
- The app shall request pedestrian routes from Valhalla.
- The app shall receive route geometry and maneuver instructions.
- The app shall track progress along the route.
- The app shall support route recalculation after significant deviation.
- The app shall announce important upcoming maneuvers.
### FR-05: Camera
- The app shall request camera permission.
- The app shall display a camera preview during vision mode.
- The app shall provide frames to the inference pipeline at a controlled rate.
- The app shall handle camera rotation and lifecycle changes.
### FR-06: Object Detection
- YOLO26n shall run locally on the Android device.
- The detector shall return class, confidence, and bounding-box information.
- The detector shall expose a stable interface so the model can be replaced later.
- The application shall record inference latency and effective FPS in debug mode.
### FR-07: Spatial and Proximity Analysis
- Detected objects shall be categorized as left, center, or right.
- The system shall assign coarse proximity categories such as far, medium, near, and critical.
- Depth estimates shall be treated as approximate and shall not be represented as precise safety measurements.
- The system shall use tracking or temporal smoothing to reduce repeated alerts.
### FR-08: Decision Engine
- The engine shall combine route state, object location, proximity, heading, and object type.
- The engine shall prioritize potentially path-blocking obstacles.
- The engine shall suppress repetitive alerts.
- The engine shall distinguish normal route instructions from urgent obstacle warnings.
- The engine shall produce short, understandable voice messages.
### FR-09: Voice Output
- The app shall use Android TextToSpeech for MVP output.
- Critical warnings shall have higher priority than normal route instructions.
- The system shall avoid speaking multiple messages simultaneously.
- The user shall be able to repeat the latest instruction.
### FR-10: Navigation Commands
- Navigate to <destination>
- Stop navigation
- Repeat
- Where am I?
- What's ahead?
- Start navigation
## 10. Voice Interaction State Machine
IDLE
  ↓
LISTENING
  ↓
PROCESSING
  ├── Destination found → SEARCHING
  └── Unclear → LISTENING
             ↓
          ROUTE_READY
             ↓
          NAVIGATING
             ├── Voice command
             ├── Route event
             ├── Obstacle event
             └── Arrival
             ↓
           ARRIVED
## 11. Vision Pipeline
Camera frame
    ↓
Preprocess
    ↓
YOLO26n
    ↓
Detections
    ↓
Spatial analysis
    ↓
Depth/proximity estimation
    ↓
Tracking / temporal smoothing
    ↓
Decision Engine
## 12. Navigation + Vision Interaction
The navigation engine describes where the user should go. The vision engine describes what is physically visible around the user. The decision engine combines these sources.
Route instruction:
    CONTINUE STRAIGHT

Vision:
    Pole
    CENTER
    NEAR

Decision:
    Potential path obstruction

Output:
    "Obstacle ahead. Move slightly left."
## 13. Non-Functional Requirements
- Privacy: camera frames used for inference should remain on-device in the MVP.
- Offline vision: object detection should not require an internet connection.
- Latency: the app should target responsive real-time detection; actual FPS must be measured on the target phone.
- Battery: inference frequency should be adjustable to balance responsiveness and power consumption.
- Accessibility: core navigation must be usable without reading the screen.
- Reliability: camera, GPS, STT, TTS, and routing failures must be handled without crashing the app.
- Modularity: detector and routing implementations must be replaceable behind interfaces.
- Logging: debug builds should record performance metrics without exposing unnecessary user data.
## 14. Ten-Phase Development Plan
## 15. MVP User Flow
Open app
  ↓
"Where do you want to go?"
  ↓
User speaks destination
  ↓
STT
  ↓
Geocoding
  ↓
Valhalla pedestrian route
  ↓
"Starting navigation."
  ↓
GPS tracks route
  ↓
Camera + YOLO26n run locally
  ↓
Vision detects relevant obstacle
  ↓
Decision Engine evaluates route conflict
  ↓
TTS gives concise warning
  ↓
Normal route instructions continue
  ↓
Destination reached
  ↓
"You have arrived."
## 16. MVP Acceptance Test
## 17. Performance Benchmarks
- Measure YOLO inference latency and effective FPS on at least one physical Android device.
- Measure end-to-end camera-to-warning latency.
- Measure CPU/GPU utilization, RAM, temperature, and battery impact.
- Measure GPS accuracy and route-recalculation latency.
- Measure STT recognition latency and TTS response latency.
- Do not use laptop/emulator performance as a substitute for phone benchmarks.
## 18. Risk and Safety Requirements
- The system must be presented as an experimental assistive prototype.
- It must not claim to reliably detect every obstacle.
- It must not instruct users to cross roads based solely on camera detection.
- Depth estimates must not be presented as precise measurements unless independently validated.
- The app should encourage use alongside established mobility aids during testing.
- Testing should occur first in controlled environments and with a sighted supervisor or other appropriate safety support.
## 19. Future Roadmap
- Custom navigation-specific dataset and model fine-tuning.
- Improved depth and distance validation.
- Better object tracking and motion prediction.
- Accessibility-aware route costing in Valhalla.
- Offline regional maps and routing.
- Haptic feedback.
- Wearable or smart-stick hardware.
- Additional classes such as stairs, curbs, potholes, crossings, and sidewalks.
- Advanced accessibility evaluation with real users.
- Production infrastructure and service-policy review.
## 20. Definition of Done
The MVP is complete when the Android APK can be installed on a physical device, accept a spoken destination, calculate a pedestrian route, provide voice navigation, run YOLO26n locally on camera frames, identify relevant objects and their coarse spatial/proximity relationship, and issue concise obstacle warnings through TTS while maintaining modular boundaries between routing, vision, voice, location, and decision-making.
## 21. Initial Repository Structure
visionnav/
├── app/
│   └── src/main/
│       ├── java/.../
│       │   ├── camera/
│       │   ├── vision/
│       │   ├── navigation/
│       │   ├── routing/
│       │   ├── location/
│       │   ├── audio/
│       │   ├── ui/
│       │   └── core/
│       └── assets/models/
├── routing/
│   └── valhalla/
├── ml/
│   ├── training/
│   ├── datasets/
│   └── experiments/
└── docs/
    ├── architecture.md
    └── testing.md

| Component | Technology | Purpose |
| --- | --- | --- |
| Platform | Android | Target mobile platform |
| Language | Kotlin | Native application development |
| UI | Jetpack Compose | Accessible application UI |
| Camera | CameraX | Camera capture and frame analysis |
| Detection | YOLO26n | Real-time object detection |
| ML runtime | LiteRT or NCNN | On-device inference |
| Depth | YOLO26n-depth initially | Coarse proximity estimation |
| Tracking | Lightweight tracker | Maintain object identity across frames |
| STT | Android SpeechRecognizer | Voice commands |
| TTS | Android TextToSpeech | Voice guidance |
| Location | Android location APIs | GPS position and heading |
| Map data | OpenStreetMap | Road/path data |
| Routing | Valhalla | Pedestrian route calculation |
| Development | Android Studio + ADB | Build, install, debug |


| Phase | Name | Deliverable |
| --- | --- | --- |
| Phase 1 | Project Foundation | Android Studio, Kotlin, Compose, permissions, Git, APK, ADB/USB debugging. |
| Phase 2 | Voice Interface | SpeechRecognizer, TextToSpeech, command parser, voice state machine. |
| Phase 3 | Location + Geocoding | GPS, accuracy, heading, destination geocoding. |
| Phase 4 | Valhalla Routing | Pedestrian route requests, route geometry, maneuvers, rerouting. |
| Phase 5 | Camera System | CameraX, frame analysis, resolution, rotation, lifecycle. |
| Phase 6 | YOLO26n | Mobile export/runtime, local inference, detection interface, FPS/latency benchmarks. |
| Phase 7 | Spatial + Depth | Left/center/right, coarse proximity, depth experiments, basic tracking. |
| Phase 8 | Decision Engine | Combine route + vision + location and prioritize meaningful events. |
| Phase 9 | Full Voice Navigation | End-to-end destination → route → obstacle awareness → TTS. |
| Phase 10 | Testing + Optimization | Device benchmarks, failure cases, optimization, release APK and documentation. |


| Test | Pass condition |
| --- | --- |
| Voice destination | User speaks a known destination and the app resolves it. |
| Routing | A valid pedestrian route is returned and displayed/logged. |
| Turn instruction | The app announces the next route maneuver. |
| Camera | Camera preview and frame analysis work on the physical device. |
| YOLO | YOLO26n detects supported objects on-device without a server. |
| Spatial awareness | Detected objects are assigned left/center/right. |
| Proximity | The system provides coarse proximity categories. |
| Obstacle warning | A relevant center-path obstacle triggers a concise warning. |
| Command control | Stop, repeat, where-am-I, and what's-ahead commands work. |
| Arrival | The app recognizes route completion and announces arrival. |
| Failure handling | Poor GPS, camera failure, or routing failure produces a usable voice message instead of a crash. |
