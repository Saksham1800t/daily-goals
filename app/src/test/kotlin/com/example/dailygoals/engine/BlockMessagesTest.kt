package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockMessagesTest {
    private val values = BlockMessages.SAMPLE

    @Test
    fun theDefaultsMatchThePlan() {
        assertEquals(
            "Instagram is blocked until 18:00 because of your goal: Study.",
            BlockMessages.render(BlockMessages.DEFAULT_SCHEDULED, values),
        )
        assertEquals(
            "You missed Study, so Instagram is blocked until 18:00.",
            BlockMessages.render(BlockMessages.DEFAULT_PUNISHMENT, values),
        )
    }

    @Test
    fun everyPlaceholderIsReplaced() {
        val text = BlockMessages.PLACEHOLDERS.joinToString("|")
        assertEquals("Instagram|Study|18:00|1 h 20 min|I want to pass the exam|3", BlockMessages.render(text, values))
    }

    @Test
    fun anUnknownPlaceholderIsLeftAsTyped() {
        assertEquals("Hello {name}, Study", BlockMessages.render("Hello {name}, {goal}", values))
    }

    @Test
    fun aMissingValueIsLeftOutCleanly() {
        val noWhy = values.copy(why = null)
        assertEquals("Stay away from Instagram.", BlockMessages.render("Stay away from {app} {why}.", noWhy))
        assertEquals("Instagram until 18:00", BlockMessages.render("{app}  {goal} until {until}", values.copy(goal = null)))
    }

    @Test
    fun aMessageIsRefusedWhenOverOneHundredSixtyCharacters() {
        assertTrue(BlockMessages.check("a".repeat(160)) is MessageCheck.Ok)
        assertEquals(MessageCheck.TooLong(161), BlockMessages.check("a".repeat(161)))
    }

    @Test
    fun anEmptyFieldMeansTheDefault() {
        assertEquals(MessageCheck.Empty, BlockMessages.check("   "))
        assertEquals(BlockMessages.DEFAULT_SCHEDULED, BlockMessages.choose("", null, BlockKind.SCHEDULED))
        assertEquals(BlockMessages.DEFAULT_PUNISHMENT, BlockMessages.choose(null, " ", BlockKind.PUNISHMENT))
    }

    @Test
    fun aGoalsOwnMessageWinsOverTheSettingsDefault() {
        assertEquals("Own text", BlockMessages.choose("Own text", "Default text", BlockKind.SCHEDULED))
        assertEquals("Default text", BlockMessages.choose(null, "Default text", BlockKind.SCHEDULED))
    }
}
