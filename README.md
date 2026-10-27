# Visual Assistant — Offline AI Visual Personal Assistant for Visually Impaired Users

> ⚠️ **Safety Disclaimer**: This application is an assistive tool only. It is NOT a certified safety device. Always exercise real-world caution. Perception may be incomplete or incorrect. Do not rely solely on this application for personal safety.

---

## Overview

An intelligent, **voice-first**, **offline-capable** Android assistant that:

- 👁️ **Sees** the user's environment using the device camera
- 🎤 **Listens** to voice commands (works offline with Android's offline recognition model)
- 🧠 **Reasons** about what matters most (not just listing every object)
- 🔊 **Speaks** only when necessary using prioritized TTS
- 🗺️ **Remembers** rooms, elevators, and landmarks across a session
- ⚠️ **Warns** about hazards (stairs, approaching people, sharp objects)
- 🚪 **Navigates** the user to goals (find door, find elevator, exit room)

---

## Architecture

```
CAMERA (30 FPS)
      │
      ▼
PerceptionPipeline
      │
  ┌───┴────────────────────┐
  │                        │
ObjectDetector          OCRProcessor
(TFLite ~5 FPS)        (ML Kit, on-demand)
  │
ObjectTracker
  │
HazardDetector
  │
  └──────────────┐
                 ▼
            WorldModel ←──── SpatialMemory
                 │
                 ▼
         AssistantEngine
         ┌───────────────┐
         │  IntentParser │ ← Voice Input
         │  PriorityEngine
         │  ResponseGenerator
         │  TaskManager  │
         │  ConvContext  │
         └───────┬───────┘
                 │
                 ▼
            TTSManager (Priority Queue)
                 │
                 ▼
              USER 🔊
```

---

## Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android device running API 26+ (Android 8.0+)
- Python 3.x (for model download script)

### Step 1: Download models

```bash
cd VQA
python scripts/download_models.py
```

This downloads:
- `efficientdet_lite0.tflite` (~6MB) → `app/src/main/assets/`
- `coco_labels.txt` → `app/src/main/assets/`

### Step 2: Open in Android Studio

1. Open Android Studio
2. File → Open → select the `VQA` folder
3. Wait for Gradle sync to complete

### Step 3: Configure Google Maps (Optional)

For outdoor navigation, replace `YOUR_MAPS_API_KEY_HERE` in `AndroidManifest.xml` with your actual Google Maps API key from [Google Cloud Console](https://console.cloud.google.com/).

### Step 4: Enable Offline Speech Recognition

On your Android device:
1. Settings → General Management → Language and Input
2. On-device recognition → Download the English model

### Step 5: Build and Run

1. Connect your Android device
2. Enable Developer Options and USB Debugging
3. Click Run in Android Studio

---

## Usage

### Basic Usage

1. Open the app
2. Press the large **START** button
3. Assistant says: *"Visual assistant started. How can I help you?"*
4. Tap the **microphone button** to speak a command
5. Press **STOP** to stop

### Voice Commands

| Command | Action |
|---------|--------|
| `"What is in front of me?"` | Describe what's ahead |
| `"What is on my left?"` | Describe left side |
| `"What is around me?"` | Full surroundings description |
| `"Is anything dangerous nearby?"` | Check for hazards |
| `"Is there anyone approaching?"` | Check for people |
| `"Where is the door?"` | Find a door |
| `"Guide me out of the room."` | Navigate to exit |
| `"Find the elevator."` | Find elevator |
| `"Take me to Room 204."` | Navigate to room |
| `"What does that sign say?"` | Read visible text |
| `"Read that."` | OCR on current view |
| `"Start walking mode."` | Enter exploration mode |
| `"Remember this location."` | Save current landmark |
| `"Where did I see the elevator?"` | Query memory |
| `"Take me there."` | Guide to last mentioned object |
| `"Stop."` | Stop current task |

---

## Module Structure

```
app/src/main/java/com/visualassistant/
├── MainActivity.kt                    # Entry point
├── VisualAssistantApp.kt             # Application class
├── VisualAssistantViewModel.kt       # Coordinates all subsystems
│
├── camera/
│   └── CameraManager.kt             # CameraX setup
│
├── perception/
│   ├── PerceptionPipeline.kt        # Orchestrates all perception
│   ├── object_detection/
│   │   ├── ObjectDetector.kt        # TFLite EfficientDet-Lite0
│   │   ├── DetectedObject.kt        # Detection result with spatial info
│   │   └── ObjectTracker.kt         # IoU-based temporal tracking
│   ├── depth/                       # Depth estimation (Milestone 2+)
│   ├── ocr/
│   │   ├── OCRProcessor.kt          # ML Kit text recognition
│   │   └── TextRegion.kt            # OCR result data class
│   ├── hazard/
│   │   ├── HazardDetector.kt        # Filters objects for hazards
│   │   └── HazardEvent.kt           # Hazard event data class
│   └── scene/                       # Scene analysis (future)
│
├── world/
│   ├── WorldModel.kt                # Current scene state
│   ├── TrackedObject.kt             # Object + temporal motion state
│   └── SpatialPosition.kt           # LEFT/CENTER/RIGHT enum
│
├── assistant/
│   ├── AssistantEngine.kt           # Top-level coordinator
│   ├── intent/
│   │   ├── IntentParser.kt          # Voice → intent mapping
│   │   └── UserIntent.kt            # Sealed class of all intents
│   ├── reasoning/
│   │   └── ResponseGenerator.kt    # Template NLG response generation
│   ├── conversation/
│   │   └── ConversationContext.kt  # Short-term context for follow-ups
│   └── priority/
│       ├── PriorityEngine.kt        # Context-aware event priority
│       └── Priority.kt              # Priority enum + SpeechEvent
│
├── navigation/
│   └── TaskManager.kt               # Task state machine
│
├── memory/
│   ├── SpatialMemory.kt            # Session spatial memory
│   └── MemoryEntry.kt              # Memory entry data class
│
├── speech/
│   ├── speech_to_text/
│   │   └── SpeechRecognitionManager.kt  # Android SpeechRecognizer
│   └── text_to_speech/
│       └── TTSManager.kt               # Priority TTS queue
│
├── ui/
│   ├── MainScreen.kt               # Compose UI (minimal, accessible)
│   └── theme/
│       └── Theme.kt                # High-contrast theme
│
└── utils/
    ├── Constants.kt                 # All configuration constants
    └── BitmapUtils.kt               # Frame conversion utilities
```

---

## Models

| Model | Size | FPS | Purpose |
|-------|------|-----|---------|
| EfficientDet-Lite0 INT8 | ~6 MB | 5 FPS | Object detection (80 COCO classes) |
| ML Kit Text Recognition v2 | ~3 MB (auto) | On-demand | OCR / sign reading |
| Android SpeechRecognizer | OS-level | — | Voice input |
| Android TextToSpeech | OS-level | — | Voice output |

### Adding Custom Models

To add a custom hazard model (e.g., fire detector):

1. Place the `.tflite` file in `app/src/main/assets/`
2. Create a new class in `perception/hazard/` implementing the same interface as `ObjectDetector`
3. Register it in `PerceptionPipeline.kt`
4. Add the new hazard type to `HazardType` enum

---

## Priority System

Events are scored 0–100 before TTS output:

| Priority | Value | Examples |
|----------|-------|---------|
| CRITICAL | 100 | Fire, vehicle in path, obstacle very close, task target found |
| VERY_HIGH | 85 | Sharp object nearby, stairs in path |
| HIGH | 70 | Person approaching, door/elevator nearby, obstacle ahead |
| MEDIUM | 40 | Door at distance, elevator visible |
| LOW | 20 | Furniture not in path |
| IGNORE | 0 | Decorative objects, background |

Priority is **task-modulated**: if the user says "find elevator", elevator priority becomes CRITICAL.

---

## Frame Processing Budget

| Operation | Frequency | Notes |
|-----------|-----------|-------|
| Camera capture | 30 FPS | Raw frame rate |
| Object detection | ~5 FPS | Every 200ms |
| Hazard detection | ~5 FPS | Same pass as object detection |
| Depth estimation | ~2 FPS | Planned for Milestone 2 |
| OCR | On-demand | Triggered by user or auto every 10s in room-search mode |
| Scene reasoning | On-demand | Per user question |

---

## Accessibility Features

- 🔘 **200dp circular START/STOP button** — large enough to tap without vision
- 🎤 **140dp microphone button** — single tap to speak
- 📢 **TalkBack-compatible semantics** on all interactive elements
- 🌑 **High-contrast dark theme** (gold on black, green on black)
- 📳 **Haptic feedback** on all button presses
- 🔊 **Voice-first interaction** — no typing required
- 🎚️ **Configurable TTS speech rate and pitch**

---

## Limitations

| Limitation | Reason | Mitigation |
|-----------|--------|-----------|
| No on-device LLM | Too large for continuous use | Template NLG + pattern matching |
| Depth is relative only | MiDaS gives relative, not metric depth | Reports "approximately" |
| Fire not in COCO | COCO has no fire class | Custom model slot available |
| Speech recognition requires offline model setup | Android SpeechRecognizer | Instructions provided |
| Room memory not persistent across app restarts | Room DB not yet wired | Session-only memory for now |

---

## Development Roadmap

| Milestone | Status | Features |
|-----------|--------|---------|
| M1 | ✅ Complete | Camera, TTS, STT, START/STOP UI |
| M2 | ✅ Complete | Object detection, tracking, WorldModel |
| M3 | ✅ Complete | Priority engine, intelligent narration, hazard detection |
| M4 | ✅ Complete | Task navigation, door/elevator/exit search |
| M5 | ✅ Complete | OCR, hazard detection |
| M6 | ✅ Complete | Spatial memory (session), room memory |
| M7 | ✅ Complete | Conversational context, follow-ups |
| M8 | 🔲 Planned | MiDaS depth, persistent Room DB, Google Maps |

---

## Running Tests

```bash
# Unit tests (no device required)
./gradlew test

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

---

## Performance Tuning

In `Constants.kt`, adjust:

```kotlin
OBJECT_DETECTION_INTERVAL_MS = 200L   // Increase for battery savings
HAZARD_CONFIDENCE = 0.35f             // Lower = more sensitive, more false positives
SPEECH_COOLDOWN_DEFAULT_MS = 5000L    // Increase to reduce speech frequency
```

For lower-end devices, increase `OBJECT_DETECTION_INTERVAL_MS` to `300-500L`.

---

## Contributing / Extending

### Add a New Intent

1. Add a new data class / object to `UserIntent.kt`
2. Add pattern matching in `IntentParser.kt`
3. Add response template in `ResponseGenerator.kt`
4. Handle in `AssistantEngine.updateTaskFromIntent()`

### Add a New Hazard Class

1. Add to `HazardType` enum in `HazardEvent.kt`
2. Add label → type mapping in `HazardType.fromLabel()`
3. Add speech template in `HazardEvent.toSpeechText()`

### Replace Object Detection Model

1. Place new `.tflite` in `assets/`
2. Update `OBJECT_DETECTION_MODEL` in `Constants.kt`
3. Adjust `ObjectDetector.kt` if output tensor layout differs
