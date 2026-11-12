package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.MonthlyNth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyTest {
    private val start = LocalDate.of(2026, 11, 1)

    @Test
    fun everyThreeDaysIsDueOnTheGridFromTheStartDate() {
        val schedule = GoalSchedule(frequency = Frequency.EVERY_N_DAYS, intervalDays = 3, startDate = start)
        val due = (0..11).map { start.plusDays(it.toLong()) }.filter { DueDates.isDue(schedule, it) }
        assertEquals(listOf(1, 4, 7, 10), due.map { it.dayOfMonth })
        assertFalse(DueDates.isDue(schedule, start.minusDays(3)))
    }

    @Test
    fun intervalGridContinuesAcrossAMonthAndYearEnd() {
        val schedule = GoalSchedule(frequency = Frequency.EVERY_N_DAYS, intervalDays = 7, startDate = LocalDate.of(2026, 12, 28))
        assertTrue(DueDates.isDue(schedule, LocalDate.of(2027, 1, 4)))
        assertFalse(DueDates.isDue(schedule, LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun firstSundayOfEveryMonthIsDueOnlyOnThatDate() {
        val schedule = GoalSchedule(
            frequency = Frequency.MONTHLY_PATTERN,
            monthlyNth = MonthlyNth.FIRST,
            monthlyWeekday = DayOfWeek.SUNDAY,
            startDate = start,
        )
        assertTrue(DueDates.isDue(schedule, LocalDate.of(2026, 11, 1)))
        assertFalse(DueDates.isDue(schedule, LocalDate.of(2026, 11, 8)))
        assertTrue(DueDates.isDue(schedule, LocalDate.of(2026, 12, 6)))
        assertFalse(DueDates.isDue(schedule, LocalDate.of(2026, 12, 13)))
    }

    @Test
    fun lastAndFourthWeekdayHandleShortAndLongMonths() {
        assertEquals(LocalDate.of(2026, 11, 27), DueDates.nthWeekday(YearMonth.of(2026, 11), MonthlyNth.LAST, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 11, 10), DueDates.nthWeekday(YearMonth.of(2026, 11), MonthlyNth.SECOND, DayOfWeek.TUESDAY))
        assertEquals(LocalDate.of(2028, 2, 29), DueDates.nthWeekday(YearMonth.of(2028, 2), MonthlyNth.LAST, DayOfWeek.TUESDAY))
        assertEquals(LocalDate.of(2026, 2, 26), DueDates.nthWeekday(YearMonth.of(2026, 2), MonthlyNth.FOURTH, DayOfWeek.THURSDAY))
    }

    @Test
    fun quotaGoalsAreVisibleOnEveryDayFromTheirStart() {
        val weekly = GoalSchedule(frequency = Frequency.PER_WEEK, count = 4, startDate = start)
        assertTrue(DueDates.isDue(weekly, LocalDate.of(2026, 11, 7)))
        assertFalse(DueDates.isDue(weekly, LocalDate.of(2026, 10, 31)))
        assertTrue(weekly.isQuota)
    }

    @Test
    fun weekRunsMondayToSundayAndMonthIsTheCalendarMonth() {
        val week = Periods.of(Frequency.PER_WEEK, LocalDate.of(2026, 11, 11))!!
        assertEquals(LocalDate.of(2026, 11, 9), week.start)
        assertEquals(LocalDate.of(2026, 11, 15), week.end)
        assertEquals(7, week.length)
        val february = Periods.of(Frequency.PER_MONTH, LocalDate.of(2028, 2, 10))!!
        assertEquals(29, february.length)
        assertEquals(LocalDate.of(2028, 2, 29), february.end)
        assertEquals(null, Periods.of(Frequency.DAILY, start))
    }
}
