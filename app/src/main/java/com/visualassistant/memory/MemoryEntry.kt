package com.visualassistant.memory

/**
 * An entry in spatial memory.
 */
data class MemoryEntry(
    val id: Long = 0,
    val label: String,           // "elevator", "room 204", "exit"
    val text: String? = null,    // Original text (for room numbers/signs)
    val relativePosition: String, // "to your left", "ahead", "near elevator"
    val floor: Int? = null,
    val contextDescription: String? = null,  // e.g., "near elevator on 2nd floor"
    val timestampMs: Long = System.currentTimeMillis()
)
