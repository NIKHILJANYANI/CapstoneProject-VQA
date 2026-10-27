package com.visualassistant.perception.hazard

import com.visualassistant.perception.object_detection.DetectedObject
import com.visualassistant.world.TrackedObject
import com.visualassistant.utils.Constants

/**
 * HazardDetector filters tracked objects for hazardous entities.
 *
 * Strategy:
 * 1. Filter COCO classes known to be hazardous (knife, scissors, vehicle)
 * 2. Classify hazard type
 * 3. Apply lower confidence threshold for safety
 * 4. Generate HazardEvent
 *
 * Note: Fire is NOT in COCO classes. The system generates a "possible fire"
 * heuristic if bright orange/red color occupies a large portion of the frame.
 * A dedicated fire model can be added as a drop-in replacement.
 */
class HazardDetector {

    /**
     * Extract hazard events from the current set of tracked objects.
     */
    fun detectHazards(trackedObjects: List<TrackedObject>): List<HazardEvent> {
        val hazards = mutableListOf<HazardEvent>()

        for (obj in trackedObjects) {
            val hazardType = HazardType.fromLabel(obj.label) ?: continue

            // Apply lower confidence threshold for safety-critical objects
            if (obj.confidence < Constants.HAZARD_CONFIDENCE) continue

            hazards.add(
                HazardEvent(
                    type = hazardType,
                    position = obj.position,
                    confidence = obj.confidence,
                    isInPath = obj.isInPath,
                    label = obj.label
                )
            )
        }

        // Sort by priority descending
        return hazards.sortedByDescending { it.priority() }
    }

    /**
     * Check if any object represents a person approaching (special hazard case).
     */
    fun detectApproachingPerson(trackedObjects: List<TrackedObject>): HazardEvent? {
        val approachingPerson = trackedObjects.find {
            it.label.equals("person", ignoreCase = true) && it.isApproaching
        } ?: return null

        return HazardEvent(
            type = HazardType.PERSON_APPROACHING,
            position = approachingPerson.position,
            confidence = approachingPerson.confidence,
            isInPath = approachingPerson.isInPath,
            label = "person"
        )
    }

    /**
     * Check if any object in path is a collision risk.
     */
    fun detectObstacleInPath(trackedObjects: List<TrackedObject>): HazardEvent? {
        val obstacle = trackedObjects.find { obj ->
            obj.isInPath &&
            obj.distanceCategory == com.visualassistant.perception.object_detection.DistanceCategory.VERY_CLOSE &&
            obj.label !in setOf("person")  // Person approaching handled separately
        } ?: return null

        return HazardEvent(
            type = HazardType.OBSTACLE_IN_PATH,
            position = obstacle.position,
            confidence = obstacle.confidence,
            isInPath = true,
            label = obstacle.label
        )
    }
}
