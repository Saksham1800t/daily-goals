package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRulesTest {
    private val own = "com.example.dailygoals"
    private val target = "com.instagram.android"
    private val session = BlockSession(target, untilMillis = 10_000)
    private val passThrough = setOf("com.android.systemui", "com.google.android.inputmethod.latin")

    private fun decide(
        pkg: String,
        showing: Boolean = false,
        now: Long = 1_000,
        active: BlockSession? = session,
    ) = ForegroundRules.decide(pkg, own, active, showing, now, passThrough)

    @Test
    fun theBlockedAppComingToTheFrontShowsTheBlock() {
        assertEquals(ForegroundAction.SHOW_BLOCK, decide(target))
    }

    @Test
    fun aBlockThatIsAlreadyUpIsNotShownAgain() {
        assertEquals(ForegroundAction.NONE, decide(target, showing = true))
    }

    @Test
    fun leavingTheBlockedAppForAnotherAppRemovesTheBlock() {
        assertEquals(ForegroundAction.HIDE_BLOCK, decide("com.android.launcher3", showing = true))
        assertEquals(ForegroundAction.NONE, decide("com.android.launcher3", showing = false))
    }

    @Test
    fun otherAppsAreNeverBlocked() {
        assertEquals(ForegroundAction.NONE, decide("com.google.android.youtube"))
    }

    @Test
    fun theAppItselfAndOverlaysFromTheSystemNeverChangeTheBlock() {
        assertEquals(ForegroundAction.NONE, decide(own, showing = true))
        assertEquals(ForegroundAction.NONE, decide("com.android.systemui", showing = true))
        assertEquals(ForegroundAction.NONE, decide("com.google.android.inputmethod.latin", showing = true))
        assertEquals(ForegroundAction.NONE, decide("com.android.systemui", showing = false))
    }

    @Test
    fun withoutASessionNothingIsBlockedAndAnyBlockIsRemoved() {
        assertEquals(ForegroundAction.NONE, decide(target, active = null))
        assertEquals(ForegroundAction.HIDE_BLOCK, decide(target, showing = true, active = null))
    }

    @Test
    fun aTestThatHasEndedStopsBlockingAndClearsTheBlock() {
        assertEquals(ForegroundAction.NONE, decide(target, now = 10_000))
        assertEquals(ForegroundAction.HIDE_BLOCK, decide(target, showing = true, now = 10_001))
        assertFalse(ForegroundRules.isActive(session, 10_000))
        assertTrue(ForegroundRules.isActive(session, 9_999))
    }

    @Test
    fun aTestWithNoEndBlocksUntilItIsSwitchedOff() {
        val open = BlockSession(target, untilMillis = null)
        assertTrue(ForegroundRules.isActive(open, Long.MAX_VALUE))
        assertEquals(ForegroundAction.SHOW_BLOCK, decide(target, now = 1_000_000_000_000, active = open))
    }
}
