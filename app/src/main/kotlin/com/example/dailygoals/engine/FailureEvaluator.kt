package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class NewFailure(val goalId: Long, val date: LocalDate, val reason: FailReason)

/** Decides when the window-end and day-end rules fail a goal. The manual rule is a user action. */
object FailureEvaluator {
    /**
     * The rule that has fired for [goal] on [date] at [now], or null. The window-end rule fires at the
     * window end plus the grace period; the day-end rule fires at [dayEndMinute] and has no grace.
     * Quota goals are judged per period by [QuotaRules], never here.
     */
    fun firedReason(goal: Goal, date: LocalDate, now: LocalDateTime, zone: ZoneId, dayEndMinute: Int): FailReason? {
        if (goal.schedule.isQuota) return null
        val nowInstant = now.atZone(zone).toInstant()
        val candidates = buildList<Pair<Instant, FailReason>> {
            val window = goal.schedule.window
            if (goal.discipline.failOnWindowEnd && window != null) {
                val deadline = LocalTimes.resolve(date, window.endMinute, zone).plusMinutes(goal.schedule.graceMinutes.toLong())
                add(deadline.toInstant() to FailReason.WINDOW_END)
            }
            if (goal.discipline.failOnDayEnd) {
                add(LocalTimes.resolve(date, dayEndMinute, zone).toInstant() to FailReason.DAY_END)
            }
        }
        return candidates.filter { !nowInstant.isBefore(it.first) }.minByOrNull { it.first }?.second
    }

    /**
     * Failures to record now. Only dates after [finalisedThrough] are examined, so a date that was
     * already closed is never reopened, and [neutralDates] (skipped by a time zone change) never fail.
     */
    fun newFailures(
        goals: List<Goal>,
        statusOf: (goalId: Long, date: LocalDate) -> DayStatus,
        now: LocalDateTime,
        zone: ZoneId,
        dayEndMinute: Int,
        finalisedThrough: LocalDate?,
        neutralDates: Set<LocalDate>,
    ): List<NewFailure> {
        val today = now.toLocalDate()
        return goals.filter { it.state == GoalState.ACTIVE && !it.schedule.isQuota }.flatMap { goal ->
            val first = maxOf(goal.schedule.startDate, finalisedThrough?.plusDays(1) ?: goal.schedule.startDate)
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= today }.mapNotNull { date ->
                if (date in neutralDates || !DueDates.isDue(goal.schedule, date)) return@mapNotNull null
                if (statusOf(goal.id, date) != DayStatus.PENDING) return@mapNotNull null
                firedReason(goal, date, now, zone, dayEndMinute)?.let { NewFailure(goal.id, date, it) }
            }.toList()
        }
    }

    /** The latest date that can be treated as closed after an evaluation at [now]. */
    fun finalisedAfter(now: LocalDateTime, previous: LocalDate?): LocalDate {
        val closed = now.toLocalDate().minusDays(1)
        return if (previous != null && previous > closed) previous else closed
    }
}
