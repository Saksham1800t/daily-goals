package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.TimeWindow
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaySectionsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val wake = TimeWindow(6 * 60, 7 * 60)

    private fun at(hour: Int, minute: Int): LocalDateTime = today.atTime(hour, minute)

    private fun section(window: TimeWindow?, time: LocalDateTime, date: LocalDate = today) =
        DaySections.sectionOf(window, DayStatus.PENDING, date, time)

    @Test
    fun pendingGoalMovesFromLaterToNowToOverdueAsItsWindowPasses() {
        assertEquals(DaySection.LATER, section(wake, at(5, 59)))
        assertEquals(DaySection.NOW, section(wake, at(6, 0)))
        assertEquals(DaySection.NOW, section(wake, at(6, 59)))
        assertEquals(DaySection.OVERDUE, section(wake, at(7, 0)))
    }

    @Test
    fun allDayGoalIsAnyTimeToday() {
        assertEquals(DaySection.ANY_TIME, section(null, at(12, 0)))
    }

    @Test
    fun resultDecidesTheSectionBeforeTime() {
        assertEquals(DaySection.MISSED, DaySections.sectionOf(wake, DayStatus.FAILED, today, at(6, 30)))
        assertEquals(DaySection.KEPT, DaySections.sectionOf(wake, DayStatus.DONE, today, at(6, 30)))
    }

    @Test
    fun pendingGoalsOnOtherDatesAreOverdueOrLater() {
        assertEquals(DaySection.OVERDUE, section(null, at(6, 30), today.minusDays(1)))
        assertEquals(DaySection.LATER, section(wake, at(6, 30), today.plusDays(1)))
    }

    @Test
    fun minutesLeftCountsDownOnlyWhileTheWindowIsOpenToday() {
        assertEquals(18, DaySections.minutesLeft(wake, DayStatus.PENDING, today, at(6, 42)))
        assertNull(DaySections.minutesLeft(wake, DayStatus.PENDING, today, at(7, 0)))
        assertNull(DaySections.minutesLeft(wake, DayStatus.DONE, today, at(6, 42)))
        assertNull(DaySections.minutesLeft(wake, DayStatus.PENDING, today.plusDays(1), at(6, 42)))
        assertNull(DaySections.minutesLeft(null, DayStatus.PENDING, today, at(6, 42)))
    }

    @Test
    fun nextDeadlineIsTheSoonestWindowEndStillAhead() {
        val gym = TimeWindow(18 * 60, 20 * 60)
        assertEquals(7 * 60, DaySections.nextDeadline(listOf(gym, wake), today, at(6, 42)))
        assertEquals(20 * 60, DaySections.nextDeadline(listOf(gym, wake), today, at(7, 6)))
        assertNull(DaySections.nextDeadline(listOf(gym, wake), today, at(20, 0)))
        assertNull(DaySections.nextDeadline(listOf(gym), today.minusDays(1), at(6, 42)))
    }
}
