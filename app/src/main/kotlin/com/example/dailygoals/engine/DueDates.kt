package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import java.time.LocalDate

object DueDates {
    fun isDue(schedule: GoalSchedule, date: LocalDate): Boolean {
        if (date < schedule.startDate) return false
        return when (schedule.frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKDAYS -> date.dayOfWeek in schedule.activeDays
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
