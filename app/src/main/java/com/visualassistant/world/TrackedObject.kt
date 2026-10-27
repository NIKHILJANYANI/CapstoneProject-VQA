package com.visualassistant.world

import com.visualassistant.perception.object_detection.DetectedObject
import com.visualassistant.perception.object_detection.DistanceCategory
import com.visualassistant.perception.ocr.TextRegion

/**
 * A tracked object with temporal state — combines detection + history.
 *
 * Tracks changes over time to detect events like "approaching".
 */
data class TrackedObject(
    val id: Int,
    val label: String,
    val position: SpatialPosition,
    val distanceCategory: DistanceCategory,
    val confidence: Float,
    val lastDetectedObject: DetectedObject,

    // History for motion detection
    val previousNormalizedArea: Float = 0f,
    val currentNormalizedArea: Float = 0f,
    val framesTracked: Int = 1,
    val framesMissing: Int = 0,
    val lastAnnouncedAtMs: Long = 0L
) {
    /**
     * Whether the object appears to be approaching (area increasing significantly).
     */
    val isApproaching: Boolean get() {
        if (framesTracked < 5 || previousNormalizedArea <= 0f) return false
        val increase = (currentNormalizedArea - previousNormalizedArea) / previousNormalizedArea
        return increase > 0.1f
    }

    /**
     * Whether the object appears to be receding.
     */
    val isReceding: Boolean get() {
        if (framesTracked < 5 || previousNormalizedArea <= 0f) return false
        val decrease = (previousNormalizedArea - currentNormalizedArea) / previousNormalizedArea
        return decrease > 0.1f
    }

    /**
     * Generate a natural description of this tracked object's state.
     */
    fun toNaturalDescription(): String {
        val posDesc = position.toDescription()
        val distDesc = lastDetectedObject.distanceDescription
        return when {
            isApproaching -> "A $label is approaching from $posDesc"
            isReceding -> "A $label is moving away $posDesc"
            distanceCategory == DistanceCategory.VERY_CLOSE ->
                "A $label is very close $posDesc"
            else -> "A $label is $posDesc, $distDesc"
        }
    }

    /**
     * Whether this object is in the walking path.
     */
    val isInPath: Boolean get() = lastDetectedObject.isInPath

    /**
     * Unique key for cooldown tracking (type + position bucket).
     */
    val cooldownKey: String get() = "${label}_${position.name}"
}
