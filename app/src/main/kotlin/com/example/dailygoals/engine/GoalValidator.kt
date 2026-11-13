package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.GoalSchedule

enum class GoalError {
    NAME_BLANK,
    NAME_TOO_LONG,
    BLOCK_MESSAGE_TOO_LONG,
    NO_DAYS,
    WINDOW_OUT_OF_RANGE,
    WINDOW_END_NOT_AFTER_START,
    WINDOW_RULE_NEEDS_WINDOW,
    COUNT_OUT_OF_RANGE,
    INTERVAL_OUT_OF_RANGE,
    UNIT_BLANK,
    TARGET_INVALID,
    MINIMUM_INVALID,
    MINIMUM_ABOVE_TARGET,
    LABEL_TOO_LONG,
}

object GoalValidator {
    const val MAX_NAME_LENGTH = 60
    const val MAX_LABEL_LENGTH = 60
    const val MAX_UNIT_LENGTH = 20
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MAX_WEEK_COUNT = 7
    private const val MAX_MONTH_COUNT = 31
    private const val MIN_INTERVAL_DAYS = 2
    private const val MAX_INTERVAL_DAYS = 30

    fun validateName(name: String): Set<GoalError> = buildSet {
        if (name.isBlank()) add(GoalError.NAME_BLANK)
        if (name.trim().length > MAX_NAME_LENGTH) add(GoalError.NAME_TOO_LONG)
    }

    fun validateSchedule(schedule: GoalSchedule): Set<GoalError> = buildSet {
        when (schedule.frequency) {
            Frequency.WEEKDAYS -> if (schedule.activeDays.isEmpty()) add(GoalError.NO_DAYS)
            Frequency.PER_WEEK -> if (schedule.count !in 1..MAX_WEEK_COUNT) add(GoalError.COUNT_OUT_OF_RANGE)
            Frequency.PER_MONTH -> if (schedule.count !in 1..MAX_MONTH_COUNT) add(GoalError.COUNT_OUT_OF_RANGE)
            Frequency.EVERY_N_DAYS -> if (schedule.intervalDays !in MIN_INTERVAL_DAYS..MAX_INTERVAL_DAYS) add(GoalError.INTERVAL_OUT_OF_RANGE)
            Frequency.DAILY, Frequency.MONTHLY_PATTERN -> Unit
        }
        val window = schedule.window ?: return@buildSet
        if (window.startMinute !in 0 until MINUTES_PER_DAY || window.endMinute !in 1..MINUTES_PER_DAY) {
            add(GoalError.WINDOW_OUT_OF_RANGE)
        } else if (window.endMinute <= window.startMinute) {
            add(GoalError.WINDOW_END_NOT_AFTER_START)
        }
    }

    fun validateDiscipline(discipline: GoalDiscipline, schedule: GoalSchedule): Set<GoalError> = buildSet {
        if (discipline.failOnWindowEnd && schedule.window == null) add(GoalError.WINDOW_RULE_NEEDS_WINDOW)
        if ((discipline.blockMessage?.trim()?.length ?: 0) > BlockMessages.MAX_LENGTH) add(GoalError.BLOCK_MESSAGE_TOO_LONG)
    }

    /** An amount goal needs a unit, a positive target, and a minimum above 0 and not above the target. */
    fun validateMeasurement(measurement: GoalMeasurement): Set<GoalError> = buildSet {
        if (!measurement.isAmount) {
            if ((measurement.minimumLabel?.trim()?.length ?: 0) > MAX_LABEL_LENGTH) add(GoalError.LABEL_TOO_LONG)
            return@buildSet
        }
        if (measurement.unit.isBlank()) add(GoalError.UNIT_BLANK)
        if (measurement.target !in 1..AmountRules.MAX_AMOUNT) add(GoalError.TARGET_INVALID)
        if (measurement.minimum < 1) {
            add(GoalError.MINIMUM_INVALID)
        } else if (measurement.minimum > measurement.target) {
            add(GoalError.MINIMUM_ABOVE_TARGET)
        }
    }
}
