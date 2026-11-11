package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DueDatesTest {
    private val start = LocalDate.of(2026, 11, 9)
    private val daily = GoalSchedule(startDate = start)
    private val weekdays = GoalSchedule(
        frequency = Frequency.WEEKDAYS,
        activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        startDate = start,
    )

    @Test
    fun dailyGoalIsDueEveryDayFromItsStartDate() {
        assertTrue(DueDates.isDue(daily, start))
        assertTrue(DueDates.isDue(daily, start.plusDays(400)))
    }

    @Test
    fun goalIsNotDueBeforeItsStartDate() {
        assertFalse(DueDates.isDue(daily, start.minusDays(1)))
    }

    @Test
    fun weekdayGoalIsDueOnlyOnChosenDays() {
        assertTrue(DueDates.isDue(weekdays, LocalDate.of(2026, 11, 9)))
        assertFalse(DueDates.isDue(weekdays, LocalDate.of(2026, 11, 10)))
        assertTrue(DueDates.isDue(weekdays, LocalDate.of(2026, 11, 11)))
        assertFalse(DueDates.isDue(weekdays, LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun weekdayGoalIsDueOnLeapDayWhenItFallsOnAChosenDay() {
        val leapDay = LocalDate.of(2028, 2, 29)
        val schedule = weekdays.copy(activeDays = setOf(leapDay.dayOfWeek))
        assertTrue(DueDates.isDue(schedule, leapDay))
    }

    @Test
    fun archivedGoalShowsOnlyWhereAResultWasRecorded() {
        val goal = Goal(name = "Gym", state = GoalState.ARCHIVED, schedule = daily)
        assertFalse(DueDates.showsOn(goal, start, DayStatus.PENDING))
        assertTrue(DueDates.showsOn(goal, start, DayStatus.DONE))
        assertTrue(DueDates.showsOn(goal, start, DayStatus.FAILED))
    }

    @Test
    fun recordedResultStaysVisibleOnADayThatIsNoLongerDue() {
        val goal = Goal(name = "Gym", schedule = weekdays)
        val tuesday = LocalDate.of(2026, 11, 10)
        assertFalse(DueDates.showsOn(goal, tuesday, DayStatus.PENDING))
        assertTrue(DueDates.showsOn(goal, tuesday, DayStatus.FAILED))
    }
}
