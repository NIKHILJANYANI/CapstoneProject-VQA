package com.visualassistant.assistant

import android.content.Context
import android.util.Log
import com.visualassistant.assistant.conversation.ConversationContext
import com.visualassistant.assistant.intent.IntentParser
import com.visualassistant.assistant.intent.UserIntent
import com.visualassistant.assistant.priority.Priority
import com.visualassistant.assistant.priority.PriorityEngine
import com.visualassistant.assistant.priority.SpeechEvent
import com.visualassistant.assistant.reasoning.ResponseGenerator
import com.visualassistant.memory.SpatialMemory
import com.visualassistant.navigation.NavigationTask
import com.visualassistant.navigation.TaskManager
import com.visualassistant.perception.hazard.HazardDetector
import com.visualassistant.perception.hazard.HazardEvent
import com.visualassistant.speech.text_to_speech.TTSManager
import com.visualassistant.world.TrackedObject
import com.visualassistant.world.WorldModel

/**
 * AssistantEngine is the top-level coordinator.
 *
 * It:
 * 1. Receives voice input → parses intent → updates task/context
 * 2. Receives WorldModel updates → generates proactive narrations
 * 3. Routes everything through the PriorityEngine → TTSManager
 *
 * This is the "brain" of the assistant.
 */
class AssistantEngine(
    private val context: Context,
    private val ttsManager: TTSManager,
    private val spatialMemory: SpatialMemory
) {
    private val intentParser = IntentParser()
    private val priorityEngine = PriorityEngine()
    private val responseGenerator = ResponseGenerator()
    private val taskManager = TaskManager()
    private val conversationContext = ConversationContext()
    private val hazardDetector = HazardDetector()

    private var currentWorldModel: WorldModel = WorldModel()
    private var isActive = false

    // Proactive narration cooldown tracking (cooldownKey → last announced ms)
    private val proactiveCooldownMap = mutableMapOf<String, Long>()

    /**
     * Start the assistant.
     */
    fun start() {
        isActive = true
        taskManager.enterExplorationMode()
        ttsManager.speakNow("Visual assistant started. How can I help you?")
        Log.d(TAG, "AssistantEngine started")
    }

    /**
     * Stop the assistant.
     */
    fun stop() {
        isActive = false
        taskManager.clearTask()
        conversationContext.clear()
        ttsManager.stopSpeaking()
        ttsManager.speakNow("Visual assistant stopped.")
        Log.d(TAG, "AssistantEngine stopped")
    }

    var onCloudDescriptionRequested: (() -> Unit)? = null

    /**
     * Called when the user speaks a command.
     *
     * @param spokenText The recognized speech text
     */
    fun onUserSpeech(spokenText: String) {
        if (!isActive) return
        Log.d(TAG, "User said: $spokenText")

        val intent = intentParser.parse(spokenText, conversationContext.lastIntent)
        conversationContext.updateWithIntent(intent)

        // Handle task-setting intents
        updateTaskFromIntent(intent)

        // Generate response
        val response = responseGenerator.generateResponse(
            intent = intent,
            world = currentWorldModel,
            currentTask = taskManager.currentTask,
            memory = spatialMemory,
            lastMentionedLabel = conversationContext.lastMentionedLabel
        )

        // Update conversation context with mentioned objects
        updateContextFromResponse(intent, currentWorldModel)

        // Speak response immediately
        if (response.isNotBlank()) {
            ttsManager.speakNow(response)
        }

        // Handle cloud fallback
        if (intent is UserIntent.DetailedDescription) {
            onCloudDescriptionRequested?.invoke()
        }

        // Store memory for remember commands
        if (intent is UserIntent.RememberCurrentLocation) {
            val nearestObject = currentWorldModel.trackedObjects.firstOrNull()
            spatialMemory.storeCurrentLocation(nearestObject)
            if (nearestObject != null) {
                ttsManager.speak("I've remembered the location of ${nearestObject.label}.", Priority.HIGH, "remember")
            } else {
                ttsManager.speak("Location remembered.", Priority.HIGH, "remember")
            }
        }
    }

    /**
     * Called when the WorldModel is updated by the PerceptionPipeline.
     * Generates proactive narrations based on current task and priorities.
     *
     * @param worldModel Updated world model
     * @param hazards Current hazard events
     */
    fun onWorldModelUpdated(worldModel: WorldModel, hazards: List<HazardEvent> = emptyList(), isListening: Boolean = false) {
        if (!isActive) return
        currentWorldModel = worldModel

        if (isListening) return // Do not proactive announce while listening to user

        // Step 1: Check for hazards first (always highest priority)
        if (hazards.isNotEmpty()) {
            val topHazard = hazards.maxByOrNull { it.type.priority }!!
            val key = "hazard_${topHazard.type.name}_${topHazard.position.name}"
            if (canAnnounce(key, 8000L)) {
                val hazardPriority = priorityEngine.computeHazardPriority(topHazard)
                val hazardText = responseGenerator.generateHazardWarning(topHazard, worldModel)
                ttsManager.speak(
                    SpeechEvent(
                        text = hazardText,
                        priority = hazardPriority,
                        cooldownKey = key,
                        cooldownMs = 8000L,
                        shouldInterrupt = hazardPriority == Priority.CRITICAL
                    )
                )
                markAnnounced(key)
            }
        }

        // Step 2: Task-based guidance
        if (taskManager.hasActiveTask && taskManager.currentTask !is NavigationTask.ExploreMode) {
            handleTaskNavigation(worldModel)
        }

        // Step 3: Proactive narration for exploration mode
        if (taskManager.currentTask is NavigationTask.ExploreMode) {
            handleExplorationMode(worldModel, hazards)
        }
    }

    /**
     * Handle task-specific navigation guidance.
     */
    private fun handleTaskNavigation(world: WorldModel) {
        val task = taskManager.currentTask

        when (task) {
            is NavigationTask.FindDoor -> {
                val door = world.findFirst("door") ?: world.findFirst("doorway")
                if (door != null) {
                    announceNavigationTarget(door, "door", task)
                } else {
                    val searchMsg = taskManager.advanceSearchState(
                        System.currentTimeMillis() - (proactiveCooldownMap["task_search"] ?: 0L)
                    )
                    searchMsg?.let {
                        ttsManager.speak(it, Priority.MEDIUM, "task_search_guidance", 4000L)
                        markAnnounced("task_search")
                    }
                }
            }
            is NavigationTask.FindElevator -> {
                val elevator = world.findFirst("elevator")
                if (elevator != null) {
                    announceNavigationTarget(elevator, "elevator", task)
                } else {
                    val searchMsg = taskManager.advanceSearchState(
                        System.currentTimeMillis() - (proactiveCooldownMap["task_search"] ?: 0L)
                    )
                    searchMsg?.let {
                        ttsManager.speak(it, Priority.MEDIUM, "task_search_guidance", 4000L)
                        markAnnounced("task_search")
                    }
                }
            }
            is NavigationTask.FindRoom -> {
                val roomText = world.detectedText.find { it.text.contains(task.roomNumber, ignoreCase = true) }
                if (roomText != null) {
                    if (canAnnounce("room_found_${task.roomNumber}", 5000L)) {
                        ttsManager.speak(
                            "Room ${task.roomNumber} is ahead.",
                            Priority.CRITICAL, "room_found_${task.roomNumber}", 5000L
                        )
                        taskManager.markReached()
                        markAnnounced("room_found_${task.roomNumber}")
                    }
                }
            }
            is NavigationTask.FindExit -> {
                val door = world.findFirst("door") ?: world.findFirst("exit")
                if (door != null) {
                    announceNavigationTarget(door, "exit", task)
                }
            }
            is NavigationTask.GuideToTarget -> {
                val found = world.findByLabel(task.target.label).firstOrNull()
                if (found != null) {
                    announceNavigationTarget(found, task.target.label, task)
                }
            }
            else -> {}
        }
    }

    /**
     * Handle proactive narration in exploration mode.
     */
    private fun handleExplorationMode(world: WorldModel, hazards: List<HazardEvent>) {
        // Get high-priority events sorted by importance
        val prioritized = priorityEngine.filterAndSortForAnnouncement(
            world.trackedObjects,
            taskManager.currentTask,
            minimumPriority = Priority.HIGH  // Only HIGH+ events get proactive narration in exploration
        )

        val narration = responseGenerator.generateProactiveNarration(prioritized, hazards, world)
        if (narration != null) {
            val key = "exploration_narration_${narration.take(20)}"
            if (canAnnounce(key, 5000L)) {
                ttsManager.speak(narration, Priority.HIGH, key, 5000L)
                markAnnounced(key)
            }
        }
    }

    /**
     * Announce a found navigation target and provide direction.
     */
    private fun announceNavigationTarget(obj: TrackedObject, targetLabel: String, task: NavigationTask) {
        val key = "nav_target_${targetLabel}_${obj.position.name}"
        if (!canAnnounce(key, 4000L)) return

        val distDesc = obj.lastDetectedObject.distanceDescription
        val posDesc = obj.position.toDescription()

        when (obj.lastDetectedObject.distanceCategory) {
            com.visualassistant.perception.object_detection.DistanceCategory.VERY_CLOSE -> {
                ttsManager.speak("You've reached the $targetLabel.", Priority.CRITICAL, key, 5000L)
                taskManager.markReached()
            }
            else -> {
                val dirInstruction = obj.position.toDirectionInstruction()
                ttsManager.speak(
                    "I can see the $targetLabel $posDesc, $distDesc. $dirInstruction.",
                    Priority.HIGH, key, 4000L
                )
            }
        }
        markAnnounced(key)

        // Update conversation context
        conversationContext.setLastMentionedObject(obj)
    }

    private fun updateTaskFromIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.GuideToExit -> taskManager.setTask(NavigationTask.FindExit)
            is UserIntent.GuideToElevator -> taskManager.setTask(NavigationTask.FindElevator)
            is UserIntent.GuideToRoom -> taskManager.setTask(NavigationTask.FindRoom(intent.roomNumber))
            is UserIntent.GuideTo -> taskManager.setTask(NavigationTask.FindObject(intent.label))
            is UserIntent.FindObject -> {}  // Don't change task for simple find queries
            is UserIntent.StartWalkingMode -> taskManager.enterExplorationMode()
            is UserIntent.Stop -> taskManager.clearTask()
            is UserIntent.GuideToLastMentioned -> {
                val newTask = conversationContext.buildGuideToLastMentioned()
                if (newTask != null) taskManager.setTask(newTask)
            }
            else -> {}
        }
    }

    private fun updateContextFromResponse(intent: UserIntent, world: WorldModel) {
        when (intent) {
            is UserIntent.FindObject -> {
                val found = world.findFirst(intent.label)
                if (found != null) conversationContext.setLastMentionedObject(found)
                else conversationContext.setLastMentionedLabel(intent.label)
            }
            is UserIntent.GuideToElevator -> conversationContext.setLastMentionedLabel("elevator")
            is UserIntent.GuideToExit -> conversationContext.setLastMentionedLabel("exit")
            is UserIntent.DescribeScene -> {
                world.objectsAhead.firstOrNull()?.let { conversationContext.setLastMentionedObject(it) }
            }
            else -> {}
        }
    }

    private fun canAnnounce(key: String, cooldownMs: Long): Boolean {
        val last = proactiveCooldownMap[key] ?: 0L
        return System.currentTimeMillis() - last >= cooldownMs
    }

    private fun markAnnounced(key: String) {
        proactiveCooldownMap[key] = System.currentTimeMillis()
    }

    // Position description extension
    private fun com.visualassistant.world.SpatialPosition.toDescription() = when (this) {
        com.visualassistant.world.SpatialPosition.LEFT -> "to your left"
        com.visualassistant.world.SpatialPosition.CENTER -> "ahead"
        com.visualassistant.world.SpatialPosition.RIGHT -> "to your right"
        else -> "nearby"
    }

    private fun com.visualassistant.world.SpatialPosition.toDirectionInstruction() = when (this) {
        com.visualassistant.world.SpatialPosition.LEFT -> "Turn slightly left"
        com.visualassistant.world.SpatialPosition.CENTER -> "Walk forward"
        com.visualassistant.world.SpatialPosition.RIGHT -> "Turn slightly right"
        else -> "Continue"
    }

    companion object {
        private const val TAG = "AssistantEngine"
    }
}
