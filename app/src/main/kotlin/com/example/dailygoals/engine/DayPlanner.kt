package com.example.dailygoals.engine

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.isMet
import java.time.LocalDate
import java.time.LocalDateTime

data class QuotaProgress(val done: Int, val required: Int, val perWeek: Boolean) {
    val met: Boolean get() = done >= required
}

data class DayItem(
    val goal: Goal,
    val status: DayStatus,
    val section: DaySection,
    val minutesLeft: Int?,
    val amount: Int = 0,
    val failReason: FailReason? = null,
    val lateMinutes: Int? = null,
    val quota: QuotaProgress? = null,
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
        val byGoal = checkIns.groupBy { it.goalId }
        val items = goals.mapNotNull { goal -> itemFor(goal, byGoal[goal.id].orEmpty(), date, now) }.sortedWith(
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
        val byGoal = checkIns.groupBy { it.goalId }
        return dates.associateWith { date ->
            val statuses = goals.mapNotNull { goal -> effective(goal, byGoal[goal.id].orEmpty(), date)?.first }
            DaySummary.of(statuses)
        }
    }

    private fun itemFor(goal: Goal, own: List<CheckIn>, date: LocalDate, now: LocalDateTime): DayItem? {
        val (status, quota) = effective(goal, own, date) ?: return null
        val today = own.firstOrNull { it.date == date }
        val window = goal.schedule.window
        return DayItem(
            goal = goal,
            status = status,
            section = DaySections.sectionOf(window, status, date, now),
            minutesLeft = DaySections.minutesLeft(window, status, date, now),
            amount = today?.amount ?: 0,
            failReason = today?.failReason,
            lateMinutes = today?.lateMinutes,
            quota = quota,
        )
    }

    /** A quota goal whose period is already met shows as kept on the remaining days of the period. */
    private fun effective(goal: Goal, own: List<CheckIn>, date: LocalDate): Pair<DayStatus, QuotaProgress?>? {
        val stored = own.firstOrNull { it.date == date }?.status ?: DayStatus.PENDING
        if (!DueDates.showsOn(goal, date, stored)) return null
        val period = Periods.of(goal.schedule.frequency, date) ?: return stored to null
        val done = own.count { it.date in period && it.status.isMet }
        val quota = QuotaProgress(done, QuotaRules.required(goal.schedule, period), goal.schedule.frequency == Frequency.PER_WEEK)
        val status = if (stored == DayStatus.PENDING && quota.met) DayStatus.DONE else stored
        return status to quota
    }
}
