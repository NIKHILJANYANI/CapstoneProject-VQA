package com.visualassistant.assistant.intent

/**
 * Sealed class representing all recognized user intents.
 *
 * The IntentParser maps free-form speech to these structured intents.
 */
sealed class UserIntent {

    // ─── Queries ────────────────────────────────────────────────────────────────
    /** "What is in front of me?" / "What do you see?" */
    object DescribeScene : UserIntent()

    /** "What is on my left?" */
    object DescribeLeft : UserIntent()

    /** "What is on my right?" */
    object DescribeRight : UserIntent()

    /** "What is around me?" */
    object DescribeSurroundings : UserIntent()

    /** "Describe in detail." / "Look deeply." */
    object DetailedDescription : UserIntent()

    /** "Is anything dangerous nearby?" */
    object CheckHazards : UserIntent()

    /** "Is there a person approaching?" */
    object CheckForPeople : UserIntent()

    // ─── Object queries ─────────────────────────────────────────────────────────
    /** "Where is the door?" / "Can you find the elevator?" */
    data class FindObject(val label: String) : UserIntent()

    /** "What room am I near?" */
    object AskCurrentRoom : UserIntent()

    /** "What does that sign say?" / "Read that." */
    object ReadText : UserIntent()

    // ─── Task commands ───────────────────────────────────────────────────────────
    /** "Guide me out of the room." / "Help me find the exit." */
    object GuideToExit : UserIntent()

    /** "Guide me to the elevator." */
    object GuideToElevator : UserIntent()

    /** "Guide me to Room 204." */
    data class GuideToRoom(val roomNumber: String) : UserIntent()

    /** "Guide me to [object]." */
    data class GuideTo(val label: String) : UserIntent()

    // ─── Mode commands ───────────────────────────────────────────────────────────
    /** "Start walking mode." / "I'm walking, help me navigate." */
    object StartWalkingMode : UserIntent()

    /** "Stop." / "Stop guiding me." */
    object Stop : UserIntent()

    // ─── Memory commands ─────────────────────────────────────────────────────────
    /** "Remember this location." / "Remember the elevator." */
    object RememberCurrentLocation : UserIntent()

    /** "Where did I see the elevator?" / "Where is Room 204?" — from memory */
    data class QueryMemory(val query: String) : UserIntent()

    // ─── Conversation follow-up ──────────────────────────────────────────────────
    /** "Take me there." / "Guide me to it." — refers to previous context */
    object GuideToLastMentioned : UserIntent()

    /** "Tell me more." */
    object MoreDetail : UserIntent()

    // ─── Unknown ────────────────────────────────────────────────────────────────
    data class Unknown(val rawText: String) : UserIntent()
}
