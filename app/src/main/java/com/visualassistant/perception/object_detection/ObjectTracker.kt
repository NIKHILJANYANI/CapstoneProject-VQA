package com.visualassistant.perception.object_detection

import com.visualassistant.utils.Constants
import com.visualassistant.world.SpatialPosition
import com.visualassistant.world.TrackedObject
import kotlin.math.abs

/**
 * ObjectTracker maintains temporal state across detection frames.
 *
 * Uses IoU-based matching to associate new detections with existing tracks.
 * Detects motion events (approaching, receding) by comparing bounding box areas over time.
 *
 * This is an intentionally simple tracker — good enough for the assistive use case
 * without requiring complex Kalman filtering.
 */
class ObjectTracker {

    private val tracks = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0

    /**
     * Update tracker with new detections from the current frame.
     *
     * @param detections Current frame's detections
     * @return Updated list of tracked objects
     */
    fun update(detections: List<DetectedObject>): List<TrackedObject> {
        // Mark all existing tracks as potentially missing
        val updatedTracks = mutableMapOf<Int, TrackedObject>()
        val matchedDetectionIndices = mutableSetOf<Int>()

        // Try to match each existing track to a new detection
        for ((id, track) in tracks) {
            var bestIou = Constants.TRACKING_IOU_THRESHOLD
            var bestMatchIndex = -1

            for ((i, detection) in detections.withIndex()) {
                if (i in matchedDetectionIndices) continue
                if (!detection.label.equals(track.label, ignoreCase = true)) continue

                val iou = detection.iouWith(track.lastDetectedObject)
                if (iou > bestIou) {
                    bestIou = iou
                    bestMatchIndex = i
                }
            }

            if (bestMatchIndex >= 0) {
                // Match found — update track
                val detection = detections[bestMatchIndex]
                matchedDetectionIndices.add(bestMatchIndex)
                updatedTracks[id] = track.copy(
                    position = detection.horizontalPosition,
                    distanceCategory = detection.distanceCategory,
                    confidence = detection.confidence,
                    lastDetectedObject = detection.copy(trackingId = id),
                    previousNormalizedArea = if (track.framesTracked % 5 == 0) track.currentNormalizedArea else track.previousNormalizedArea,
                    currentNormalizedArea = (track.currentNormalizedArea * 0.7f) + (detection.normalizedArea * 0.3f),
                    framesTracked = track.framesTracked + 1,
                    framesMissing = 0
                )
            } else {
                // No match — increment missing counter
                val newMissing = track.framesMissing + 1
                if (newMissing <= Constants.OBJECT_MISSING_FRAMES_THRESHOLD) {
                    updatedTracks[id] = track.copy(framesMissing = newMissing)
                }
                // Else: drop the track
            }
        }

        // Create new tracks for unmatched detections
        for ((i, detection) in detections.withIndex()) {
            if (i in matchedDetectionIndices) continue
            val id = nextId++
            updatedTracks[id] = TrackedObject(
                id = id,
                label = detection.label,
                position = detection.horizontalPosition,
                distanceCategory = detection.distanceCategory,
                confidence = detection.confidence,
                lastDetectedObject = detection.copy(trackingId = id),
                previousNormalizedArea = 0f,
                currentNormalizedArea = detection.normalizedArea,
                framesTracked = 1,
                framesMissing = 0
            )
        }

        // Cap total tracked objects
        val finalTracks = if (updatedTracks.size > Constants.MAX_TRACKED_OBJECTS) {
            updatedTracks.entries.sortedByDescending { it.value.confidence }
                .take(Constants.MAX_TRACKED_OBJECTS)
                .associate { it.key to it.value }
                .toMutableMap()
        } else {
            updatedTracks
        }

        tracks.clear()
        tracks.putAll(finalTracks)
        return tracks.values.toList()
    }

    /**
     * Reset all tracks (e.g., when assistant is stopped).
     */
    fun reset() {
        tracks.clear()
        nextId = 0
    }

    /**
     * Get the current tracked objects without updating.
     */
    fun getCurrentTracks(): List<TrackedObject> = tracks.values.toList()
}
