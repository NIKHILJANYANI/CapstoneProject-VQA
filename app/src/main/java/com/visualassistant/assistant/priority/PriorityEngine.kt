package com.visualassistant.assistant.priority

import com.visualassistant.navigation.TaskManager
import com.visualassistant.navigation.NavigationTask
import com.visualassistant.utils.Constants
import com.visualassistant.world.TrackedObject
import com.visualassistant.world.SpatialPosition
import com.visualassistant.perception.object_detection.DistanceCategory
import com.visualassistant.perception.hazard.HazardEvent
import com.visualassistant.perception.hazard.HazardType

/**
 * The PriorityEngine assigns speech priority to detected events.
 *
 * Priority depends on:
 * 1. Object type (hazard > navigation > obstacle > person > furniture)
 * 2. Current task (if task = find elevator, elevator priority → CRITICAL)
 * 3. Spatial context (objects in path > objects to the side)
 * 4. Motion (approaching objects have higher priority)
 */
class PriorityEngine {

    /**
     * Compute priority for a tracked object given the current task.
     */
    fun computePriority(
        obj: TrackedObject,
        currentTask: NavigationTask?
    ): Priority {
        val label = obj.label.lowercase()

        // Task-specific boosting
        if (currentTask != null) {
            if (isTaskTarget(label, currentTask)) {
                return Priority.CRITICAL
            }
        }

        // Hazard classes
        if (isHazardClass(label)) {
            return if (obj.isInPath) Priority.CRITICAL else Priority.VERY_HIGH
        }

        // Stairs (always high priority if detected)
        if (label == "stairs" || label == "staircase" || label == "steps") {
            return if (obj.isInPath) Priority.CRITICAL else Priority.HIGH
        }

        // Approaching person
        if (label == "person" && obj.isApproaching) {
            return Priority.HIGH
        }

        // Obstacle in path
        if (obj.isInPath && obj.distanceCategory == DistanceCategory.VERY_CLOSE) {
            return Priority.VERY_HIGH
        }

        // Navigation targets (door, elevator) with distance context
        if (isNavigationTarget(label)) {
            return when (obj.distanceCategory) {
                DistanceCategory.VERY_CLOSE, DistanceCategory.CLOSE -> Priority.HIGH
                DistanceCategory.MEDIUM -> Priority.MEDIUM
                else -> Priority.MEDIUM
            }
        }

        // Vehicle / bicycle
        if (label in setOf("car", "truck", "bus", "motorcycle", "bicycle")) {
            return if (obj.isApproaching) Priority.CRITICAL else Priority.HIGH
        }

        // Person standing still
        if (label == "person") {
            return if (obj.isInPath && obj.distanceCategory == DistanceCategory.CLOSE)
                Priority.HIGH else Priority.MEDIUM
        }

        // Furniture / low-priority objects
        if (label in Constants.LOW_PRIORITY_CLASSES) {
            return if (obj.isInPath && obj.distanceCategory != DistanceCategory.FAR
                && obj.distanceCategory != DistanceCategory.VERY_FAR)
                Priority.MEDIUM else Priority.LOW
        }

        return Priority.LOW
    }

    /**
     * Compute priority for a hazard event.
     */
    fun computeHazardPriority(hazard: HazardEvent): Priority = when {
        hazard.type == HazardType.FIRE -> Priority.CRITICAL
        hazard.type == HazardType.VEHICLE && hazard.isInPath -> Priority.CRITICAL
        hazard.type == HazardType.OBSTACLE_IN_PATH -> Priority.CRITICAL
        hazard.type == HazardType.STAIRS && hazard.isInPath -> Priority.CRITICAL
        hazard.type == HazardType.SHARP_OBJECT -> Priority.VERY_HIGH
        hazard.type == HazardType.BICYCLE && hazard.isInPath -> Priority.VERY_HIGH
        hazard.type == HazardType.PERSON_APPROACHING -> Priority.HIGH
        else -> Priority.HIGH
    }

    private fun isHazardClass(label: String): Boolean =
        label in setOf("knife", "scissors", "fire", "flame", "car", "truck", "bus", "motorcycle")

    private fun isNavigationTarget(label: String): Boolean =
        label in setOf("door", "elevator", "exit", "stairs", "escalator", "hallway")

    private fun isTaskTarget(label: String, task: NavigationTask): Boolean {
        return when (task) {
            is NavigationTask.FindDoor -> label in setOf("door", "doorway", "exit")
            is NavigationTask.FindElevator -> label in setOf("elevator", "lift")
            is NavigationTask.FindRoom -> false  // Handled separately via OCR
            is NavigationTask.FindExit -> label in setOf("door", "exit", "hallway")
            is NavigationTask.FindReception -> label in setOf("reception", "desk")
            is NavigationTask.FindObject -> label.equals(task.objectLabel, ignoreCase = true)
            is NavigationTask.GuideToTarget -> label.equals(task.target.label, ignoreCase = true)
            is NavigationTask.ExploreMode -> false
            is NavigationTask.None -> false
        }
    }

    /**
     * Filter objects that should be announced, sorted by priority descending.
     * Returns only objects above a minimum priority threshold.
     */
    fun filterAndSortForAnnouncement(
        objects: List<TrackedObject>,
        currentTask: NavigationTask?,
        minimumPriority: Priority = Priority.MEDIUM
    ): List<Pair<TrackedObject, Priority>> {
        return objects
            .map { it to computePriority(it, currentTask) }
            .filter { (_, priority) -> priority.value >= minimumPriority.value }
            .sortedByDescending { (_, priority) -> priority.value }
    }
}
