package com.visualassistant.assistant.priority

import android.graphics.RectF
import com.visualassistant.navigation.NavigationTask
import com.visualassistant.perception.object_detection.DetectedObject
import com.visualassistant.world.SpatialPosition
import com.visualassistant.world.TrackedObject
import com.visualassistant.perception.object_detection.DistanceCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PriorityEngineTest {

    private lateinit var engine: PriorityEngine

    @Before
    fun setup() {
        engine = PriorityEngine()
    }

    private fun makeTrackedObject(
        label: String,
        position: SpatialPosition = SpatialPosition.CENTER,
        distanceCategory: DistanceCategory = DistanceCategory.MEDIUM,
        isApproaching: Boolean = false,
        normalizedHeight: Float = 0.15f
    ): TrackedObject {
        val box = RectF(0.4f, 0.5f - normalizedHeight / 2, 0.6f, 0.5f + normalizedHeight / 2)
        val det = DetectedObject(label, 0.9f, box)
        return TrackedObject(
            id = 0, label = label, position = position,
            distanceCategory = distanceCategory, confidence = 0.9f,
            lastDetectedObject = det,
            previousNormalizedArea = if (isApproaching) 0.1f else normalizedHeight * 0.2f,
            currentNormalizedArea = normalizedHeight * 0.2f,
            framesTracked = if (isApproaching) 5 else 1
        )
    }

    @Test
    fun `knife in path should be CRITICAL`() {
        val knife = makeTrackedObject("knife", SpatialPosition.CENTER, DistanceCategory.CLOSE)
        val priority = engine.computePriority(knife, null)
        assertTrue("Knife should be VERY_HIGH or higher",
            priority.value >= Priority.VERY_HIGH.value)
    }

    @Test
    fun `stairs in path should be CRITICAL`() {
        val stairs = makeTrackedObject("stairs", SpatialPosition.CENTER, DistanceCategory.MEDIUM)
        val priority = engine.computePriority(stairs, null)
        assertTrue("Stairs in path should be CRITICAL",
            priority == Priority.CRITICAL)
    }

    @Test
    fun `chair not in path should be LOW`() {
        val chair = makeTrackedObject("chair", SpatialPosition.LEFT, DistanceCategory.FAR)
        val priority = engine.computePriority(chair, null)
        assertTrue("Chair not in path should be LOW", priority == Priority.LOW)
    }

    @Test
    fun `elevator boosted to CRITICAL when task is FindElevator`() {
        val elevator = makeTrackedObject("elevator", SpatialPosition.CENTER, DistanceCategory.MEDIUM)
        val priority = engine.computePriority(elevator, NavigationTask.FindElevator)
        assertEquals("Elevator should be CRITICAL when task is FindElevator",
            Priority.CRITICAL, priority)
    }

    @Test
    fun `approaching person should be HIGH`() {
        val person = makeTrackedObject("person", SpatialPosition.RIGHT,
            DistanceCategory.MEDIUM, isApproaching = true)
        val priority = engine.computePriority(person, null)
        assertTrue("Approaching person should be HIGH or above",
            priority.value >= Priority.HIGH.value)
    }

    @Test
    fun `laptop should be IGNORE or LOW`() {
        val laptop = makeTrackedObject("laptop", SpatialPosition.LEFT, DistanceCategory.FAR)
        val priority = engine.computePriority(laptop, null)
        assertTrue("Laptop not in path should be LOW",
            priority.value <= Priority.LOW.value)
    }
}
