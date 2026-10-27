package com.visualassistant.perception.object_detection

import android.graphics.RectF
import com.visualassistant.world.SpatialPosition

/**
 * A detected object from the object detection model.
 *
 * @param label Human-readable class label (e.g., "person", "knife")
 * @param confidence Detection confidence [0, 1]
 * @param boundingBox Normalized bounding box [0,1] relative to frame dimensions
 * @param trackingId Assigned by the tracker (null if not yet tracked)
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,          // normalized [0,1]
    val trackingId: Int? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
) {
    /**
     * Center X as fraction of frame width [0, 1].
     */
    val centerX: Float get() = (boundingBox.left + boundingBox.right) / 2f

    /**
     * Center Y as fraction of frame height [0, 1].
     */
    val centerY: Float get() = (boundingBox.top + boundingBox.bottom) / 2f

    /**
     * Bounding box height as fraction of frame height.
     * Used as a proxy for distance estimation.
     */
    val normalizedHeight: Float get() = boundingBox.bottom - boundingBox.top

    /**
     * Bounding box width as fraction of frame width.
     */
    val normalizedWidth: Float get() = boundingBox.right - boundingBox.left

    /**
     * Area of the bounding box as fraction of frame area.
     */
    val normalizedArea: Float get() = normalizedHeight * normalizedWidth

    /**
     * Spatial horizontal position derived from center X.
     */
    val horizontalPosition: SpatialPosition get() = when {
        centerX > 0.65f -> SpatialPosition.LEFT
        centerX < 0.35f -> SpatialPosition.RIGHT
        else -> SpatialPosition.CENTER
    }

    /**
     * Rough distance category based on bounding box height.
     * These are approximations — always report as "approximately".
     */
    val distanceCategory: DistanceCategory get() = when {
        normalizedHeight > 0.5f -> DistanceCategory.VERY_CLOSE   // <1m
        normalizedHeight > 0.25f -> DistanceCategory.CLOSE       // ~1-2m
        normalizedHeight > 0.12f -> DistanceCategory.MEDIUM      // ~3-4m
        normalizedHeight > 0.05f -> DistanceCategory.FAR         // ~5-8m
        else -> DistanceCategory.VERY_FAR                        // >8m
    }

    /**
     * Human-readable distance string.
     */
    val distanceDescription: String get() = when (distanceCategory) {
        DistanceCategory.VERY_CLOSE -> "very close"
        DistanceCategory.CLOSE -> "approximately one to two meters away"
        DistanceCategory.MEDIUM -> "approximately three to four meters away"
        DistanceCategory.FAR -> "approximately five to eight meters away"
        DistanceCategory.VERY_FAR -> "far away"
    }

    /**
     * Whether this object is in the user's direct walking path (center area, not too far).
     */
    val isInPath: Boolean get() = horizontalPosition == SpatialPosition.CENTER &&
            distanceCategory != DistanceCategory.VERY_FAR

    /**
     * Compute IoU (Intersection over Union) with another bounding box.
     * Used for temporal tracking.
     */
    fun iouWith(other: DetectedObject): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(this.boundingBox, other.boundingBox)) return 0f
        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = (this.normalizedArea + other.normalizedArea) - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }
}

enum class DistanceCategory {
    VERY_CLOSE, CLOSE, MEDIUM, FAR, VERY_FAR
}
