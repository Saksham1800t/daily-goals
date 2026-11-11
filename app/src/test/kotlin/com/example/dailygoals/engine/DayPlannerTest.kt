package com.example.dailygoals.engine

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlannerTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val start = LocalDate.of(2026, 11, 1)
    private val now = today.atTime(6, 42)

    private fun goal(id: Long, name: String, window: TimeWindow? = null) =
        Goal(id = id, name = name, sortOrder = id.toInt(), schedule = GoalSchedule(window = window, startDate = start))

    private val water = goal(1, "Drink water")
    private val wake = goal(2, "Wake up early", TimeWindow(360, 420))
    private val study = goal(3, "Study")
    private val gym = goal(4, "Gym", TimeWindow(1080, 1200))
    private val goals = listOf(water, wake, study, gym)

    @Test
    fun itemsAreGroupedByUrgencyThenWindowStart() {
        val checkIns = listOf(CheckIn(1, today, DayStatus.DONE), CheckIn(3, today, DayStatus.FAILED))
        val plan = DayPlanner.plan(goals, checkIns, today, now)
        assertEquals(listOf("Study", "Wake up early", "Gym", "Drink water"), plan.items.map { it.goal.name })
        assertEquals(
            listOf(DaySection.MISSED, DaySection.NOW, DaySection.LATER, DaySection.KEPT),
            plan.items.map { it.section },
        )
        assertEquals(DaySummary(total = 4, kept = 1, failed = 1), plan.summary)
        assertEquals(18, plan.items[1].minutesLeft)
        assertEquals(420, plan.nextDeadlineMinute)
        assertTrue(plan.editable)
    }

    @Test
    fun tickedWindowNoLongerCountsAsADeadline() {
        val plan = DayPlanner.plan(goals, listOf(CheckIn(2, today, DayStatus.DONE)), today, now)
        assertEquals(1200, plan.nextDeadlineMinute)
    }

    @Test
    fun checkInsFromOtherDatesAreIgnored() {
        val plan = DayPlanner.plan(goals, listOf(CheckIn(1, today.minusDays(1), DayStatus.DONE)), today, now)
        assertEquals(0, plan.summary.kept)
    }

    @Test
    fun futureDateIsViewOnlyAndHasNoDeadline() {
        val plan = DayPlanner.plan(goals, emptyList(), today.plusDays(1), now)
        assertFalse(plan.editable)
        assertNull(plan.nextDeadlineMinute)
        assertTrue(plan.items.all { it.section == DaySection.LATER })
    }

    @Test
    fun goalsNotDueOrNotStartedAreLeftOut() {
        val mondays = Goal(
            id = 5,
            name = "Weekly review",
            schedule = GoalSchedule(Frequency.WEEKDAYS, setOf(DayOfWeek.MONDAY), startDate = start),
        )
        val future = goal(6, "Starts later").let { it.copy(schedule = it.schedule.copy(startDate = today.plusDays(1))) }
        val plan = DayPlanner.plan(listOf(mondays, future), emptyList(), today, now)
        assertTrue(plan.items.isEmpty())
        assertEquals(DaySummary.EMPTY, plan.summary)
    }

    @Test
    fun archivedGoalStaysInHistoryOnlyWhereItHasAResult() {
        val archived = gym.copy(state = GoalState.ARCHIVED)
        val yesterday = today.minusDays(1)
        val checkIns = listOf(CheckIn(4, yesterday, DayStatus.FAILED))
        assertEquals(1, DayPlanner.plan(listOf(archived), checkIns, yesterday, now).items.size)
        assertTrue(DayPlanner.plan(listOf(archived), checkIns, today, now).items.isEmpty())
    }

    @Test
    fun summariesCoverEachRequestedDate() {
        val yesterday = today.minusDays(1)
        val checkIns = listOf(
            CheckIn(1, yesterday, DayStatus.DONE),
            CheckIn(2, yesterday, DayStatus.FAILED),
            CheckIn(1, today, DayStatus.DONE),
        )
        val result = DayPlanner.summaries(goals, checkIns, listOf(yesterday, today, start.minusDays(1)))
        assertEquals(DaySummary(total = 4, kept = 1, failed = 1), result[yesterday])
        assertEquals(DaySummary(total = 4, kept = 1, failed = 0), result[today])
        assertEquals(DaySummary.EMPTY, result[start.minusDays(1)])
    }
}
