package com.visualassistant.assistant.reasoning

import com.visualassistant.assistant.intent.UserIntent
import com.visualassistant.assistant.priority.Priority
import com.visualassistant.memory.SpatialMemory
import com.visualassistant.navigation.NavigationTask
import com.visualassistant.perception.hazard.HazardEvent
import com.visualassistant.world.TrackedObject
import com.visualassistant.world.WorldModel
import com.visualassistant.world.SpatialPosition
import com.visualassistant.perception.object_detection.DistanceCategory

/**
 * ResponseGenerator converts WorldModel + UserIntent + Memory into natural language responses.
 *
 * This is a template-based NLG (Natural Language Generation) engine — no LLM required.
 * Responses are designed to be concise, clear, and useful for visually impaired users.
 */
class ResponseGenerator {

    /**
     * Generate a response to a user question/command given the current world state.
     */
    fun generateResponse(
        intent: UserIntent,
        world: WorldModel,
        currentTask: NavigationTask,
        memory: SpatialMemory,
        lastMentionedLabel: String?
    ): String {
        return when (intent) {
            is UserIntent.DescribeScene -> describeScene(world)
            is UserIntent.DescribeLeft -> describeDirection(world, SpatialPosition.LEFT)
            is UserIntent.DescribeRight -> describeDirection(world, SpatialPosition.RIGHT)
            is UserIntent.DescribeSurroundings -> describeSurroundings(world)
            is UserIntent.CheckHazards -> checkHazards(world)
            is UserIntent.CheckForPeople -> checkForPeople(world)
            is UserIntent.FindObject -> findObject(intent.label, world, memory)
            is UserIntent.AskCurrentRoom -> askCurrentRoom(world, memory)
            is UserIntent.ReadText -> readText(world)
            is UserIntent.GuideToExit -> "Searching for an exit. ${findObject("door", world, memory)}"
            is UserIntent.GuideToElevator -> "Searching for the elevator. ${findObject("elevator", world, memory)}"
            is UserIntent.GuideToRoom -> guideToRoom(intent.roomNumber, world, memory)
            is UserIntent.GuideTo -> "Looking for ${intent.label}. ${findObject(intent.label, world, memory)}"
            is UserIntent.StartWalkingMode -> "Walking mode started. I'll alert you to obstacles and important objects."
            is UserIntent.Stop -> "Stopping. Let me know if you need anything."
            is UserIntent.RememberCurrentLocation -> "I'll remember your current location."
            is UserIntent.QueryMemory -> queryMemory(intent.query, memory, world)
            is UserIntent.GuideToLastMentioned ->
                if (lastMentionedLabel != null) {
                    "Looking for $lastMentionedLabel. ${findObject(lastMentionedLabel, world, memory)}"
                } else {
                    "I'm not sure what to guide you to. What would you like to find?"
                }
            is UserIntent.MoreDetail -> describeSurroundings(world)
            is UserIntent.DetailedDescription -> "Analyzing scene deeply..."
            is UserIntent.Unknown -> handleUnknown(intent.rawText, world)
        }
    }

    /**
     * Generate a proactive narration (not triggered by user) for exploration mode.
     * Returns null if nothing important enough to say.
     */
    fun generateProactiveNarration(
        importantEvents: List<Pair<TrackedObject, Priority>>,
        hazards: List<HazardEvent>,
        world: WorldModel
    ): String? {
        if (hazards.isNotEmpty()) {
            // Hazards take absolute precedence
            val topHazard = hazards.maxByOrNull { it.type.priority }!!
            return generateHazardWarning(topHazard, world)
        }

        if (importantEvents.isEmpty()) return null

        val parts = mutableListOf<String>()

        // Check for obstacles in path first
        val pathObstacles = importantEvents.filter { (obj, _) -> obj.isInPath }
        if (pathObstacles.isNotEmpty()) {
            val top = pathObstacles.first()
            parts.add(describeObjectBriefly(top.first, pathContext = true))
        }

        // Then approaching objects
        val approaching = importantEvents.filter { (obj, priority) ->
            obj.isApproaching && priority.value >= Priority.HIGH.value
        }
        approaching.take(1).forEach { (obj, _) ->
            if (!obj.isInPath) {  // already covered by path obstacles
                parts.add("A ${obj.label} is approaching from ${obj.position.toDescription()}")
            }
        }

        // Then navigation targets (door, stairs, elevator) if HIGH+
        val navTargets = importantEvents.filter { (obj, priority) ->
            priority.value >= Priority.HIGH.value && obj.label in setOf("door", "stairs", "elevator", "exit")
            && !obj.isInPath   // path obstacles already covered
        }
        navTargets.take(1).forEach { (obj, _) ->
            parts.add(describeObjectBriefly(obj, pathContext = false))
        }

        if (parts.isEmpty()) return null
        return parts.joinToString(". ") + "."
    }

