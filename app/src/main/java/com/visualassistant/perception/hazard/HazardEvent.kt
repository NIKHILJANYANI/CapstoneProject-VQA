package com.visualassistant.perception.hazard

import com.visualassistant.world.SpatialPosition

/**
 * A detected hazard event.
 *
 * Hazards have higher priority than normal objects and trigger different behavior.
 */
data class HazardEvent(
    val type: HazardType,
    val position: SpatialPosition,
    val confidence: Float,
    val isInPath: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
    val label: String? = null
) {
    fun toSpeechText(): String = when (type) {
        HazardType.FIRE -> "Fire detected ${position.toDescription()}."
        HazardType.VEHICLE -> "Vehicle ${position.toDescription()}."
        HazardType.SHARP_OBJECT -> when {
            isInPath -> "Sharp object directly in your path."
            else -> "A sharp object is ${position.toDescription()}."
        }
        HazardType.STAIRS -> when {
            isInPath -> "Stairs begin ahead."
            else -> "Stairs are ${position.toDescription()}."
        }
        HazardType.OBSTACLE_IN_PATH -> {
            val name = label ?: "Obstacle"
            "${name.replaceFirstChar { it.uppercase() }} directly ahead."
        }
        HazardType.BICYCLE -> "Bicycle ${position.toDescription()}."
        HazardType.PERSON_APPROACHING -> "A person is approaching from ${position.toDescription()}."
        HazardType.UNKNOWN -> "Potential hazard ${position.toDescription()}."
    }

    fun priority(): Int = type.priority
}

enum class HazardType(val priority: Int) {
    FIRE(100),
    VEHICLE(95),
    OBSTACLE_IN_PATH(90),
    STAIRS(80),
    SHARP_OBJECT(75),
    BICYCLE(70),
    PERSON_APPROACHING(65),
    UNKNOWN(50);

    companion object {
        fun fromLabel(label: String): HazardType? = when (label.lowercase()) {
            "fire", "flame" -> FIRE
            "car", "truck", "bus", "motorcycle", "vehicle" -> VEHICLE
            "knife", "scissors" -> SHARP_OBJECT
            "stairs", "staircase", "steps" -> STAIRS
            "bicycle", "bike" -> BICYCLE
            else -> null
        }
    }
}
