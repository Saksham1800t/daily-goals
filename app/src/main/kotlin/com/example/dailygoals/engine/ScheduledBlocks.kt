package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A stretch of time in which blocks did not apply: the emergency pause (every app) or temporary access (one app). */
data class BlockLift(val startMillis: Long, val endMillis: Long, val packageName: String? = null)

enum class KeepOutcome { KEEP, ASK }

data class AutoKeep(val goalId: Long, val date: LocalDate, val outcome: KeepOutcome)

data class ScheduledPlan(val active: List<ActiveBlock>, val nextChange: Instant?)

/**
 * Scheduled blocking for avoid goals: the goal's apps are blocked during its window (or all day) on the
 * days it is due. It is not a punishment, so it ignores the punishment intensity and needs no failure.
 */
object ScheduledBlocks {
    private const val MINUTES_PER_DAY = 24 * 60

    /** Quota goals have no fixed days, so they cannot block on a schedule. */
    fun canOffer(goal: Goal): Boolean = goal.type == GoalType.AVOID && !goal.schedule.isQuota

    fun applies(goal: Goal): Boolean =
        canOffer(goal) && goal.state == GoalState.ACTIVE && goal.discipline.scheduledBlockEnabled && goal.avoidance.temptingPackages.isNotEmpty()

    /** When the goal blocks on [date], or null when it does not block that day. */
    fun span(goal: Goal, date: LocalDate, zone: ZoneId): Pair<Instant, Instant>? {
        if (!applies(goal) || !DueDates.isDue(goal.schedule, date)) return null
        val window = goal.schedule.window
        val start = LocalTimes.resolve(date, window?.startMinute ?: 0, zone).toInstant()
        val end = LocalTimes.resolve(date, window?.endMinute ?: MINUTES_PER_DAY, zone).toInstant()
        return start to end
    }

    /**
     * The blocks that must be in force at [now] and the next moment one starts or ends. An all-day block
     * that continues straight into the next day gets one end time, so there is no gap at midnight.
     */
    fun plan(goals: List<Goal>, now: LocalDateTime, zone: ZoneId): ScheduledPlan {
        val instant = now.atZone(zone).toInstant()
        val today = now.toLocalDate()
        val active = mutableListOf<ActiveBlock>()
        val changes = mutableListOf<Instant>()
        goals.forEach { goal ->
            val current = span(goal, today, zone)
            val next = span(goal, today.plusDays(1), zone)
            listOfNotNull(current, next).forEach { changes += listOf(it.first, it.second) }
            if (current == null || instant < current.first || instant >= current.second) return@forEach
            val end = if (next != null && next.first == current.second) next.second else current.second
            goal.avoidance.temptingPackages.forEach { pkg ->
                active += ActiveBlock(goal.id, goal.name, pkg, BlockKind.SCHEDULED, end.toEpochMilli(), goal.discipline.blockMessage, today.toString(), goal.discipline.allowTempAccess)
            }
        }
        return ScheduledPlan(active, changes.filter { it > instant }.minOrNull())
    }

    /** When the day can be settled: the window end, or the day end for an all-day block. */
    fun keepMoment(goal: Goal, date: LocalDate, zone: ZoneId, dayEndMinute: Int): Instant =
        LocalTimes.resolve(date, goal.schedule.window?.endMinute ?: dayEndMinute, zone).toInstant()

    /**
     * Days that got through their blocked time untouched become kept on their own. When the pause or a
     * temporary access lifted the block during that time the app cannot know, so the user is asked.
     */
    fun autoKeeps(
        goals: List<Goal>,
        statusOf: (goalId: Long, date: LocalDate) -> DayStatus,
        now: LocalDateTime,
        zone: ZoneId,
        dayEndMinute: Int,
        finalisedThrough: LocalDate?,
        neutralDates: Set<LocalDate>,
        lifts: List<BlockLift>,
    ): List<AutoKeep> {
        val instant = now.atZone(zone).toInstant()
        val today = now.toLocalDate()
        return goals.filter { applies(it) && it.discipline.autoKeepAtWindowEnd }.flatMap { goal ->
            val first = maxOf(goal.schedule.startDate, finalisedThrough?.plusDays(1) ?: goal.schedule.startDate)
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= today }.mapNotNull { date ->
                if (date in neutralDates || statusOf(goal.id, date) != DayStatus.PENDING) return@mapNotNull null
                val span = span(goal, date, zone) ?: return@mapNotNull null
                val settle = keepMoment(goal, date, zone, dayEndMinute)
                if (instant < settle) return@mapNotNull null
                val lifted = lifts.any { lift ->
                    (lift.packageName == null || lift.packageName in goal.avoidance.temptingPackages) &&
                        lift.startMillis < settle.toEpochMilli() && lift.endMillis > span.first.toEpochMilli()
                }
                AutoKeep(goal.id, date, if (lifted) KeepOutcome.ASK else KeepOutcome.KEEP)
            }.toList()
        }
    }
}
