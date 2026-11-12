package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.TimeWindow
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun apply(current: DayStatus, action: CheckAction, date: LocalDate = today) =
        CheckInRules.apply(current, action, date, today)

    @Test
    fun tickingAPendingGoalMarksItDone() {
        assertEquals(CheckResult.Changed(DayStatus.DONE, null), apply(DayStatus.PENDING, CheckAction.TOGGLE_DONE))
    }

    @Test
    fun tickingADoneGoalReturnsItToPending() {
        assertEquals(CheckResult.Changed(DayStatus.PENDING, null), apply(DayStatus.DONE, CheckAction.TOGGLE_DONE))
    }

    @Test
    fun aFailedGoalCanBeCorrectedToDone() {
        assertEquals(CheckResult.Changed(DayStatus.DONE, null), apply(DayStatus.FAILED, CheckAction.TOGGLE_DONE))
    }

    @Test
    fun markingFailedRecordsTheManualReason() {
        val expected = CheckResult.Changed(DayStatus.FAILED, FailReason.MANUAL)
        assertEquals(expected, apply(DayStatus.PENDING, CheckAction.MARK_FAILED))
        assertEquals(expected, apply(DayStatus.DONE, CheckAction.MARK_FAILED))
    }

    @Test
    fun repeatingTheSameResultChangesNothing() {
        assertEquals(CheckResult.NoChange, apply(DayStatus.FAILED, CheckAction.MARK_FAILED))
        assertEquals(CheckResult.NoChange, apply(DayStatus.PENDING, CheckAction.CLEAR))
    }

    @Test
    fun clearingReturnsAnyResultToPending() {
        assertEquals(CheckResult.Changed(DayStatus.PENDING, null), apply(DayStatus.FAILED, CheckAction.CLEAR))
        assertEquals(CheckResult.Changed(DayStatus.PENDING, null), apply(DayStatus.DONE, CheckAction.CLEAR))
    }

    @Test
    fun theMinimumStepIsSetAndClearedLikeATick() {
        assertEquals(CheckResult.Changed(DayStatus.MINIMUM, null), apply(DayStatus.PENDING, CheckAction.MARK_MINIMUM))
        assertEquals(CheckResult.Changed(DayStatus.PENDING, null), apply(DayStatus.MINIMUM, CheckAction.MARK_MINIMUM))
        assertEquals(CheckResult.Changed(DayStatus.DONE, null), apply(DayStatus.MINIMUM, CheckAction.TOGGLE_DONE))
    }

    @Test
    fun lateMinutesCountFromTheWindowEndOnTheSameDateOnly() {
        val window = TimeWindow(6 * 60, 7 * 60)
        val utc = ZoneOffset.UTC
        assertEquals(3, CheckInRules.lateMinutes(window, today, today.atTime(7, 3), utc))
        assertEquals(null, CheckInRules.lateMinutes(window, today, today.atTime(7, 0), utc))
        assertEquals(null, CheckInRules.lateMinutes(window, today, today.atTime(6, 30), utc))
        assertEquals(null, CheckInRules.lateMinutes(window, today.minusDays(1), today.atTime(9, 0), utc))
        assertEquals(null, CheckInRules.lateMinutes(null, today, today.atTime(9, 0), utc))
    }

    @Test
    fun pastDatesCanBeEdited() {
        val result = apply(DayStatus.PENDING, CheckAction.TOGGLE_DONE, today.minusDays(30))
        assertEquals(CheckResult.Changed(DayStatus.DONE, null), result)
    }

    @Test
    fun futureDatesCannotBeTickedOrFailed() {
        val tomorrow = today.plusDays(1)
        assertEquals(CheckResult.FutureDate, apply(DayStatus.PENDING, CheckAction.TOGGLE_DONE, tomorrow))
        assertEquals(CheckResult.FutureDate, apply(DayStatus.PENDING, CheckAction.MARK_FAILED, tomorrow))
        assertFalse(CheckInRules.canEdit(tomorrow, today))
        assertTrue(CheckInRules.canEdit(today, today))
    }
}
