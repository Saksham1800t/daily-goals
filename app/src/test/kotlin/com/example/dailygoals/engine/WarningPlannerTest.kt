package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalReminder
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.TimeWindow
import com.example.dailygoals.model.WarningMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningPlannerTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 11, 11)
    private val dayEnd = 23 * 60 + 59

    private fun goal(
        window: TimeWindow? = TimeWindow(6 * 60, 7 * 60),
        discipline: GoalDiscipline = GoalDiscipline(failOnWindowEnd = true),
        reminder: GoalReminder = GoalReminder(WarningMode.REPEATING, intervalMinutes = 10),
        schedule: GoalSchedule = GoalSchedule(window = window, startDate = date.minusDays(10)),
    ) = Goal(id = 1, name = "Wake up early", schedule = schedule.copy(window = window), discipline = discipline, reminder = reminder)

    private fun at(hour: Int, minute: Int, day: LocalDate = date): Instant = day.atTime(hour, minute).toInstant(ZoneOffset.UTC)

    private fun times(goal: Goal) = WarningPlanner.timesFor(goal, date, utc, dayEnd)

    @Test
    fun repeatingWarningsRunFromTheWindowStartUntilTheGraceDeadline() {
        val events = times(goal())
        assertEquals(7, events.size)
        assertEquals(at(6, 0), events.first().at)
        assertEquals(at(7, 0), events.last().at)
        assertEquals((0..6).toList(), events.map { it.index })
    }

    @Test
    fun aOnceWarningFiresOnlyAtTheWindowStartAndNoneNeverWarns() {
        assertEquals(listOf(at(6, 0)), times(goal(reminder = GoalReminder(WarningMode.ONCE))).map { it.at })
        assertEquals(emptyList<WarningTime>(), times(goal(reminder = GoalReminder(WarningMode.NONE))))
    }

    @Test
    fun withoutAWindowEndRuleWarningsContinueUntilTheDayEnds() {
        val events = times(goal(discipline = GoalDiscipline()))
        assertEquals(108, events.size)
        assertEquals(at(23, 50), events.last().at)
    }

    @Test
    fun anAllDayGoalWarnsFromItsCheckTime() {
        val reminder = GoalReminder(WarningMode.REPEATING, intervalMinutes = 30, allDayMinute = 20 * 60)
        val events = times(goal(window = null, discipline = GoalDiscipline(), reminder = reminder))
        assertEquals(8, events.size)
        assertEquals(at(20, 0), events.first().at)
        assertEquals(at(23, 30), events.last().at)
    }

    @Test
    fun wordingGetsFirmerAndCountsDownTheGraceTime() {
        val withRule = goal()
        assertEquals(WarningKind.Start, WarningPlanner.kindFor(withRule, date, 0, at(6, 0), utc, dayEnd))
        assertEquals(WarningKind.StillOpen, WarningPlanner.kindFor(withRule, date, 1, at(6, 10), utc, dayEnd))
        assertEquals(WarningKind.Grace(5), WarningPlanner.kindFor(withRule, date, 6, at(7, 0), utc, dayEnd))
        assertEquals(WarningKind.Grace(2), WarningPlanner.kindFor(withRule, date, 6, at(7, 3), utc, dayEnd))
        val noRule = goal(discipline = GoalDiscipline())
        assertEquals(WarningKind.Late(30), WarningPlanner.kindFor(noRule, date, 9, at(7, 30), utc, dayEnd))
    }

    private fun plan(
        goal: Goal,
        now: Instant,
        marks: Map<Pair<Long, LocalDate>, Int> = emptyMap(),
        status: DayStatus = DayStatus.PENDING,
    ) = WarningPlanner.plan(listOf(goal), { _, _ -> status }, marks, now.atZone(utc).toLocalDateTime(), utc, dayEnd)

    @Test
    fun theLatestDueWarningIsShownAndTheNextOneIsTheWakeUp() {
        val result = plan(goal(), at(6, 12))
        assertEquals(listOf(Warning(1, date, 1, WarningKind.StillOpen)), result.toFire)
        assertEquals(at(6, 20), result.nextWakeup)
    }

    @Test
    fun aWarningAlreadyShownIsNotShownAgain() {
        assertEquals(emptyList<Warning>(), plan(goal(), at(6, 12), marks = mapOf((1L to date) to 1)).toFire)
        assertEquals(1, plan(goal(), at(6, 12), marks = mapOf((1L to date) to 0)).toFire.single().index)
    }

    @Test
    fun aPhoneThatWasOffGetsOneWarningNotABurst() {
        val result = plan(goal(), at(6, 55))
        assertEquals(1, result.toFire.size)
        assertEquals(5, result.toFire.single().index)
    }

    @Test
    fun aTickedGoalStopsWarningAndOnlyTheMidnightRolloverRemains() {
        val result = plan(goal(), at(6, 12), status = DayStatus.DONE)
        assertEquals(emptyList<Warning>(), result.toFire)
        assertEquals(at(0, 0, date.plusDays(1)), result.nextWakeup)
    }

    @Test
    fun theAppWakesForTheFailureDeadlineAfterTheLastWarning() {
        val result = plan(goal(), at(7, 1))
        assertEquals(at(7, 5), result.nextWakeup)
    }

    @Test
    fun aDayEndRuleWakesTheAppAtTheDayEnd() {
        val allDay = goal(window = null, discipline = GoalDiscipline(failOnDayEnd = true), reminder = GoalReminder(WarningMode.NONE))
        assertEquals(at(23, 59), plan(allDay, at(12, 0)).nextWakeup)
    }

    @Test
    fun goalsNotDueArchivedOrQuotaGoalsAreLeftOut() {
        val notWednesday = goal(schedule = GoalSchedule(frequency = Frequency.WEEKDAYS, activeDays = setOf(DayOfWeek.MONDAY), startDate = date.minusDays(10)))
        assertEquals(emptyList<Warning>(), plan(notWednesday, at(6, 12)).toFire)
        assertEquals(emptyList<Warning>(), plan(goal().copy(state = GoalState.ARCHIVED), at(6, 12)).toFire)
        val weekly = goal(schedule = GoalSchedule(frequency = Frequency.PER_WEEK, count = 3, startDate = date.minusDays(10)))
        assertEquals(emptyList<Warning>(), plan(weekly, at(6, 12)).toFire)
    }

    @Test
    fun tomorrowsFirstWarningCanBeTheNextWakeUp() {
        val sleeper = goal(window = TimeWindow(0, 60), reminder = GoalReminder(WarningMode.ONCE))
        val result = plan(sleeper, at(1, 30), status = DayStatus.DONE)
        assertEquals(at(0, 0, date.plusDays(1)), result.nextWakeup)
        val pendingAfter = plan(sleeper, at(23, 59, date))
        assertTrue(pendingAfter.nextWakeup <= at(0, 0, date.plusDays(1)))
    }

    @Test
    fun aRepeatedHourGivesEveryWarningItsOwnKeyAndTwoAtTheSameLocalTime() {
        val newYork = ZoneId.of("America/New_York")
        val fallBack = LocalDate.of(2026, 11, 1)
        val night = goal(window = TimeWindow(30, 4 * 60), discipline = GoalDiscipline(), reminder = GoalReminder(WarningMode.REPEATING, intervalMinutes = 30))
            .let { it.copy(schedule = it.schedule.copy(startDate = fallBack.minusDays(5))) }
        val events = WarningPlanner.timesFor(night, fallBack, newYork, dayEnd)
        assertEquals(events.size, events.map { it.index }.toSet().size)
        assertEquals(events.size, events.map { it.at }.toSet().size)
        assertEquals(2, events.count { it.at.atZone(newYork).toLocalTime() == LocalTime.of(1, 30) })
    }
}
