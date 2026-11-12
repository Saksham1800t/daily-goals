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

    private val launcher = "com.motorola.launcher3"

    private fun decideAfterShow(pkg: String, sinceShown: Long, showing: Boolean = true) = ForegroundRules.decide(
        pkg, own, session, showing, 1_000, passThrough, launcherPackages = setOf(launcher), millisSinceShown = sinceShown,
    )

    @Test
    fun aLauncherEventRightAfterTheBlockAppearsIsTheRecentsTransitionNotALeave() {
        assertEquals(ForegroundAction.NONE, decideAfterShow(launcher, sinceShown = 230))
        assertEquals(ForegroundAction.NONE, decideAfterShow(launcher, sinceShown = 999))
    }

    @Test
    fun aLauncherEventAfterTheGraceTimeRemovesTheBlock() {
        assertEquals(ForegroundAction.HIDE_BLOCK, decideAfterShow(launcher, sinceShown = ForegroundRules.LAUNCHER_GRACE_MILLIS))
        assertEquals(ForegroundAction.HIDE_BLOCK, decideAfterShow(launcher, sinceShown = 5_000))
    }

    @Test
    fun anotherAppStillRemovesTheBlockEvenInsideTheGraceTime() {
        assertEquals(ForegroundAction.HIDE_BLOCK, decideAfterShow("com.google.android.googlequicksearchbox", sinceShown = 100))
        assertEquals(ForegroundAction.HIDE_BLOCK, decideAfterShow("com.google.android.youtube.music", sinceShown = 100))
    }

    @Test
    fun theGraceTimeNeverShowsABlockOrKeepsOneThatIsNotUp() {
        assertEquals(ForegroundAction.NONE, decideAfterShow(launcher, sinceShown = 100, showing = false))
        assertEquals(ForegroundAction.SHOW_BLOCK, decideAfterShow(target, sinceShown = 100, showing = false))
    }

    @Test
    fun theBlockedAppReportingItselfRightAfterSendingHomeIsItsFloatingWindowNotAnOpen() {
        fun afterHome(sinceHome: Long) = ForegroundRules.decide(target, own, session, false, 1_000, passThrough, millisSinceHome = sinceHome)
        assertEquals(ForegroundAction.NONE, afterHome(500))
        assertEquals(ForegroundAction.NONE, afterHome(ForegroundRules.HOME_QUIET_MILLIS - 1))
        assertEquals(ForegroundAction.SHOW_BLOCK, afterHome(ForegroundRules.HOME_QUIET_MILLIS))
        assertEquals(ForegroundAction.SHOW_BLOCK, afterHome(Long.MAX_VALUE))
    }

    @Test
    fun sendingHomeDoesNotStopOtherAppsFromChangingTheBlock() {
        val hide = ForegroundRules.decide("com.android.launcher3", own, session, true, 1_000, passThrough, millisSinceHome = 100)
        assertEquals(ForegroundAction.HIDE_BLOCK, hide)
    }

    private fun verified(event: String, active: String?, showing: Boolean = false, active2: BlockSession? = session) =
        ForegroundRules.decide(
            event, own, active2, showing, 1_000, passThrough,
            launcherPackages = setOf(launcher), millisSinceShown = 100, millisSinceHome = 100, activeWindowPackage = active,
        )

    @Test
    fun theBlockIsShownOnlyWhenTheBlockedAppIsReallyTheActiveWindow() {
        assertEquals(ForegroundAction.SHOW_BLOCK, verified(event = target, active = target))
        assertEquals(ForegroundAction.SHOW_BLOCK, verified(event = launcher, active = target))
    }

    @Test
    fun aFloatingWindowOfTheBlockedAppIsNotTheAppBeingOpened() {
        assertEquals(ForegroundAction.NONE, verified(event = target, active = launcher))
        assertEquals(ForegroundAction.NONE, verified(event = target, active = "com.google.android.googlequicksearchbox"))
    }

    @Test
    fun aLateLauncherReportNeverRemovesTheBlockWhileTheAppIsStillActive() {
        assertEquals(ForegroundAction.NONE, verified(event = launcher, active = target, showing = true))
    }

    @Test
    fun theBlockIsRemovedAsSoonAsAnotherWindowIsReallyActive() {
        assertEquals(ForegroundAction.HIDE_BLOCK, verified(event = launcher, active = launcher, showing = true))
        assertEquals(ForegroundAction.HIDE_BLOCK, verified(event = "com.google.android.youtube.music", active = "com.google.android.youtube.music", showing = true))
    }

    @Test
    fun ourOwnScreensAndSystemOverlaysAsTheActiveWindowKeepTheBlockAsItIs() {
        assertEquals(ForegroundAction.NONE, verified(event = launcher, active = own, showing = true))
        assertEquals(ForegroundAction.NONE, verified(event = launcher, active = "com.android.systemui", showing = true))
        assertEquals(ForegroundAction.NONE, verified(event = launcher, active = own, showing = false))
    }

    @Test
    fun anEndedOrMissingSessionStillRemovesTheBlockWhateverIsActive() {
        assertEquals(ForegroundAction.HIDE_BLOCK, verified(event = target, active = target, showing = true, active2 = null))
        assertEquals(ForegroundAction.NONE, verified(event = target, active = target, active2 = null))
    }

    @Test
    fun withoutAnActiveWindowTheOlderTimingRulesStillApply() {
        assertEquals(ForegroundAction.NONE, verified(event = target, active = null))
        assertEquals(ForegroundAction.NONE, verified(event = launcher, active = null, showing = true))
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
