package com.visualassistant.memory

import android.content.Context
import com.visualassistant.utils.Constants
import com.visualassistant.world.TrackedObject

/**
 * SpatialMemory — high-level API for storing and retrieving environmental memory.
 *
 * Stores:
 * - Room numbers detected by OCR
 * - Important landmarks (elevator, door, exit, reception)
 * - User-remembered locations
 *
 * Uses an in-memory map for fast access, backed by Room database for persistence.
 */
class SpatialMemory(context: Context) {

    // In-memory store: label → list of entries (most recent first)
    private val memoryMap = mutableMapOf<String, MutableList<MemoryEntry>>()
    private val roomMemory = mutableMapOf<String, MemoryEntry>()  // roomNumber → entry

    // TODO: Room DB integration for persistence across sessions
    // private val db = MemoryDatabase.getDatabase(context)

    /**
     * Store a detected landmark (elevator, door, exit, etc.)
     */
    fun storeLandmark(
        label: String,
        relativePosition: String,
        contextDescription: String? = null,
        floor: Int? = null
    ) {
        val entry = MemoryEntry(
            label = label,
            relativePosition = relativePosition,
            contextDescription = contextDescription,
            floor = floor
        )
        memoryMap.getOrPut(label.lowercase()) { mutableListOf() }.apply {
            add(0, entry)  // most recent first
            if (size > 10) removeLast()  // cap entries per label
        }
    }

    /**
     * Store a detected room number.
     */
    fun storeRoom(
        roomNumber: String,
        relativePosition: String,
        nearbyLandmark: String? = null,
        floor: Int? = null
    ) {
        val contextDesc = if (nearbyLandmark != null) "near $nearbyLandmark" else null
        val entry = MemoryEntry(
            label = "room",
            text = roomNumber,
            relativePosition = relativePosition,
            contextDescription = contextDesc,
            floor = floor
        )
        roomMemory[roomNumber.uppercase()] = entry
        storeLandmark("room $roomNumber", relativePosition, contextDesc, floor)
    }

    /**
     * Store the current location as user-bookmarked.
     */
    fun storeCurrentLocation(trackedObject: TrackedObject?) {
        if (trackedObject != null) {
            storeLandmark(
                label = trackedObject.label,
                relativePosition = trackedObject.position.toDescription(),
                contextDescription = "User-bookmarked"
            )
        }
    }

    /**
     * Find the most recent memory entry for a label.
     */
    fun findRecent(label: String): MemoryEntry? =
        memoryMap[label.lowercase()]?.firstOrNull()

    /**
     * Find a room entry by room number.
     */
    fun findRoom(roomNumber: String): MemoryEntry? =
        roomMemory[roomNumber.uppercase()]

    /**
     * Get the most recently seen room number.
     */
    fun getMostRecentRoom(): String? {
        return roomMemory.entries
            .maxByOrNull { it.value.timestampMs }
            ?.key
    }

    /**
     * Get all stored landmark labels.
     */
    fun getAllLandmarks(): List<String> = memoryMap.keys.toList()

    /**
     * Clear all memory.
     */
    fun clear() {
        memoryMap.clear()
        roomMemory.clear()
    }

    /**
     * Summarize all memory for debugging.
     */
    fun toDebugString(): String = buildString {
        append("SpatialMemory:\n")
        append("  Rooms: ${roomMemory.keys.joinToString(", ")}\n")
        append("  Landmarks: ${memoryMap.keys.joinToString(", ")}\n")
    }

    // Extension for position description
    private fun com.visualassistant.world.SpatialPosition.toDescription() = when (this) {
        com.visualassistant.world.SpatialPosition.LEFT -> "to your left"
        com.visualassistant.world.SpatialPosition.CENTER -> "ahead"
        com.visualassistant.world.SpatialPosition.RIGHT -> "to your right"
        else -> "nearby"
    }
}
