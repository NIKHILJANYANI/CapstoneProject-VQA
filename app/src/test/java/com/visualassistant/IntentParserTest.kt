package com.visualassistant.assistant.intent

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the IntentParser.
 *
 * These tests verify that common voice commands are correctly mapped to intents.
 */
class IntentParserTest {

    private lateinit var parser: IntentParser

    @Before
    fun setup() {
        parser = IntentParser()
    }

    @Test
    fun `parse stop command`() {
        listOf("stop", "stop guiding", "cancel", "never mind").forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to Stop", intent is UserIntent.Stop)
        }
    }

    @Test
    fun `parse walking mode command`() {
        listOf("start walking", "walking mode", "help me navigate", "i'm walking").forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to StartWalkingMode", intent is UserIntent.StartWalkingMode)
        }
    }

    @Test
    fun `parse read text command`() {
        listOf("read that", "read this", "what does that say", "what does the sign say").forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to ReadText", intent is UserIntent.ReadText)
        }
    }

    @Test
    fun `parse guide to exit`() {
        listOf("guide me out", "help me get out", "find the exit", "i need to get outside").forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to GuideToExit", intent is UserIntent.GuideToExit)
        }
    }

    @Test
    fun `parse guide to elevator`() {
        val intent = parser.parse("guide me to the elevator")
        assertTrue(intent is UserIntent.GuideToElevator)
    }

    @Test
    fun `parse guide to room with number`() {
        val intent = parser.parse("guide me to room 204")
        assertTrue(intent is UserIntent.GuideToRoom)
        assertEquals("204", (intent as UserIntent.GuideToRoom).roomNumber)
    }

    @Test
    fun `parse guide to room 3 digit`() {
        val intent = parser.parse("take me to 204")
        assertTrue(intent is UserIntent.GuideToRoom)
    }

    @Test
    fun `parse describe scene`() {
        listOf(
            "what is in front of me",
            "what's ahead",
            "what do you see"
        ).forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to DescribeScene", intent is UserIntent.DescribeScene)
        }
    }

    @Test
    fun `parse describe left`() {
        val intent = parser.parse("what is on my left")
        assertTrue(intent is UserIntent.DescribeLeft)
    }

    @Test
    fun `parse describe right`() {
        val intent = parser.parse("what is on my right")
        assertTrue(intent is UserIntent.DescribeRight)
    }

    @Test
    fun `parse check hazards`() {
        listOf("anything dangerous", "any danger", "is it safe", "any hazards").forEach { input ->
            val intent = parser.parse(input)
            assertTrue("'$input' should map to CheckHazards", intent is UserIntent.CheckHazards)
        }
    }

    @Test
    fun `parse remember location`() {
        val intent = parser.parse("remember this location")
        assertTrue(intent is UserIntent.RememberCurrentLocation)
    }

    @Test
    fun `parse guide to it follow up`() {
        val intent = parser.parse("take me there")
        assertTrue(intent is UserIntent.GuideToLastMentioned)
    }

    @Test
    fun `parse find object`() {
        val intent = parser.parse("where is the door")
        // Should map to FindObject or GuideToExit depending on context
        assertTrue(intent is UserIntent.FindObject || intent is UserIntent.GuideToExit)
    }

    @Test
    fun `parse query memory room`() {
        val intent = parser.parse("where is room 204")
        assertTrue("Should map to QueryMemory for room query", intent is UserIntent.QueryMemory)
    }

    @Test
    fun `parse surroundings`() {
        val intent = parser.parse("what is around me")
        assertTrue(intent is UserIntent.DescribeSurroundings)
    }
}
