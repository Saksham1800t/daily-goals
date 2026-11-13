package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunishmentRulesTest {
    private val apps = setOf("com.instagram.android", "com.android.dialer")
    private val safe = setOf("com.android.dialer")
    private val own = "com.example.dailygoals"

    private fun plan(intensity: Intensity, paused: Boolean = false, minutes: Int = 60, packages: Set<String> = apps) =
        PunishmentRules.onFailure(intensity, packages, minutes, failedAtMillis = 1_000_000, pauseActive = paused, safeApps = safe, ownPackage = own)

    @Test
    fun noneTracksOnly() {
        assertEquals(PunishmentPlan.NOTHING, plan(Intensity.NONE))
    }

    @Test
    fun gentleNotifiesButNeverBlocks() {
        val p = plan(Intensity.GENTLE)
        assertTrue(p.notify)
        assertFalse(p.blocks)
        assertFalse(p.fullScreenAlert)
    }

    @Test
    fun moderateBlocksTheChosenAppsForTheChosenTime() {
        val p = plan(Intensity.MODERATE, minutes = 30)
        assertTrue(p.notify)
        assertTrue(p.blocks)
        assertEquals(1_000_000 + 30 * 60_000L, p.blockUntilMillis)
        assertFalse(p.fullScreenAlert)
    }

    @Test
    fun strongAddsTheFullScreenAlert() {
        val p = plan(Intensity.STRONG)
        assertTrue(p.fullScreenAlert)
        assertTrue(p.blocks)
    }

    @Test
    fun safeAppsAndSkritiNeverEndUpInABlock() {
        val p = plan(Intensity.MODERATE, packages = apps + own)
        assertEquals(setOf("com.instagram.android"), p.blockPackages)
        assertFalse(plan(Intensity.MODERATE, packages = setOf("com.android.dialer")).blocks)
    }

    @Test
    fun aFailureDuringTheEmergencyPauseIsMarkedSkippedAndDoesNothing() {
        val p = plan(Intensity.STRONG, paused = true)
        assertTrue(p.skippedByPause)
        assertFalse(p.notify)
        assertFalse(p.blocks)
        assertFalse(plan(Intensity.NONE, paused = true).skippedByPause)
    }

    @Test
    fun moderateWithNoAppsChosenStillNotifies() {
        val p = plan(Intensity.MODERATE, packages = emptySet())
        assertTrue(p.notify)
        assertFalse(p.blocks)
    }
}
