package com.visualassistant.world

import com.visualassistant.perception.ocr.TextRegion

/**
 * The WorldModel is the structured, current representation of the scene.
 *
 * This is the central data structure used by the Assistant for reasoning.
 * It is updated periodically by the PerceptionPipeline and read by the AssistantEngine.
 */
data class WorldModel(
    val timestampMs: Long = System.currentTimeMillis(),
    val trackedObjects: List<TrackedObject> = emptyList(),
    val detectedText: List<TextRegion> = emptyList(),
    val hazards: List<TrackedObject> = emptyList(),
    val navigationTargets: List<TrackedObject> = emptyList()
) {

    /** Whether there are any detected objects */
    val isEmpty: Boolean get() = trackedObjects.isEmpty()

    /** Objects currently in the walking path (center, not very far) */
    val objectsInPath: List<TrackedObject> get() = trackedObjects.filter { it.isInPath }

    /** Objects on the left side */
    val objectsOnLeft: List<TrackedObject> get() = trackedObjects.filter {
        it.position == SpatialPosition.LEFT
    }

    /** Objects on the right side */
    val objectsOnRight: List<TrackedObject> get() = trackedObjects.filter {
        it.position == SpatialPosition.RIGHT
    }

    /** Objects directly ahead */
    val objectsAhead: List<TrackedObject> get() = trackedObjects.filter {
        it.position == SpatialPosition.CENTER
    }

    /** Objects that are approaching */
    val approachingObjects: List<TrackedObject> get() = trackedObjects.filter { it.isApproaching }

    /** Find objects by label (case-insensitive) */
    fun findByLabel(label: String): List<TrackedObject> =
        trackedObjects.filter { it.label.equals(label, ignoreCase = true) }

    /** Find first object of a given label */
    fun findFirst(label: String): TrackedObject? = findByLabel(label).firstOrNull()

    /** Check if any of the given labels are present */
    fun containsAny(labels: Collection<String>): Boolean =
        trackedObjects.any { obj -> labels.any { it.equals(obj.label, ignoreCase = true) } }

    /** All detected text joined (for quick lookup) */
    val allDetectedTextCombined: String get() = detectedText.joinToString(" ") { it.text }

    /**
     * Build a concise scene description string (for debugging/logging).
     */
    fun toDebugString(): String = buildString {
        append("WorldModel @ ${timestampMs}ms\n")
        if (trackedObjects.isEmpty()) {
            append("  No objects detected\n")
        } else {
            trackedObjects.forEach { obj ->
                append("  [${obj.label}] ${obj.position.name} dist=${obj.distanceCategory.name}")
                if (obj.isApproaching) append(" APPROACHING")
                append("\n")
            }
        }
        if (hazards.isNotEmpty()) {
            append("  HAZARDS: ${hazards.map { it.label }}\n")
        }
        if (detectedText.isNotEmpty()) {
            append("  TEXT: ${detectedText.map { it.text }}\n")
        }
    }
}
