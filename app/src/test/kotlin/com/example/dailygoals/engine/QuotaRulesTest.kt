package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.QuotaFailureMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaRulesTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val week = Period(monday, monday.plusDays(6))
    private val dayEnd = 23 * 60 + 59

    private fun gym(mode: QuotaFailureMode, count: Int = 4) = GoalSchedule(
        frequency = Frequency.PER_WEEK,
        count = count,
        quotaFailureMode = mode,
        startDate = LocalDate.of(2026, 11, 1),
    )

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

    private fun judge(
        schedule: GoalSchedule,
        met: Set<LocalDate>,
        now: LocalDateTime,
        neutral: Set<LocalDate> = emptySet(),
    ) = QuotaRules.evaluate(schedule, week, met, neutral, now, dayEnd)

    @Test
    fun missingTuesdayIsNotAFailureWhileFourCompletionsAreStillReachable() {
        val outcome = judge(gym(QuotaFailureMode.WHEN_UNREACHABLE), setOf(monday), at(11, 8))
        assertEquals(PeriodStatus.OPEN, outcome.status)
        assertEquals(1, outcome.completed)
    }

    @Test
    fun unreachableModeFailsOnTheFirstDayTheCountCannotBeMet() {
        val schedule = gym(QuotaFailureMode.WHEN_UNREACHABLE)
        assertEquals(PeriodStatus.OPEN, judge(schedule, emptySet(), at(12, 8)).status)
        val failed = judge(schedule, emptySet(), at(13, 8))
        assertEquals(PeriodStatus.FAILED, failed.status)
        assertEquals(LocalDate.of(2026, 11, 12), failed.failedOn)
    }

    @Test
    fun endOfPeriodModeFailsOnlyAfterTheLastDayEnds() {
        val schedule = gym(QuotaFailureMode.END_OF_PERIOD)
        assertEquals(PeriodStatus.OPEN, judge(schedule, emptySet(), at(13, 8)).status)
        assertEquals(PeriodStatus.OPEN, judge(schedule, emptySet(), at(15, 12)).status)
        val failed = judge(schedule, emptySet(), at(15, 23, 59))
        assertEquals(PeriodStatus.FAILED, failed.status)
        assertEquals(LocalDate.of(2026, 11, 15), failed.failedOn)
    }

    @Test
    fun reachingTheCountMeetsThePeriodAtOnce() {
        val met = (9..12).map { LocalDate.of(2026, 11, it) }.toSet()
        assertEquals(PeriodStatus.MET, judge(gym(QuotaFailureMode.WHEN_UNREACHABLE), met, at(12, 9)).status)
    }

    @Test
    fun pausedMondayToWednesdayStillNeedsFourButMondayToThursdayNeedsThree() {
        val schedule = gym(QuotaFailureMode.END_OF_PERIOD)
        val pausedThreeDays = (9..11).map { LocalDate.of(2026, 11, it) }.toSet()
        val pausedFourDays = (9..12).map { LocalDate.of(2026, 11, it) }.toSet()
        assertEquals(4, judge(schedule, emptySet(), at(13, 8), pausedThreeDays).adjustedRequired)
        val fourDays = judge(schedule, emptySet(), at(13, 8), pausedFourDays)
        assertEquals(3, fourDays.adjustedRequired)
        assertEquals(4, fourDays.originalRequired)
        assertEquals(3, fourDays.eligibleDays)
    }

    @Test
    fun aFullyPausedPeriodIsSkippedNotFailed() {
        val outcome = judge(gym(QuotaFailureMode.WHEN_UNREACHABLE), emptySet(), at(16, 8), week.days().toSet())
        assertEquals(PeriodStatus.SKIPPED, outcome.status)
        assertNull(outcome.failedOn)
        assertEquals(0, outcome.adjustedRequired)
    }

    @Test
    fun completionsMadeBeforeAPauseStillCount() {
        val outcome = judge(gym(QuotaFailureMode.END_OF_PERIOD), setOf(monday), at(13, 8), setOf(LocalDate.of(2026, 11, 10)))
        assertEquals(1, outcome.completed)
        assertEquals(PeriodStatus.OPEN, outcome.status)
    }

    @Test
    fun requirementIsCappedByTheLengthOfThePeriod() {
        val monthly = GoalSchedule(frequency = Frequency.PER_MONTH, count = 40, startDate = LocalDate.of(2026, 11, 1))
        assertEquals(30, QuotaRules.required(monthly, Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))))
        assertEquals(7, QuotaRules.required(gym(QuotaFailureMode.WHEN_UNREACHABLE, count = 9), week))
    }

    @Test
    fun daysBeforeTheStartDateAreNotEligible() {
        val late = gym(QuotaFailureMode.END_OF_PERIOD).copy(startDate = LocalDate.of(2026, 11, 13))
        val outcome = judge(late, emptySet(), at(14, 8))
        assertEquals(3, outcome.eligibleDays)
        assertEquals(3, outcome.adjustedRequired)
    }
}
