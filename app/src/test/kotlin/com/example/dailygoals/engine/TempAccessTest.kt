package com.example.dailygoals.engine

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempAccessTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val settings = TempAccessSettings()
    private val insta = "com.instagram.android"

    private fun millis(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val noon = millis(13, 12)

    private fun offer(uses: List<TempAccessUse> = emptyList(), allow: List<Boolean> = listOf(true), with: TempAccessSettings = settings, now: Long = noon) =
        TempAccess.offer(with, uses, allow, now, zone)

    @Test
    fun defaultsAreTwoUsesTenMinutesAndASixtySecondWait() {
        assertEquals(TempAccessSettings(enabled = true, maxUsesPerDay = 2, maxMinutes = 10, waitSeconds = 60), settings)
        assertEquals(TempAccessOffer.AVAILABLE, offer())
    }

    @Test
    fun offOrZeroUsesTurnsTheFeatureOff() {
        assertEquals(TempAccessOffer.OFF, offer(with = settings.copy(enabled = false)))
        assertEquals(TempAccessOffer.OFF, offer(with = settings.copy(maxUsesPerDay = 0)))
    }

    @Test
    fun oneGoalThatForbidsItIsEnoughToRefuse() {
        assertEquals(TempAccessOffer.GOAL_FORBIDS, offer(allow = listOf(true, false)))
        assertEquals(TempAccessOffer.AVAILABLE, offer(allow = listOf(true, true)))
    }

    @Test
    fun theDailyLimitCountsAcrossAppsAndResetsAtTheLocalDateChange() {
        val uses = listOf(TempAccessUse(insta, millis(13, 9), millis(13, 9, 5)), TempAccessUse("com.youtube", millis(13, 10), millis(13, 10, 5)))
        assertEquals(TempAccessOffer.NONE_LEFT, offer(uses))
        assertEquals(0, TempAccess.usesLeft(settings, uses, noon, zone))
        assertEquals(TempAccessOffer.AVAILABLE, offer(uses, now = millis(14, 0, 1)))
        assertEquals(2, TempAccess.usesLeft(settings, uses, millis(14, 0, 1), zone))
    }

    @Test
    fun theWaitTheReasonAndTheMinuteLimitAreEnforced() {
        assertEquals(setOf(TempAccessError.WAIT_NOT_OVER), TempAccess.errors(settings, 59, "bank transfer", 5))
        assertEquals(setOf(TempAccessError.REASON_MISSING), TempAccess.errors(settings, 60, "  ", 5))
        assertEquals(setOf(TempAccessError.MINUTES_OUT_OF_RANGE), TempAccess.errors(settings, 60, "bank", 11))
        assertEquals(setOf(TempAccessError.MINUTES_OUT_OF_RANGE), TempAccess.errors(settings, 60, "bank", 0))
        assertTrue(TempAccess.errors(settings, 60, "bank", 10).isEmpty())
    }

    @Test
    fun onlyTheChosenAppIsUnlockedAndTheBlockReturnsOnTime() {
        val uses = TempAccess.start(emptyList(), insta, 10, noon)
        assertEquals(setOf(insta), TempAccess.unlockedPackages(uses, noon + 1))
        assertNull(TempAccess.active(uses, "com.youtube", noon + 1))
        assertEquals(setOf(insta), TempAccess.unlockedPackages(uses, noon + 10 * 60_000L - 1))
        assertTrue(TempAccess.unlockedPackages(uses, noon + 10 * 60_000L).isEmpty())
    }

    @Test
    fun lockingAgainEarlyEndsItButDoesNotGiveTheUseBack() {
        val started = TempAccess.start(emptyList(), insta, 10, noon)
        val ended = TempAccess.endEarly(started, insta, noon + 60_000L)
        assertTrue(TempAccess.unlockedPackages(ended, noon + 60_001L).isEmpty())
        assertEquals(1, TempAccess.usesToday(ended, noon + 60_001L, zone))
        assertEquals(1, TempAccess.usesLeft(settings, ended, noon + 60_001L, zone))
    }

    @Test
    fun minuteChoicesNeverGoAboveTheMaximum() {
        assertEquals(listOf(1, 2, 5, 10), TempAccess.minuteChoices(settings))
        assertEquals(listOf(1), TempAccess.minuteChoices(settings.copy(maxMinutes = 1)))
        assertEquals(listOf(1, 5, 20, 60), TempAccess.minuteChoices(settings.copy(maxMinutes = 60)))
        assertEquals(listOf(1, 2, 5, 7), TempAccess.minuteChoices(settings.copy(maxMinutes = 7)))
    }

    @Test
    fun settingsAreClampedToTheirRanges() {
        assertEquals(TempAccessSettings(true, 10, 60, 300), TempAccessSettings(true, 99, 99, 999).clamped())
        assertEquals(TempAccessSettings(true, 0, 1, 15), TempAccessSettings(true, -1, 0, 1).clamped())
    }

    @Test
    fun raisingALimitLoosensAndLoweringItDoesNot() {
        assertTrue(TempAccess.loosens(settings, settings.copy(maxUsesPerDay = 3)))
        assertTrue(TempAccess.loosens(settings, settings.copy(maxMinutes = 11)))
        assertTrue(TempAccess.loosens(settings, settings.copy(waitSeconds = 30)))
        assertTrue(TempAccess.loosens(settings.copy(enabled = false), settings))
        assertFalse(TempAccess.loosens(settings, settings.copy(maxUsesPerDay = 1, maxMinutes = 5, waitSeconds = 120)))
        assertFalse(TempAccess.loosens(settings, settings.copy(enabled = false)))
    }
}
