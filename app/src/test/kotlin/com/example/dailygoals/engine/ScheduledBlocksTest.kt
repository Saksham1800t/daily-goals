package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalAvoidance
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledBlocksTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val friday = LocalDate.of(2026, 11, 13)
    private val insta = "com.instagram.android"

    private val work = Goal(
        id = 5,
        name = "No Instagram during work",
        type = GoalType.AVOID,
        schedule = GoalSchedule(
            frequency = Frequency.WEEKDAYS,
            activeDays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY,
            window = TimeWindow(540, 1080),
            startDate = friday.minusDays(7),
        ),
        discipline = GoalDiscipline(scheduledBlockEnabled = true),
        avoidance = GoalAvoidance(setOf(insta)),
    )
    private val allDay = work.copy(id = 6, name = "No games", schedule = GoalSchedule(startDate = friday.minusDays(7)))

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0) = at(date, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun plan(goal: Goal, now: LocalDateTime) = ScheduledBlocks.plan(listOf(goal), now, zone)

    @Test
    fun blocksInsideTheWindowOnADueDayUntilTheWindowEnds() {
        val result = plan(work, at(friday, 10))
        assertEquals(listOf(insta), result.active.map { it.packageName })
        assertEquals(BlockKind.SCHEDULED, result.active.single().kind)
        assertEquals(millis(friday, 18), result.active.single().endsAtMillis)
        assertEquals(millis(friday, 18), result.nextChange!!.toEpochMilli())
    }

    @Test
    fun opensNormallyOutsideTheWindowAndOnDaysThatAreNotDue() {
        assertTrue(plan(work, at(friday, 8, 59)).active.isEmpty())
        assertEquals(millis(friday, 9), plan(work, at(friday, 8, 59)).nextChange!!.toEpochMilli())
        assertTrue(plan(work, at(friday, 18)).active.isEmpty())
        assertTrue(plan(work, at(friday.plusDays(1), 10)).active.isEmpty())
    }

    @Test
    fun withNoWindowTheAppsAreBlockedAllDayWithNoGapAtMidnight() {
        val block = plan(allDay, at(friday, 23, 59)).active.single()
        assertEquals(millis(friday.plusDays(2), 0), block.endsAtMillis)
    }

    @Test
    fun itIgnoresPunishmentIntensityButNeedsTheSwitchAnAvoidGoalAndApps() {
        assertEquals(Intensity.NONE, work.discipline.intensity)
        assertTrue(ScheduledBlocks.applies(work))
        assertFalse(ScheduledBlocks.applies(work.copy(discipline = GoalDiscipline(scheduledBlockEnabled = false))))
        assertFalse(ScheduledBlocks.applies(work.copy(type = GoalType.DO)))
        assertFalse(ScheduledBlocks.applies(work.copy(avoidance = GoalAvoidance())))
        assertFalse(ScheduledBlocks.applies(work.copy(state = GoalState.ARCHIVED)))
    }

    @Test
    fun quotaGoalsAreNeverOfferedScheduledBlocking() {
        val weekly = work.copy(schedule = work.schedule.copy(frequency = Frequency.PER_WEEK, count = 3))
        assertFalse(ScheduledBlocks.canOffer(weekly))
        assertNull(ScheduledBlocks.span(weekly, friday, zone))
    }

    private fun keeps(goal: Goal, now: LocalDateTime, lifts: List<BlockLift> = emptyList(), status: DayStatus = DayStatus.PENDING) =
        ScheduledBlocks.autoKeeps(
            goals = listOf(goal),
            statusOf = { _, _ -> status },
            now = now,
            zone = zone,
            dayEndMinute = 1380,
            finalisedThrough = friday.minusDays(1),
            neutralDates = emptySet(),
            lifts = lifts,
        )

    @Test
    fun aGoalBecomesKeptAtWindowEndWhenNothingLiftedTheBlock() {
        assertTrue(keeps(work, at(friday, 17, 59)).isEmpty())
        assertEquals(listOf(AutoKeep(5, friday, KeepOutcome.KEEP)), keeps(work, at(friday, 18)))
    }

    @Test
    fun aPauseDuringTheWindowMeansTheUserIsAskedInstead() {
        val pause = BlockLift(millis(friday, 12), millis(friday, 12, 30))
        assertEquals(listOf(AutoKeep(5, friday, KeepOutcome.ASK)), keeps(work, at(friday, 18), listOf(pause)))
        val before = BlockLift(millis(friday, 7), millis(friday, 8))
        assertEquals(KeepOutcome.KEEP, keeps(work, at(friday, 18), listOf(before)).single().outcome)
    }

    @Test
    fun temporaryAccessToAnotherAppDoesNotCauseTheQuestion() {
        val other = BlockLift(millis(friday, 12), millis(friday, 12, 10), "com.other")
        val same = other.copy(packageName = insta)
        assertEquals(KeepOutcome.KEEP, keeps(work, at(friday, 18), listOf(other)).single().outcome)
        assertEquals(KeepOutcome.ASK, keeps(work, at(friday, 18), listOf(same)).single().outcome)
    }

    @Test
    fun aDayAlreadyAnsweredOrWithAutomaticKeepOffIsLeftAlone() {
        assertTrue(keeps(work, at(friday, 18), status = DayStatus.FAILED).isEmpty())
        val manual = work.copy(discipline = work.discipline.copy(autoKeepAtWindowEnd = false))
        assertTrue(keeps(manual, at(friday, 18)).isEmpty())
    }

    @Test
    fun anAllDayBlockSettlesAtTheDayEnd() {
        assertTrue(keeps(allDay, at(friday, 22, 59)).isEmpty())
        assertEquals(KeepOutcome.KEEP, keeps(allDay, at(friday, 23)).single().outcome)
    }
}
