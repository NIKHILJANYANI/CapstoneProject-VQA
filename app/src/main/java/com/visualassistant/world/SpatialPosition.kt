package com.visualassistant.world

/**
 * Spatial position of an object relative to the user's viewpoint.
 */
enum class SpatialPosition {
    LEFT,
    CENTER,
    RIGHT,
    ABOVE,
    BELOW,
    BEHIND,
    UNKNOWN;

    fun toDescription(): String = when (this) {
        LEFT -> "to your left"
        CENTER -> "ahead"
        RIGHT -> "to your right"
        ABOVE -> "above you"
        BELOW -> "below you"
        BEHIND -> "behind you"
        UNKNOWN -> "nearby"
    }

    fun toDirectionInstruction(): String = when (this) {
        LEFT -> "Turn slightly left"
        CENTER -> "Walk forward"
        RIGHT -> "Turn slightly right"
        ABOVE -> "Look up"
        BELOW -> "Look down"
        BEHIND -> "Turn around"
        UNKNOWN -> "Look around"
    }
}
