package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSetTest {
    private val own = "com.example.dailygoals"
    private val safe = setOf("com.android.dialer", "com.google.android.apps.authenticator2")
    private val insta = "com.instagram.android"
    private val wake = ActiveBlock(1, "Wake up early", insta, BlockKind.PUNISHMENT, endsAtMillis = 8_000)
    private val study = ActiveBlock(2, "Study", insta, BlockKind.PUNISHMENT, endsAtMillis = 10_000)
    private val work = ActiveBlock(3, "No Instagram during work", insta, BlockKind.SCHEDULED, endsAtMillis = 9_000)

    private fun blockFor(pkg: String, blocks: List<ActiveBlock>, paused: Boolean = false, now: Long = 1_000) =
        BlockSet.blockFor(pkg, blocks, safe, own, paused, now)

    @Test
    fun temporaryAccessUnlocksOnlyTheChosenApp() {
        val youtube = wake.copy(packageName = "com.youtube")
        val blocks = listOf(wake, youtube)
        assertNull(BlockSet.blockFor(insta, blocks, safe, own, pauseActive = false, nowMillis = 1_000, unlocked = setOf(insta)))
        assertNotNull(BlockSet.blockFor("com.youtube", blocks, safe, own, pauseActive = false, nowMillis = 1_000, unlocked = setOf(insta)))
    }

    @Test
    fun anAppWithNoActiveBlockOpensNormally() {
        assertNull(blockFor("com.youtube", listOf(wake)))
        assertNull(blockFor(insta, listOf(wake), now = 8_000))
    }

    @Test
    fun severalGoalsBlockingOneAppAreListedWithTheLongestMarked() {
        val view = blockFor(insta, listOf(study, wake, work))!!
        assertEquals(listOf("Wake up early", "No Instagram during work", "Study"), view.blocks.map { it.goalName })
        assertEquals("Study", view.longest.goalName)
        assertEquals(listOf("Wake up early", "No Instagram during work"), view.others.map { it.goalName })
    }

    @Test
    fun correctingOneFailureRemovesOnlyThatGoalsBlock() {
        val left = BlockSet.withoutGoal(listOf(wake, study), goalId = 2)
        assertEquals("Wake up early", blockFor(insta, left)!!.longest.goalName)
        assertNull(blockFor(insta, BlockSet.withoutGoal(left, goalId = 1)))
    }

    @Test
    fun safeAppsAndSkritiItselfAreNeverBlockedEvenIfSavedOnAGoal() {
        val blocks = listOf(
            ActiveBlock(1, "Goal", "com.android.dialer", BlockKind.SCHEDULED, 9_000),
            ActiveBlock(1, "Goal", own, BlockKind.SCHEDULED, 9_000),
            ActiveBlock(1, "Goal", insta, BlockKind.SCHEDULED, 9_000),
        )
        assertNull(blockFor("com.android.dialer", blocks))
        assertNull(blockFor(own, blocks))
        assertNotNull(blockFor(insta, blocks))
        assertEquals(listOf(insta), BlockSet.conflicts(blocks, safe, own, 1_000).map { it.packageName })
    }

    @Test
    fun theEmergencyPauseLiftsEveryBlockButTheListStillShowsThem() {
        assertNull(blockFor(insta, listOf(wake, work), paused = true))
        assertEquals(1, BlockSet.conflicts(listOf(wake, work), safe, own, 1_000).size)
    }

    @Test
    fun endTimesKeepRunningDuringAPause() {
        val during = blockFor(insta, listOf(wake), paused = true, now = 5_000)
        val after = blockFor(insta, listOf(wake), paused = false, now = 9_000)
        assertNull(during)
        assertNull(after)
        assertNotNull(blockFor(insta, listOf(wake), paused = false, now = 7_999))
    }

    @Test
    fun scheduledAndPunishmentBlocksOnTheSameAppShowTogether() {
        val view = blockFor(insta, listOf(wake, work))!!
        assertEquals(setOf(BlockKind.SCHEDULED, BlockKind.PUNISHMENT), view.blocks.map { it.kind }.toSet())
    }

    @Test
    fun correctingOneDaysFailureEndsOnlyTheBlockItCaused() {
        val monday = wake.copy(sourceDate = "2026-11-09")
        val tuesday = wake.copy(sourceDate = "2026-11-10", endsAtMillis = 12_000)
        val left = BlockSet.withoutFailure(listOf(monday, tuesday, study), goalId = 1, date = "2026-11-10")
        assertEquals(listOf(monday, study), left)
    }

    @Test
    fun expiredBlocksAreDropped() {
        assertEquals(listOf(study), BlockSet.withoutExpired(listOf(wake, study), nowMillis = 8_000))
        assertTrue(BlockSet.withoutExpired(listOf(wake), nowMillis = 8_000).isEmpty())
    }
}
