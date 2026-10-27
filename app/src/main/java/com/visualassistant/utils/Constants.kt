package com.visualassistant.utils

/**
 * Application-wide constants.
 */
object Constants {

    // Frame processing rates
    const val CAMERA_FPS = 30
    const val OBJECT_DETECTION_INTERVAL_MS = 200L       // ~5 FPS
    const val DEPTH_ESTIMATION_INTERVAL_MS = 500L       // ~2 FPS
    const val HAZARD_DETECTION_INTERVAL_MS = 200L       // ~5 FPS
    const val SCENE_ANALYSIS_INTERVAL_MS = 1000L        // 1 FPS

    // Speech cooldown (ms) — minimum time before repeating an event type
    const val SPEECH_COOLDOWN_DEFAULT_MS = 5000L
    const val SPEECH_COOLDOWN_HAZARD_MS = 3000L
    const val SPEECH_COOLDOWN_NAVIGATION_MS = 2000L

    // Distance estimation thresholds (normalized, based on bbox height ratio)
    const val DISTANCE_VERY_CLOSE_THRESHOLD = 0.5f   // bbox occupies >50% of frame height → very close
    const val DISTANCE_CLOSE_THRESHOLD = 0.25f       // >25% → close (~1-2m)
    const val DISTANCE_MEDIUM_THRESHOLD = 0.12f      // >12% → medium (~3-5m)
    // Anything smaller → far (>5m)

    // Spatial position thresholds (based on center X as fraction of frame width)
    const val POSITION_LEFT_MAX = 0.35f
    const val POSITION_RIGHT_MIN = 0.65f
    // Between 0.35 and 0.65 → center

    // ObjectTracker
    const val MAX_TRACKED_OBJECTS = 20
    const val TRACKING_IOU_THRESHOLD = 0.3f
    const val OBJECT_MISSING_FRAMES_THRESHOLD = 10   // frames before removing tracked object

    // Approaching detection
    const val APPROACHING_SIZE_INCREASE_THRESHOLD = 0.15f  // 15% size increase = approaching

    // Task navigation
    const val TASK_SEARCH_TIMEOUT_MS = 30_000L
    const val TASK_TARGET_REACHED_DISTANCE = 0.4f   // Very close threshold

    // Memory
    const val MEMORY_DB_NAME = "visual_assistant_memory"
    const val MAX_MEMORY_ENTRIES = 500

    // Model filenames (placed in assets/)
    const val OBJECT_DETECTION_MODEL = "efficientdet_lite0.tflite"
    const val DEPTH_MODEL = "midas_small.tflite"
    const val OBJECT_DETECTION_LABELS = "coco_labels.txt"

    // Object detection input size
    const val OBJECT_DETECTION_INPUT_SIZE = 320

    // Depth model input size
    const val DEPTH_INPUT_WIDTH = 256
    const val DEPTH_INPUT_HEIGHT = 256

    // Confidence thresholds
    const val OBJECT_DETECTION_CONFIDENCE = 0.30f
    const val HAZARD_CONFIDENCE = 0.25f   // Lower threshold for safety

    // TTS speech rate
    const val TTS_SPEECH_RATE_DEFAULT = 0.9f
    const val TTS_PITCH_DEFAULT = 1.0f

    // Permissions request code
    const val PERMISSION_REQUEST_CODE = 100

    // Hazard COCO classes (indices)
    val HAZARD_COCO_CLASSES = setOf(
        "knife", "scissors", "fire hydrant"
        // fire is NOT in COCO — handled by dedicated detector or heuristic
    )

    // Navigation target classes
    val NAVIGATION_CLASSES = setOf(
        "door", "stairs", "elevator", "escalator", "exit"
    )

    // Classes that should generally be LOW priority unless in path
    val LOW_PRIORITY_CLASSES = setOf(
        "chair", "table", "laptop", "bottle", "book", "plant",
        "monitor", "keyboard", "mouse", "cup", "vase", "clock"
    )

    // HIGH priority classes (potential obstacles/navigation)
    val HIGH_PRIORITY_CLASSES = setOf(
        "person", "bicycle", "car", "motorcycle", "truck", "bus",
        "dog", "cat"
    )

    // Room number regex pattern
    val ROOM_NUMBER_PATTERN = Regex("""(?i)(room\s*)?(\d{3,4}[A-Z]?)""")

    // Sign keywords for memory storage
    val SIGN_KEYWORDS = listOf(
        "room", "floor", "exit", "elevator", "stairs", "reception",
        "cafeteria", "library", "lab", "office", "restroom", "bathroom",
        "emergency", "fire", "entrance", "lobby"
    )
}
