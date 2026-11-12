package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(n: Int) = LocalDate.of(2026, 11, n)

    private fun daily(start: LocalDate = day(1)) = Goal(id = 1, name = "Study", schedule = GoalSchedule(startDate = start))

    private val sample: Map<LocalDate, DayStatus> = buildMap {
        (1..5).forEach { put(day(it), DayStatus.DONE) }
        put(day(6), DayStatus.FAILED)
        (7..9).forEach { put(day(it), DayStatus.DONE) }
        put(day(10), DayStatus.MINIMUM)
    }

    @Test
    fun percentStreaksAndMinimumDaysAreCountedFromDueDays() {
        val stats = Consistency.forGoal(daily(), sample, today)
        assertEquals(10, stats.due)
        assertEquals(9, stats.met)
        assertEquals(90, stats.percent)
        assertEquals(1, stats.minimumOnly)
        assertEquals(5, stats.bestStreak)
        assertEquals(4, stats.currentStreak)
        assertFalse(stats.streakBroken)
    }

    @Test
    fun todayIsNotJudgedUntilItHasAResult() {
        assertEquals(10, Consistency.forGoal(daily(), sample, today).due)
        val ticked = sample + (today to DayStatus.DONE)
        val stats = Consistency.forGoal(daily(), ticked, today)
        assertEquals(11, stats.due)
        assertEquals(5, stats.currentStreak)
    }

    @Test
    fun aMissedLastDayBreaksTheStreakAndKeepsTheBest() {
        val broken = sample + (day(10) to DayStatus.FAILED)
        val stats = Consistency.forGoal(daily(), broken, today)
        assertEquals(0, stats.currentStreak)
        assertEquals(5, stats.bestStreak)
        assertTrue(stats.streakBroken)
    }

    @Test
    fun notDueAndNeutralDaysAreLeftOutAndDoNotBreakTheStreak() {
        val goal = daily().let { it.copy(schedule = it.schedule.copy(frequency = Frequency.WEEKDAYS, activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))) }
        val statuses = mapOf(day(2) to DayStatus.DONE, day(4) to DayStatus.DONE, day(9) to DayStatus.DONE)
        val stats = Consistency.forGoal(goal, statuses, today, neutralDates = setOf(day(4)))
        assertEquals(2, stats.due)
        assertEquals(2, stats.met)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun onlyTheLastThirtyDaysCountTowardsThePercentage() {
        val start = today.minusDays(60)
        val statuses = generateSequence(start) { it.plusDays(1) }.takeWhile { it < today }
            .associateWith { if (it < today.minusDays(30)) DayStatus.FAILED else DayStatus.DONE }
        val stats = Consistency.forGoal(daily(start), statuses, today)
        assertEquals(29, stats.due)
        assertEquals(100, stats.percent)
        assertEquals(30, stats.bestStreak)
    }

    @Test
    fun aGoalWithNoDueDaysHasEmptyStats() {
        assertEquals(ConsistencyStats.EMPTY, Consistency.forGoal(daily(today), emptyMap(), today))
        assertEquals(0, ConsistencyStats.EMPTY.percent)
    }

    @Test
    fun quotaGoalsCountMetPeriodsAndSkipTheOpenOne() {
        val weekly = Goal(id = 2, name = "Gym", schedule = GoalSchedule(frequency = Frequency.PER_WEEK, count = 2, startDate = day(2)))
        val statuses = mapOf(day(3) to DayStatus.DONE, day(5) to DayStatus.MINIMUM, day(10) to DayStatus.DONE)
        val open = Consistency.forGoal(weekly, statuses, today)
        assertEquals(1, open.due)
        assertEquals(1, open.met)
        assertEquals(1, open.currentStreak)
        val metNow = Consistency.forGoal(weekly, statuses + (day(11) to DayStatus.DONE), today)
        assertEquals(2, metNow.due)
        assertEquals(2, metNow.currentStreak)
    }

    @Test
    fun aMissedQuotaWeekBreaksTheStreak() {
        val weekly = Goal(id = 2, name = "Gym", schedule = GoalSchedule(frequency = Frequency.PER_WEEK, count = 2, startDate = day(2)))
        val stats = Consistency.forGoal(weekly, mapOf(day(3) to DayStatus.DONE), today)
        assertEquals(1, stats.due)
        assertEquals(0, stats.met)
        assertEquals(0, stats.currentStreak)
    }
}
