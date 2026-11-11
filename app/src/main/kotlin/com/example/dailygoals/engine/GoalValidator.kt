package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalSchedule

enum class GoalError { NAME_BLANK, NAME_TOO_LONG, NO_DAYS, WINDOW_OUT_OF_RANGE, WINDOW_END_NOT_AFTER_START }

object GoalValidator {
    const val MAX_NAME_LENGTH = 60
    private const val MINUTES_PER_DAY = 24 * 60

    fun validateName(name: String): Set<GoalError> = buildSet {
        if (name.isBlank()) add(GoalError.NAME_BLANK)
        if (name.trim().length > MAX_NAME_LENGTH) add(GoalError.NAME_TOO_LONG)
    }

    fun validateSchedule(schedule: GoalSchedule): Set<GoalError> = buildSet {
        if (schedule.frequency == Frequency.WEEKDAYS && schedule.activeDays.isEmpty()) add(GoalError.NO_DAYS)
        val window = schedule.window ?: return@buildSet
        if (window.startMinute !in 0 until MINUTES_PER_DAY || window.endMinute !in 1..MINUTES_PER_DAY) {
            add(GoalError.WINDOW_OUT_OF_RANGE)
        } else if (window.endMinute <= window.startMinute) {
            add(GoalError.WINDOW_END_NOT_AFTER_START)
        }
    }
}