    /**
     * Generate hazard warning with actionable safety guidance.
     */
    fun generateHazardWarning(hazard: HazardEvent, world: WorldModel): String {
        val baseText = hazard.toSpeechText()
        if (!hazard.isInPath) return baseText

        // If hazard is in path, suggest a safe direction
        // Check if left/right are generally clear (no very close objects)
        val isLeftClear = world.objectsOnLeft.none { it.distanceCategory == DistanceCategory.VERY_CLOSE }
        val isRightClear = world.objectsOnRight.none { it.distanceCategory == DistanceCategory.VERY_CLOSE }

        val actionText = when {
            isLeftClear && isRightClear -> "Move to either side."
            isLeftClear -> "Move slightly left."
            isRightClear -> "Move slightly right."
            else -> "Proceed with caution."
        }

        return "$baseText $actionText"
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    private fun describeScene(world: WorldModel): String {
        val ahead = world.objectsAhead
        if (ahead.isEmpty()) {
            if (world.trackedObjects.isNotEmpty()) {
                val other = world.trackedObjects.take(3).map {
                    "${it.label} ${it.position.toDescription()}, ${it.lastDetectedObject.distanceDescription}"
                }
                return "The path directly ahead is clear. Nearby: ${other.joinToString(", ")}."
            }
            return "The path ahead appears clear."
        }
        val important = ahead.filter { it.label !in lowPriorityObjects }
        return if (important.isEmpty()) {
            "There is a ${ahead.first().label} ahead, ${ahead.first().lastDetectedObject.distanceDescription}."
        } else {
            val descriptions = important.take(3).map { describeObjectBriefly(it, pathContext = true) }
            descriptions.joinToString(". ")
        }
    }

    private fun describeDirection(world: WorldModel, direction: SpatialPosition): String {
        val objects = when (direction) {
            SpatialPosition.LEFT -> world.objectsOnLeft
            SpatialPosition.RIGHT -> world.objectsOnRight
            else -> world.objectsAhead
        }
        val dirText = direction.toDescription()
        if (objects.isEmpty()) return "I don't see anything significant $dirText."
        val descriptions = objects.take(3).map { "${it.label} ${it.lastDetectedObject.distanceDescription}" }
        return "To your ${direction.name.lowercase()}: ${descriptions.joinToString(", ")}."
    }

    private fun describeSurroundings(world: WorldModel): String {
        if (world.isEmpty) return "I don't currently see anything in your surroundings."

        val parts = mutableListOf<String>()

        val ahead = world.objectsAhead.take(2)
        val left = world.objectsOnLeft.take(2)
        val right = world.objectsOnRight.take(2)

        if (ahead.isNotEmpty()) {
            parts.add("Ahead: ${ahead.joinToString(", ") { "${it.label} (${it.lastDetectedObject.distanceDescription})" }}")
        }
        if (left.isNotEmpty()) {
            parts.add("To your left: ${left.joinToString(", ") { "${it.label} (${it.lastDetectedObject.distanceDescription})" }}")
        }
        if (right.isNotEmpty()) {
            parts.add("To your right: ${right.joinToString(", ") { "${it.label} (${it.lastDetectedObject.distanceDescription})" }}")
        }

        if (parts.isEmpty() && world.trackedObjects.isNotEmpty()) {
            val list = world.trackedObjects.take(3).map { "${it.label} ${it.position.toDescription()}" }
            return "I see: ${list.joinToString(", ")}."
        }

        return if (parts.isEmpty()) "The area around you appears clear."
        else parts.joinToString(". ") + "."
    }

    private fun checkHazards(world: WorldModel): String {
        val hazards = world.hazards
        return if (hazards.isEmpty()) {
            "I don't currently detect any dangerous objects. Please remain cautious."
        } else {
            val hazardDescriptions = hazards.take(3).map {
                "${it.label} ${it.position.toDescription()}"
            }
            "Caution. I detect: ${hazardDescriptions.joinToString(", ")}."
        }
    }

    private fun checkForPeople(world: WorldModel): String {
        val people = world.findByLabel("person")
        return when {
            people.isEmpty() -> "I don't currently see any people nearby."
            people.size == 1 -> {
                val p = people.first()
                if (p.isApproaching) "A person is approaching from ${p.position.toDescription()}."
                else "There is one person ${p.position.toDescription()}, ${p.lastDetectedObject.distanceDescription}."
            }
            else -> {
                val approaching = people.count { it.isApproaching }
                if (approaching > 0) "There are ${people.size} people nearby. ${approaching} ${if (approaching == 1) "is" else "are"} approaching."
                else "There are ${people.size} people around you."
            }
        }
    }

    private fun findObject(label: String, world: WorldModel, memory: SpatialMemory): String {
        // Check current view first
        val found = world.findFirst(label)
        if (found != null) {
            return "Yes. There is a ${found.label} ${found.position.toDescription()}, ${found.lastDetectedObject.distanceDescription}."
        }

        // Check memory
        val memEntry = memory.findRecent(label)
        if (memEntry != null) {
            return "I previously detected a $label ${memEntry.relativePosition}. I can't currently see it from this direction."
        }

        return "I can't currently see a $label from this direction."
    }

    private fun askCurrentRoom(world: WorldModel, memory: SpatialMemory): String {
        // Check detected text for room numbers
        val roomText = world.detectedText.find { it.isRoomNumber }
        if (roomText != null) return "I can see ${roomText.text} nearby."

        val recentRoom = memory.getMostRecentRoom()
        return if (recentRoom != null) {
            "The last room number I detected was ${recentRoom}."
        } else {
            "I haven't detected a room number recently. Look for room signs on walls or doors."
        }
    }

    private fun readText(world: WorldModel): String {
        val texts = world.detectedText
        return when {
            texts.isEmpty() -> "I don't currently detect any readable text. Please point the camera at a sign or text."
            texts.size == 1 -> "The text reads: ${texts.first().text}."
            else -> {
                val textList = texts.take(3).joinToString("; ") { it.text }
                "I can see the following text: $textList."
            }
        }
    }

    private fun guideToRoom(roomNumber: String, world: WorldModel, memory: SpatialMemory): String {
        // Check memory
        val memEntry = memory.findRoom(roomNumber)
        if (memEntry != null) {
            return "Room $roomNumber was previously detected ${memEntry.relativePosition}. I'll keep searching."
        }
        return "Searching for Room $roomNumber. I'll let you know when I spot it. Walk slowly and I'll guide you."
    }

    private fun queryMemory(query: String, memory: SpatialMemory, world: WorldModel): String {
        val roomMatch = Regex("""room:([A-Z0-9]+)""").find(query)
        if (roomMatch != null) {
            val roomNum = roomMatch.groupValues[1]
            val entry = memory.findRoom(roomNum)
            return if (entry != null) {
                "Room $roomNum was previously detected ${entry.relativePosition}."
            } else {
                "I don't have Room $roomNum in my memory. I'll look out for it."
            }
        }

        // Generic memory query
        val keywords = listOf("elevator", "door", "exit", "reception", "cafeteria", "library")
        for (kw in keywords) {
            if (query.contains(kw)) {
                val entry = memory.findRecent(kw)
                if (entry != null) {
                    return "I previously detected a $kw ${entry.relativePosition}."
                }
            }
        }
        return "I don't have that information in my memory."
    }

    private fun handleUnknown(rawText: String, world: WorldModel): String {
        return "I didn't understand that. You can ask me what's ahead, find the exit, " +
                "read a sign, or say 'start walking mode'."
    }

    private fun describeObjectBriefly(obj: TrackedObject, pathContext: Boolean): String {
        val label = obj.label
        val pos = obj.position.toDescription()
        val dist = obj.lastDetectedObject.distanceDescription

        return when {
            label == "stairs" && pathContext -> "Caution. Stairs ahead $pos"
            label == "stairs" -> "Stairs are $pos"
            label == "person" && obj.isApproaching -> "A person approaching from $pos"
            label == "person" -> "A person is $pos, $dist"
            label in setOf("door", "doorway") -> "There is a door $pos, $dist"
            label == "elevator" -> "An elevator is $pos"
            obj.isInPath && obj.distanceCategory == DistanceCategory.VERY_CLOSE ->
                "Caution. ${label.replaceFirstChar { it.uppercase() }} very close $pos"
            else -> "A $label is $pos"
        }
    }

    private val lowPriorityObjects = setOf(
        "chair", "table", "laptop", "bottle", "book", "plant",
        "monitor", "keyboard", "cup", "vase", "clock", "mouse"
    )
}
