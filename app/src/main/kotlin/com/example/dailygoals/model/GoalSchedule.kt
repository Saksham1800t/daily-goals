package com.example.dailygoals.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKDAYS }

/** Minutes since midnight; the window is open from [startMinute] up to, not including, [endMinute]. */
data class TimeWindow(val startMinute: Int, val endMinute: Int)

data class GoalSchedule(
    val frequency: Frequency = Frequency.DAILY,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val window: TimeWindow? = null,
    val startDate: LocalDate,
)
