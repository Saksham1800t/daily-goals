package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import java.time.LocalDate
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
