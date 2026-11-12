package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.MeasureType
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
    fun anAmountGoalOf120WithAMinimumOf20IsAcceptedAndAMinimumAboveTheTargetIsNot() {
        val ok = GoalMeasurement(MeasureType.AMOUNT, unit = "min", target = 120, minimum = 20)
        assertEquals(emptySet<GoalError>(), GoalValidator.validateMeasurement(ok))
        assertEquals(setOf(GoalError.MINIMUM_ABOVE_TARGET), GoalValidator.validateMeasurement(ok.copy(minimum = 121)))
        assertEquals(setOf(GoalError.MINIMUM_INVALID), GoalValidator.validateMeasurement(ok.copy(minimum = 0)))
        assertEquals(setOf(GoalError.UNIT_BLANK), GoalValidator.validateMeasurement(ok.copy(unit = " ")))
        assertEquals(setOf(GoalError.TARGET_INVALID, GoalError.MINIMUM_INVALID), GoalValidator.validateMeasurement(ok.copy(target = 0, minimum = 0)))
    }

    @Test
    fun aCheckGoalOnlyLimitsTheMinimumLabelLength() {
        assertEquals(emptySet<GoalError>(), GoalValidator.validateMeasurement(GoalMeasurement(minimumLabel = "5 minutes")))
        assertEquals(setOf(GoalError.LABEL_TOO_LONG), GoalValidator.validateMeasurement(GoalMeasurement(minimumLabel = "x".repeat(61))))
    }

    @Test
    fun theWindowEndRuleNeedsATimeWindow() {
        val rule = GoalDiscipline(failOnWindowEnd = true)
        assertEquals(setOf(GoalError.WINDOW_RULE_NEEDS_WINDOW), GoalValidator.validateDiscipline(rule, schedule))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateDiscipline(rule, schedule.copy(window = TimeWindow(360, 420))))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateDiscipline(GoalDiscipline(failOnDayEnd = true), schedule))
    }

    @Test
    fun quotaAndIntervalSettingsStayInRange() {
        val weekly = schedule.copy(frequency = Frequency.PER_WEEK, count = 4)
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(weekly))
        assertEquals(setOf(GoalError.COUNT_OUT_OF_RANGE), GoalValidator.validateSchedule(weekly.copy(count = 8)))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(weekly.copy(frequency = Frequency.PER_MONTH, count = 31)))
        assertEquals(setOf(GoalError.COUNT_OUT_OF_RANGE), GoalValidator.validateSchedule(weekly.copy(frequency = Frequency.PER_MONTH, count = 0)))
        val interval = schedule.copy(frequency = Frequency.EVERY_N_DAYS, intervalDays = 1)
        assertEquals(setOf(GoalError.INTERVAL_OUT_OF_RANGE), GoalValidator.validateSchedule(interval))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(interval.copy(intervalDays = 30)))
    }

    @Test
    fun windowMustStayInsideOneDay() {
        val error = setOf(GoalError.WINDOW_OUT_OF_RANGE)
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(-1, 60))))
        assertEquals(error, GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(60, 1441))))
        assertEquals(emptySet<GoalError>(), GoalValidator.validateSchedule(schedule.copy(window = TimeWindow(1380, 1440))))
    }
}
