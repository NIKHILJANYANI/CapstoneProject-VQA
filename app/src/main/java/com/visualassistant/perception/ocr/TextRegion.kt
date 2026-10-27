package com.visualassistant.perception.ocr

import android.graphics.RectF

/**
 * A region of text detected by the OCR engine.
 */
data class TextRegion(
    val text: String,
    val boundingBox: RectF,     // normalized [0,1]
    val confidence: Float = 1.0f,
    val isRoomNumber: Boolean = false,
    val isSign: Boolean = false
) {
    /**
     * Whether this text is likely a room number (e.g., "204", "Room 204", "B204").
     */
    companion object {
        private val ROOM_PATTERN = Regex("""(?i)(room\s*)?([A-Z]?\d{3,4}[A-Z]?)""")
        private val SIGN_KEYWORDS = listOf(
            "exit", "elevator", "stairs", "reception", "cafeteria",
            "library", "restroom", "bathroom", "emergency", "entrance",
            "lobby", "floor", "lab", "office"
        )

        fun classify(text: String, boundingBox: RectF): TextRegion {
            val cleaned = text.trim()
            val isRoom = ROOM_PATTERN.containsMatchIn(cleaned)
            val isSign = SIGN_KEYWORDS.any { cleaned.contains(it, ignoreCase = true) }
            return TextRegion(
                text = cleaned,
                boundingBox = boundingBox,
                isRoomNumber = isRoom,
                isSign = isSign || isRoom
            )
        }
    }
}
