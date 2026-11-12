package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.WarningMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What a warning should say; the wording itself lives with the notification code. */
sealed interface WarningKind {
    data object Start : WarningKind
    data object StillOpen : WarningKind
    data class Grace(val minutesLeft: Int) : WarningKind
    data class Late(val minutesLate: Int) : WarningKind
}

data class WarningTime(val index: Int, val at: Instant)

data class Warning(val goalId: Long, val date: LocalDate, val index: Int, val kind: WarningKind)

data class WarningPlan(val toFire: List<Warning>, val nextWakeup: Instant)

/**
 * Warnings follow each goal's own window and keep going until the goal is ticked, fails, or the day
 * ends. Only the next event is ever scheduled, and every event has a dedupe key (goal, date, index) so
 * it fires once even across reboots, repeated hours and re-runs.
 */
object WarningPlanner {
    private const val MAX_EVENTS = 400
    private const val SECONDS_PER_MINUTE = 60L

    /** Start time of the first warning on [date], or null when the goal does not warn. */
    private fun firstWarning(goal: Goal, date: LocalDate, zone: ZoneId): Instant? {
        val reminder = goal.reminder
        if (reminder.mode == WarningMode.NONE) return null
        val minute = goal.schedule.window?.startMinute ?: reminder.allDayMinute
        return LocalTimes.resolve(date, minute, zone).toInstant()
    }

    /** The warnings stop at the failure deadline when a rule will fail the goal, otherwise at day end. */
    fun stopsAt(goal: Goal, date: LocalDate, zone: ZoneId, dayEndMinute: Int): Instant {
        val dayEnd = LocalTimes.resolve(date, dayEndMinute, zone).toInstant()
        val window = goal.schedule.window
        if (goal.discipline.failOnWindowEnd && window != null) {
            val deadline = LocalTimes.resolve(date, window.endMinute, zone).plusMinutes(goal.schedule.graceMinutes.toLong()).toInstant()
            return minOf(deadline, if (goal.discipline.failOnDayEnd) dayEnd else deadline)
        }
        return dayEnd
    }

    fun timesFor(goal: Goal, date: LocalDate, zone: ZoneId, dayEndMinute: Int): List<WarningTime> {
        val first = firstWarning(goal, date, zone) ?: return emptyList()
        val stop = stopsAt(goal, date, zone, dayEndMinute)
        val step = Duration.ofMinutes(goal.reminder.intervalMinutes.coerceAtLeast(1).toLong())
        val repeating = goal.reminder.mode == WarningMode.REPEATING
        return generateSequence(first) { if (repeating) it.plus(step) else null }
            .takeWhile { it.isBefore(stop) }
            .take(MAX_EVENTS)
            .mapIndexed { index, at -> WarningTime(index, at) }
            .toList()
    }

    fun kindFor(goal: Goal, date: LocalDate, index: Int, at: Instant, zone: ZoneId, dayEndMinute: Int): WarningKind {
        val window = goal.schedule.window
        if (window != null) {
            val end = LocalTimes.resolve(date, window.endMinute, zone).toInstant()
            if (!at.isBefore(end)) {
                return if (goal.discipline.failOnWindowEnd) {
                    val seconds = Duration.between(at, stopsAt(goal, date, zone, dayEndMinute)).seconds
                    WarningKind.Grace(((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1))
                } else {
                    WarningKind.Late(Duration.between(end, at).toMinutes().toInt())
                }
            }
        }
        return if (index == 0) WarningKind.Start else WarningKind.StillOpen
    }

    /**
     * [marks] holds the last index already shown per goal and date. For each goal only the latest due
     * event is shown, so a phone that was off does not get a burst of old warnings. Dates after a
     * closed one are not touched: only today and tomorrow's first event are looked at.
     */
    fun plan(
        goals: List<Goal>,
        statusOf: (goalId: Long, date: LocalDate) -> DayStatus,
        marks: Map<Pair<Long, LocalDate>, Int>,
        now: LocalDateTime,
        zone: ZoneId,
        dayEndMinute: Int,
    ): WarningPlan {
        val nowInstant = now.atZone(zone).toInstant()
        val today = now.toLocalDate()
        val fire = mutableListOf<Warning>()
        var next = LocalTimes.resolve(today.plusDays(1), 0, zone).toInstant()
        goals.filter { it.state == GoalState.ACTIVE && !it.schedule.isQuota }.forEach { goal ->
            listOf(today, today.plusDays(1)).forEach { date ->
                if (!DueDates.isDue(goal.schedule, date) || statusOf(goal.id, date) != DayStatus.PENDING) return@forEach
                val times = timesFor(goal, date, zone, dayEndMinute)
                if (date == today) {
                    val lastShown = marks[goal.id to date] ?: -1
                    times.lastOrNull { !it.at.isAfter(nowInstant) && it.index > lastShown }?.let {
                        fire += Warning(goal.id, date, it.index, kindFor(goal, date, it.index, nowInstant, zone, dayEndMinute))
                    }
                }
                times.firstOrNull { it.at.isAfter(nowInstant) }?.let { if (it.at.isBefore(next)) next = it.at }
                if (date == today) {
                    deadlines(goal, date, zone, dayEndMinute).filter { it.isAfter(nowInstant) }.forEach { if (it.isBefore(next)) next = it }
                }
            }
        }
        return WarningPlan(fire, next)
    }

    /** The moments a failure rule can fire, so the app wakes up for them even while closed. */
    fun deadlines(goal: Goal, date: LocalDate, zone: ZoneId, dayEndMinute: Int): List<Instant> = buildList {
        val window = goal.schedule.window
        if (goal.discipline.failOnWindowEnd && window != null) {
            add(LocalTimes.resolve(date, window.endMinute, zone).plusMinutes(goal.schedule.graceMinutes.toLong()).toInstant())
        }
        if (goal.discipline.failOnDayEnd) add(LocalTimes.resolve(date, dayEndMinute, zone).toInstant())
    }
}
