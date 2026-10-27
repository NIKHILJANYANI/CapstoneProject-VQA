package com.visualassistant.assistant.conversation

import com.visualassistant.assistant.intent.UserIntent
import com.visualassistant.navigation.NavigationTask
import com.visualassistant.world.TrackedObject

/**
 * Maintains short-term conversation context.
 *
 * Remembers:
 * - Last user intent
 * - Last mentioned object (for "take me there" follow-ups)
 * - Current active task
 */
class ConversationContext {

    var lastIntent: UserIntent? = null
        private set

    var lastMentionedObject: TrackedObject? = null
        private set

    var lastMentionedLabel: String? = null
        private set

    private val recentIntents = ArrayDeque<UserIntent>(10)

    /**
     * Update context after parsing a new intent.
     */
    fun updateWithIntent(intent: UserIntent) {
        lastIntent = intent
        recentIntents.addFirst(intent)
        if (recentIntents.size > 10) recentIntents.removeLast()
    }

    /**
     * Record an object that was mentioned in a response (for follow-ups).
     */
    fun setLastMentionedObject(obj: TrackedObject?) {
        lastMentionedObject = obj
        lastMentionedLabel = obj?.label
    }

    fun setLastMentionedLabel(label: String) {
        lastMentionedLabel = label
        lastMentionedObject = null
    }

    /**
     * Resolve "it", "there", "that" to the last mentioned object.
     */
    fun resolveReference(): TrackedObject? = lastMentionedObject

    /**
     * Build a NavigationTask from the last mentioned object (for "guide me to it").
     */
    fun buildGuideToLastMentioned(): NavigationTask? {
        lastMentionedObject?.let { return NavigationTask.GuideToTarget(it) }
        lastMentionedLabel?.let { label ->
            return when (label.lowercase()) {
                "door", "doorway", "exit" -> NavigationTask.FindDoor
                "elevator", "lift" -> NavigationTask.FindElevator
                else -> NavigationTask.FindObject(label)
            }
        }
        return null
    }

    fun clear() {
        lastIntent = null
        lastMentionedObject = null
        lastMentionedLabel = null
        recentIntents.clear()
    }
}
