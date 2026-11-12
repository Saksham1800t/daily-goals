package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.isMet
import java.time.LocalDate

data class ConsistencyStats(
    val met: Int,
    val due: Int,
    val minimumOnly: Int,
    val bestStreak: Int,
    val currentStreak: Int,
) {
    val percent: Int get() = if (due == 0) 0 else (met * 100 + due / 2) / due
    val streakBroken: Boolean get() = currentStreak == 0 && bestStreak > 0

    companion object {
        val EMPTY = ConsistencyStats(0, 0, 0, 0, 0)
    }
}

/**
 * Consistency is the share of due days (or periods, for quota goals) met over the last 30 days. Paused,
 * rescued and not-due days are left out, and MINIMUM counts as met. A day still open today is not judged yet.
 */
object Consistency {
    const val WINDOW_DAYS = 30

    fun forGoal(goal: Goal, statuses: Map<LocalDate, DayStatus>, today: LocalDate, neutralDates: Set<LocalDate> = emptySet()): ConsistencyStats {
        val judged = if (goal.schedule.isQuota) judgedPeriods(goal, statuses, today, neutralDates) else judgedDays(goal, statuses, today, neutralDates)
        val recent = judged.filter { it.date > today.minusDays(WINDOW_DAYS.toLong()) }
        var run = 0
        var best = 0
        judged.forEach { entry ->
            run = if (entry.met) run + 1 else 0
            best = maxOf(best, run)
        }
        return ConsistencyStats(
            met = recent.count { it.met },
            due = recent.size,
            minimumOnly = recent.count { it.minimumOnly },
            bestStreak = best,
            currentStreak = run,
        )
    }

    private class Judged(val date: LocalDate, val met: Boolean, val minimumOnly: Boolean)

    private fun judgedDays(goal: Goal, statuses: Map<LocalDate, DayStatus>, today: LocalDate, neutral: Set<LocalDate>): List<Judged> =
        generateSequence(goal.schedule.startDate) { it.plusDays(1) }.takeWhile { it <= today }.mapNotNull { date ->
            if (date in neutral || !DueDates.isDue(goal.schedule, date)) return@mapNotNull null
            val status = statuses[date] ?: DayStatus.PENDING
            if (date == today && status == DayStatus.PENDING) return@mapNotNull null
            Judged(date, status.isMet, status == DayStatus.MINIMUM)
        }.toList()

    private fun judgedPeriods(goal: Goal, statuses: Map<LocalDate, DayStatus>, today: LocalDate, neutral: Set<LocalDate>): List<Judged> {
        val frequency = goal.schedule.frequency
        val first = Periods.of(frequency, goal.schedule.startDate) ?: return emptyList()
        return generateSequence(first) { Periods.of(frequency, it.end.plusDays(1)) }.takeWhile { it.start <= today }.mapNotNull { period ->
            val eligible = period.days().filter { it >= goal.schedule.startDate && it !in neutral }
            if (eligible.isEmpty()) return@mapNotNull null
            val needed = minOf(QuotaRules.required(goal.schedule, period), eligible.size)
            val metDays = period.days().filter { statuses[it]?.isMet == true }
            val isMet = metDays.size >= needed
            if (today in period && !isMet) return@mapNotNull null
            Judged(period.end, isMet, metDays.isNotEmpty() && metDays.all { statuses[it] == DayStatus.MINIMUM })
        }.toList()
    }
}
