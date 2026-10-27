package com.visualassistant.assistant.intent

/**
 * IntentParser maps free-form speech to structured UserIntents.
 *
 * Uses keyword/pattern matching — no LLM required. Handles natural phrasing variations.
 */
class IntentParser {

    /**
     * Parse speech text to a UserIntent.
     *
     * @param text Recognized speech (already lowercase-trimmed by caller)
     * @param lastIntent Previous intent for context (for "take me there" follow-ups)
     */
    fun parse(text: String, lastIntent: UserIntent? = null): UserIntent {
        val lower = text.lowercase().trim()
        val clean = lower
            .replace("'", "")
            .replace("?", "")
            .replace("!", "")
            .replace(".", "")
            .replace(",", "")
            .trim()

        // ── Stop ─────────────────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("stop", "stop guiding", "cancel", "quit", "never mind", "nevermind", "shut up", "pause"))) {
            return UserIntent.Stop
        }

        // ── Walking / Exploration Mode ────────────────────────────────────────────
        if (matchesAny(clean, listOf("walking mode", "start walking", "help me navigate",
                "exploration mode", "explore", "im walking", "i am walking", "navigation mode", "start navigation"))) {
            return UserIntent.StartWalkingMode
        }

        // ── Remember ─────────────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("remember this", "remember here", "remember the elevator",
                "save this location", "save location", "remember location", "save here"))) {
            return UserIntent.RememberCurrentLocation
        }

        // ── Read Text / OCR ───────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("read that", "read this", "what does that say",
                "what does this say", "what does the sign say", "read the sign",
                "what is written", "read it", "read", "read sign"))) {
            return UserIntent.ReadText
        }

        // ── Current Room Query ────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("what room", "which room", "am i near", "what floor",
                "where am i", "what is this room", "what room is this"))) {
            return UserIntent.AskCurrentRoom
        }

        // ── Guide to Exit ─────────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("guide me out", "guide me to the exit", "find the exit",
                "take me to the exit", "help me get out", "how do i get out",
                "exit the room", "i need to get outside", "find exit",
                "way out", "get out", "where is the exit", "wheres the exit", "exit", "door", "where is the door", "wheres the door"))) {
            return UserIntent.GuideToExit
        }

        // ── Guide to Elevator ─────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("guide me to the elevator", "find the elevator",
                "take me to the elevator", "where is the elevator", "wheres the elevator", "find elevator",
                "elevator", "find lift", "where is the lift", "wheres the lift", "lift"))) {
            return if (clean.contains("guide") || clean.contains("take me") || clean.contains("find")) {
                UserIntent.GuideToElevator
            } else {
                UserIntent.FindObject("elevator")
            }
        }

        // ── Guide to Room ─────────────────────────────────────────────────────────
        val roomGuideMatch = Regex("""(?:guide me to|take me to|find|go to)\s+(?:room\s*)?([A-Za-z]?\d{3,4}[A-Za-z]?)""")
            .find(clean)
        if (roomGuideMatch != null) {
            return UserIntent.GuideToRoom(roomGuideMatch.groupValues[1].uppercase())
        }

        // ── Query Memory about room ───────────────────────────────────────────────
        val roomQueryMatch = Regex("""(?:where is|where was|find|locate)\s+(?:room\s*)?([A-Za-z]?\d{3,4}[A-Za-z]?)""")
            .find(clean)
        if (roomQueryMatch != null) {
            return UserIntent.QueryMemory("room:${roomQueryMatch.groupValues[1].uppercase()}")
        }

        // ── Where did I see X (memory query) ────────────────────────────────────
        if (clean.contains("where did i see") || clean.contains("where was the") ||
            clean.contains("have you seen")) {
            return UserIntent.QueryMemory(clean)
        }

        // ── Guide to it / Take me there (follow-up) ──────────────────────────────
        if (matchesAny(clean, listOf("take me there", "guide me to it", "guide me there",
                "go there", "lead me there", "take me to it"))) {
            return UserIntent.GuideToLastMentioned
        }

        // ── Describe surroundings ("What's around me" / "whats around me") ─────────
        if (matchesAny(clean, listOf("what is around me", "whats around me", "around me", "around",
                "what is around", "whats around", "describe my surroundings", "describe surroundings",
                "what surrounds me", "what do you see", "look around", "describe everything",
                "what can you see", "what is near me", "whats near me"))) {
            return UserIntent.DescribeSurroundings
        }

        // ── Describe ahead / what is in front ────────────────────────────────────
        if (matchesAny(clean, listOf("what is in front of me", "whats in front of me", "in front of me",
                "what is in front", "whats in front", "what is ahead", "whats ahead", "what is before me",
                "whats before me", "describe what is in front", "ahead", "in front"))) {
            return UserIntent.DescribeScene
        }

        // ── Detailed description (Cloud Vision) ──────────────────────────────────
        if (matchesAny(clean, listOf("describe in detail", "look closely", "what exactly", 
                "describe deeply", "detailed description", "tell me everything"))) {
            return UserIntent.DetailedDescription
        }

        // ── Describe left ─────────────────────────────────────────────────────────
        if (clean.contains("left") && matchesAny(clean, listOf("what is on my left", "whats on my left",
                "what is to my left", "whats to my left", "describe left", "what is there on the left",
                "on my left", "to my left", "on the left"))) {
            return UserIntent.DescribeLeft
        }

        // ── Describe right ────────────────────────────────────────────────────────
        if (clean.contains("right") && matchesAny(clean, listOf("what is on my right", "whats on my right",
                "what is to my right", "whats to my right", "describe right", "what is there on the right",
                "on my right", "to my right", "on the right"))) {
            return UserIntent.DescribeRight
        }

        // ── Hazard check ──────────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("anything dangerous", "any danger", "is it safe",
                "any hazards", "danger nearby", "anything sharp", "fire",
                "any obstacles", "is there an obstacle", "danger", "hazard", "safe"))) {
            return UserIntent.CheckHazards
        }

        // ── People ────────────────────────────────────────────────────────────────
        if (matchesAny(clean, listOf("is anyone", "is there anyone", "is there a person",
                "any people", "anyone approaching", "any person near", "people nearby", "who is there", "someone approaching"))) {
            return UserIntent.CheckForPeople
        }

        // ── Specific table/surface check ──────────────────────────────────────────
        if (clean.contains("table") || clean.contains("desk")) {
            return UserIntent.FindObject("table")
        }

        // ── Find generic object ───────────────────────────────────────────────────
        val findMatch = Regex("""(?:find|locate|where is|wheres|can you see|what is on the)\s+(?:a\s+|the\s+)?(\w+)""")
            .find(clean)
        if (findMatch != null) {
            val objectLabel = findMatch.groupValues[1]
            if (objectLabel.length > 2 && objectLabel !in setOf("me", "my", "the", "this")) {
                return UserIntent.FindObject(objectLabel)
            }
        }

        // ── Guide to object ───────────────────────────────────────────────────────
        val guideMatch = Regex("""(?:guide me to|take me to|lead me to)\s+(?:the\s+)?(\w+)""")
            .find(clean)
        if (guideMatch != null) {
            val objectLabel = guideMatch.groupValues[1]
            if (objectLabel !in setOf("exit", "elevator", "room", "door")) {
                return UserIntent.GuideTo(objectLabel)
            }
        }

        // ── Describe scene (fallback for simple "what do I see" questions) ────────
        if (matchesAny(clean, listOf("what", "describe", "tell me", "see", "look"))) {
            return UserIntent.DescribeScene
        }

        return UserIntent.Unknown(text)
    }

    private fun matchesAny(input: String, patterns: List<String>): Boolean =
        patterns.any { input.contains(it) }
}
