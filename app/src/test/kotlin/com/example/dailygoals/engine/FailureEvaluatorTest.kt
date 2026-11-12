package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.TimeWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureEvaluatorTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 11, 11)
    private val dayEnd = 23 * 60 + 59
    private val window = TimeWindow(6 * 60, 7 * 60)

    private fun goal(
        discipline: GoalDiscipline,
        window: TimeWindow? = this.window,
        grace: Int = 5,
        start: LocalDate = date.minusDays(10),
    ) = Goal(
        id = 1,
        name = "Wake up early",
        schedule = GoalSchedule(window = window, startDate = start, graceMinutes = grace),
        discipline = discipline,
    )

    private fun fired(goal: Goal, hour: Int, minute: Int, day: LocalDate = date) =
        FailureEvaluator.firedReason(goal, day, day.atTime(hour, minute), utc, dayEnd)

    @Test
    fun windowEndRuleFiresAtTheEndPlusGrace() {
        val goal = goal(GoalDiscipline(failOnWindowEnd = true))
        assertNull(fired(goal, 7, 4))
        assertEquals(FailReason.WINDOW_END, fired(goal, 7, 5))
    }

    @Test
    fun gracePeriodsOfZeroAndFifteenMoveTheDeadline() {
        val rule = GoalDiscipline(failOnWindowEnd = true)
        assertEquals(FailReason.WINDOW_END, fired(goal(rule, grace = 0), 7, 0))
        assertNull(fired(goal(rule, grace = 15), 7, 14))
        assertEquals(FailReason.WINDOW_END, fired(goal(rule, grace = 15), 7, 15))
    }

    @Test
    fun dayEndRuleHasNoGraceAndFiresAtTheDayEndTime() {
        val goal = goal(GoalDiscipline(failOnDayEnd = true), window = null)
        assertNull(fired(goal, 23, 58))
        assertEquals(FailReason.DAY_END, fired(goal, 23, 59))
    }

    @Test
    fun theEarlierOfTwoRulesGivesTheReason() {
        val goal = goal(GoalDiscipline(failOnWindowEnd = true, failOnDayEnd = true))
        assertEquals(FailReason.WINDOW_END, fired(goal, 12, 0))
        assertEquals(FailReason.WINDOW_END, fired(goal, 23, 59))
    }

    @Test
    fun noRuleOrOnlyTheManualRuleNeverFiresOnItsOwn() {
        assertNull(fired(goal(GoalDiscipline()), 23, 59))
        assertNull(fired(goal(GoalDiscipline(failOnManual = true)), 23, 59))
    }

    @Test
    fun quotaGoalsAreLeftToTheQuotaRules() {
        val weekly = goal(GoalDiscipline(failOnDayEnd = true)).let { it.copy(schedule = it.schedule.copy(frequency = Frequency.PER_WEEK)) }
        assertNull(fired(weekly, 23, 59))
    }

    @Test
    fun aWindowEndInsideADaylightSavingGapFiresOnceAtTheFirstValidMoment() {
        val newYork = ZoneId.of("America/New_York")
        val gapDay = LocalDate.of(2026, 3, 8)
        val goal = goal(GoalDiscipline(failOnWindowEnd = true), window = TimeWindow(60, 2 * 60 + 30), grace = 0)
        assertNull(FailureEvaluator.firedReason(goal, gapDay, gapDay.atTime(3, 29), newYork, dayEnd))
        assertEquals(FailReason.WINDOW_END, FailureEvaluator.firedReason(goal, gapDay, gapDay.atTime(3, 30), newYork, dayEnd))
    }

    @Test
    fun aWindowEndInARepeatedHourProducesOneFailurePerDate() {
        val newYork = ZoneId.of("America/New_York")
        val repeatDay = LocalDate.of(2026, 11, 1)
        val goal = goal(GoalDiscipline(failOnWindowEnd = true), window = TimeWindow(0, 60 + 30), grace = 0, start = repeatDay)
        val failures = FailureEvaluator.newFailures(
            goals = listOf(goal),
            statusOf = { _, _ -> DayStatus.PENDING },
            now = repeatDay.atTime(1, 30),
            zone = newYork,
            dayEndMinute = dayEnd,
            finalisedThrough = null,
            neutralDates = emptySet(),
        )
        assertEquals(listOf(NewFailure(1, repeatDay, FailReason.WINDOW_END)), failures)
    }

    private fun failures(
        goal: Goal,
        now: LocalDateTime,
        finalisedThrough: LocalDate? = null,
        neutral: Set<LocalDate> = emptySet(),
        status: (Long, LocalDate) -> DayStatus = { _, _ -> DayStatus.PENDING },
    ) = FailureEvaluator.newFailures(listOf(goal), status, now, utc, dayEnd, finalisedThrough, neutral)

    @Test
    fun everyOpenPastDateFailsOnTheFirstRun() {
        val goal = goal(GoalDiscipline(failOnDayEnd = true), window = null, start = date.minusDays(3))
        val result = failures(goal, date.atTime(8, 0))
        assertEquals(listOf(date.minusDays(3), date.minusDays(2), date.minusDays(1)), result.map { it.date })
    }

    @Test
    fun finalisedDatesAreNeverReopened() {
        val goal = goal(GoalDiscipline(failOnDayEnd = true), window = null, start = date.minusDays(3))
        val result = failures(goal, date.atTime(8, 0), finalisedThrough = date.minusDays(2))
        assertEquals(listOf(date.minusDays(1)), result.map { it.date })
    }

    @Test
    fun tickedDatesAndNeutralDatesDoNotFail() {
        val goal = goal(GoalDiscipline(failOnDayEnd = true), window = null, start = date.minusDays(3))
        val result = failures(
            goal,
            date.atTime(8, 0),
            neutral = setOf(date.minusDays(3)),
            status = { _, d -> if (d == date.minusDays(2)) DayStatus.DONE else DayStatus.PENDING },
        )
        assertEquals(listOf(date.minusDays(1)), result.map { it.date })
    }

    @Test
    fun notDueDatesAndArchivedGoalsDoNotFail() {
        val onlyWednesday = goal(GoalDiscipline(failOnDayEnd = true), window = null, start = date.minusDays(6)).let {
            it.copy(schedule = it.schedule.copy(frequency = Frequency.WEEKDAYS, activeDays = setOf(date.dayOfWeek)))
        }
        assertEquals(emptyList<NewFailure>(), failures(onlyWednesday, date.atTime(8, 0)))
        val archived = goal(GoalDiscipline(failOnDayEnd = true), window = null).copy(state = GoalState.ARCHIVED)
        assertEquals(emptyList<NewFailure>(), failures(archived, date.atTime(23, 59)))
    }

    @Test
    fun finalisedHighWaterMarkNeverMovesBackwardWhenTheClockDoes() {
        assertEquals(date.minusDays(1), FailureEvaluator.finalisedAfter(date.atTime(8, 0), null))
        assertEquals(date.minusDays(1), FailureEvaluator.finalisedAfter(date.atTime(8, 0), date.minusDays(5)))
        assertEquals(date.plusDays(1), FailureEvaluator.finalisedAfter(date.atTime(8, 0), date.plusDays(1)))
    }

    @Test
    fun aClockMovedBackToAFinalisedDateCreatesNoFailures() {
        val goal = goal(GoalDiscipline(failOnDayEnd = true), window = null, start = date.minusDays(3))
        val result = failures(goal, date.atTime(23, 59), finalisedThrough = date)
        assertEquals(emptyList<NewFailure>(), result)
    }
}
