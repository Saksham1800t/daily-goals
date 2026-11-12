package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.MonthlyNth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object DueDates {
    /** Quota goals count as due on every day of their period; the quota rules decide the outcome. */
    fun isDue(schedule: GoalSchedule, date: LocalDate): Boolean {
        if (date < schedule.startDate) return false
        return when (schedule.frequency) {
            Frequency.DAILY, Frequency.PER_WEEK, Frequency.PER_MONTH -> true
            Frequency.WEEKDAYS -> date.dayOfWeek in schedule.activeDays
            Frequency.EVERY_N_DAYS -> ChronoUnit.DAYS.between(schedule.startDate, date) % schedule.intervalDays.coerceAtLeast(1) == 0L
            Frequency.MONTHLY_PATTERN -> date == nthWeekday(YearMonth.from(date), schedule.monthlyNth, schedule.monthlyWeekday)
        }
    }

    fun nthWeekday(month: YearMonth, nth: MonthlyNth, weekday: DayOfWeek): LocalDate {
        val first = month.atDay(1)
        return when (nth) {
            MonthlyNth.FIRST -> first.with(TemporalAdjusters.dayOfWeekInMonth(1, weekday))
            MonthlyNth.SECOND -> first.with(TemporalAdjusters.dayOfWeekInMonth(2, weekday))
            MonthlyNth.THIRD -> first.with(TemporalAdjusters.dayOfWeekInMonth(3, weekday))
            MonthlyNth.FOURTH -> first.with(TemporalAdjusters.dayOfWeekInMonth(4, weekday))
            MonthlyNth.LAST -> first.with(TemporalAdjusters.lastInMonth(weekday))
        }
    }

    /**
     * A recorded result keeps a goal visible on that date even after the goal is archived or its
     * schedule is edited, so history never loses a tick or a miss.
     */
    fun showsOn(goal: Goal, date: LocalDate, status: DayStatus): Boolean {
        if (status != DayStatus.PENDING) return true
        return goal.state == GoalState.ACTIVE && isDue(goal.schedule, date)
    }
}
