package com.example.dailygoals.engine

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import java.time.LocalDate
import java.time.LocalDateTime

data class DayItem(
    val goal: Goal,
    val status: DayStatus,
    val section: DaySection,
    val minutesLeft: Int?,
)

data class DayPlan(
    val date: LocalDate,
    val items: List<DayItem>,
    val summary: DaySummary,
    val nextDeadlineMinute: Int?,
    val editable: Boolean,
)

object DayPlanner {
    fun plan(goals: List<Goal>, checkIns: List<CheckIn>, date: LocalDate, now: LocalDateTime): DayPlan {
        val statusByGoal = checkIns.filter { it.date == date }.associate { it.goalId to it.status }
        val items = goals.mapNotNull { goal ->
            val status = statusByGoal[goal.id] ?: DayStatus.PENDING
            if (!DueDates.showsOn(goal, date, status)) return@mapNotNull null
            val window = goal.schedule.window
            DayItem(
                goal = goal,
                status = status,
                section = DaySections.sectionOf(window, status, date, now),
                minutesLeft = DaySections.minutesLeft(window, status, date, now),
            )
        }.sortedWith(
            compareBy<DayItem> { it.section }
                .thenBy { it.goal.schedule.window?.startMinute ?: Int.MAX_VALUE }
                .thenBy { it.goal.sortOrder }
                .thenBy { it.goal.id },
        )
        val pendingWindows = items.filter { it.status == DayStatus.PENDING }.mapNotNull { it.goal.schedule.window }
        return DayPlan(
            date = date,
            items = items,
            summary = DaySummary.of(items.map { it.status }),
            nextDeadlineMinute = DaySections.nextDeadline(pendingWindows, date, now),
            editable = CheckInRules.canEdit(date, now.toLocalDate()),
        )
    }

    fun summaries(goals: List<Goal>, checkIns: List<CheckIn>, dates: Collection<LocalDate>): Map<LocalDate, DaySummary> {
        val byDate = checkIns.groupBy { it.date }
        return dates.associateWith { date ->
            val statusByGoal = byDate[date].orEmpty().associate { it.goalId to it.status }
            val statuses = goals.mapNotNull { goal ->
                val status = statusByGoal[goal.id] ?: DayStatus.PENDING
                status.takeIf { DueDates.showsOn(goal, date, status) }
            }
            DaySummary.of(statuses)
        }
    }
}
