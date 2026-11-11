package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.TimeWindow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalValidatorTest {
    private val schedule = GoalSchedule(startDate = LocalDate.of(2026, 11, 11))

    @Test
    fun nameMustNotBeBlankOrTooLong() {
        assertEquals(setOf(GoalError.NAME_BLANK), GoalValidator.validateName("   "))
        assertEquals(setOf(GoalError.NAME_TOO_LONG), GoalValidator.validateName("x".repeat(61)))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateName("  Wake up early  "))
    }

    @Test
    fun defaultScheduleIsValid() {
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(schedule))
    }

    @Test
    fun weekdayScheduleNeedsAtLeastOneDay() {
        val noDays = schedule.copy(frequency = Frequency.WEEKDAYS, activeDays = emptySet())
        assertEquals(setOf(GoalError.NO_DAYS), GoalValidator.validateSchedule(noDays))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(noDays.copy(frequency = Frequency.DAILY)))
    }

    @Test
    fun windowMustEndAfterItStarts() {
        val error = setOf(GoalError.WINDOW_END_NOT_AFTER_START)
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(420, 420))))
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(1320, 120))))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(360, 420))))
    }

    @Test
    fun windowMustStayInsideOneDay() {
        val error = setOf(GoalError.WINDOW_OUT_OF_RANGE)
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(-1, 60))))
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(60, 1441))))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(1380, 1440))))
    }
}
