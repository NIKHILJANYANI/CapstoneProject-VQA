package com.visualassistant.navigation

import com.visualassistant.world.TrackedObject

/**
 * Represents the current task the user has given to the assistant.
 */
sealed class NavigationTask {
    object None : NavigationTask()
    object ExploreMode : NavigationTask()
    object FindDoor : NavigationTask()
    object FindElevator : NavigationTask()
    object FindExit : NavigationTask()
    object FindReception : NavigationTask()
    data class FindRoom(val roomNumber: String) : NavigationTask()
    data class FindObject(val objectLabel: String) : NavigationTask()
    data class GuideToTarget(val target: TrackedObject) : NavigationTask()

    fun description(): String = when (this) {
        is None -> "No active task"
        is ExploreMode -> "Exploration mode"
        is FindDoor -> "Finding a door"
        is FindElevator -> "Finding the elevator"
        is FindExit -> "Finding the exit"
        is FindReception -> "Finding reception"
        is FindRoom -> "Finding Room $roomNumber"
        is FindObject -> "Finding $objectLabel"
        is GuideToTarget -> "Guiding to ${target.label}"
    }
}

/**
 * State of the current search operation (for turn-and-search behavior).
 */
enum class SearchState {
    SEARCHING,                  // Looking in current view
    ASKING_TO_ROTATE_RIGHT,    // Ask user to turn right
    ASKING_TO_ROTATE_LEFT,     // Ask user to turn left
    ASKING_TO_TURN_AROUND,     // Ask user to turn around
    GUIDING,                   // Target found, guiding user
    REACHED,                   // User has reached the target
    FAILED                     // Could not find the target
}

/**
 * TaskManager manages the current navigation task and its state.
 */
class TaskManager {

    private var _currentTask: NavigationTask = NavigationTask.None
    private var _searchState: SearchState = SearchState.SEARCHING
    private var _taskStartedAtMs: Long = 0L
    private var _lastSearchAnnounceMs: Long = 0L
    private var _rotationAttempts: Int = 0

    val currentTask: NavigationTask get() = _currentTask
    val searchState: SearchState get() = _searchState
    val hasActiveTask: Boolean get() = _currentTask !is NavigationTask.None

    /**
     * Set a new task. Resets search state.
     */
    fun setTask(task: NavigationTask) {
        _currentTask = task
        _searchState = SearchState.SEARCHING
        _taskStartedAtMs = System.currentTimeMillis()
        _lastSearchAnnounceMs = 0L
        _rotationAttempts = 0
    }

    /**
     * Clear current task.
     */
    fun clearTask() {
        _currentTask = NavigationTask.None
        _searchState = SearchState.SEARCHING
    }

    /**
     * Switch to exploration mode.
     */
    fun enterExplorationMode() {
        setTask(NavigationTask.ExploreMode)
    }

    /**
     * Mark the current task as reached.
     */
    fun markReached() {
        _searchState = SearchState.REACHED
    }

    /**
     * Called when the target is not visible — advances search state.
     * Returns a search guidance message or null if silent.
     */
    fun advanceSearchState(timeSinceLastAnnounceMs: Long): String? {
        val now = System.currentTimeMillis()
        // Don't announce too frequently
        if (now - _lastSearchAnnounceMs < 4000L) return null
        _lastSearchAnnounceMs = now

        return when (_searchState) {
            SearchState.SEARCHING -> {
                _searchState = SearchState.ASKING_TO_ROTATE_RIGHT
                _rotationAttempts++
                "I can't see the target from here. Please turn slowly to your right."
            }
            SearchState.ASKING_TO_ROTATE_RIGHT -> {
                _searchState = SearchState.ASKING_TO_ROTATE_LEFT
                _rotationAttempts++
                "Still searching. Please turn to your left."
            }
            SearchState.ASKING_TO_ROTATE_LEFT -> {
                _searchState = SearchState.ASKING_TO_TURN_AROUND
                _rotationAttempts++
                "I still can't see it. Please turn around slowly."
            }
            SearchState.ASKING_TO_TURN_AROUND -> {
                if (_rotationAttempts > 4) {
                    _searchState = SearchState.FAILED
                    "I was unable to locate ${_currentTask.description()}. Please ask for help or try again."
                } else {
                    _searchState = SearchState.SEARCHING
                    "Let me continue searching in this direction."
                }
            }
            else -> null
        }
    }

    /**
     * Task timeout check.
     */
    fun isTimedOut(): Boolean {
        if (_taskStartedAtMs == 0L) return false
        return System.currentTimeMillis() - _taskStartedAtMs > 60_000L
    }
}
