package com.visualassistant.assistant.priority

/**
 * Priority levels for speech output events.
 *
 * CRITICAL events interrupt anything currently being spoken.
 * Higher numeric value = higher priority.
 */
enum class Priority(val value: Int) {
    IGNORE(0),
    VERY_LOW(10),
    LOW(20),
    MEDIUM(40),
    HIGH(70),
    VERY_HIGH(85),
    CRITICAL(100);

    fun isHigherThan(other: Priority): Boolean = this.value > other.value
}

/**
 * A speech event with associated priority, text, and deduplication key.
 */
data class SpeechEvent(
    val text: String,
    val priority: Priority,
    val cooldownKey: String,      // Used for deduplication (same key = same event type)
    val cooldownMs: Long = 5000L,
    val shouldInterrupt: Boolean = priority.value >= Priority.CRITICAL.value,
    val timestampMs: Long = System.currentTimeMillis()
) : Comparable<SpeechEvent> {
    override fun compareTo(other: SpeechEvent): Int =
        other.priority.value.compareTo(this.priority.value)  // higher priority first
}
