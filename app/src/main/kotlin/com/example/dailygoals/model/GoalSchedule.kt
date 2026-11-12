package com.example.dailygoals.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKDAYS, PER_WEEK, PER_MONTH, EVERY_N_DAYS, MONTHLY_PATTERN }

enum class MonthlyNth { FIRST, SECOND, THIRD, FOURTH, LAST }

/** When a quota goal is judged: as soon as the count is out of reach, or only on the last day. */
enum class QuotaFailureMode { WHEN_UNREACHABLE, END_OF_PERIOD }

/** Minutes since midnight; the window is open from [startMinute] up to, not including, [endMinute]. */
data class TimeWindow(val startMinute: Int, val endMinute: Int)

data class GoalSchedule(
    val frequency: Frequency = Frequency.DAILY,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val window: TimeWindow? = null,
    val startDate: LocalDate,
    val count: Int = 1,
    val intervalDays: Int = DEFAULT_INTERVAL_DAYS,
    val monthlyNth: MonthlyNth = MonthlyNth.FIRST,
    val monthlyWeekday: DayOfWeek = DayOfWeek.MONDAY,
    val quotaFailureMode: QuotaFailureMode = QuotaFailureMode.WHEN_UNREACHABLE,
    val graceMinutes: Int = DEFAULT_GRACE_MINUTES,
) {
    val isQuota: Boolean get() = frequency == Frequency.PER_WEEK || frequency == Frequency.PER_MONTH

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 2
        const val DEFAULT_GRACE_MINUTES = 5
        val GRACE_CHOICES = listOf(0, 5, 10, 15)
    }
}
