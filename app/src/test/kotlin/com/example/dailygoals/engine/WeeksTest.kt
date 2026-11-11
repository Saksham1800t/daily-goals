package com.example.dailygoals.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeksTest {
    @Test
    fun weekStartsOnMondayAndContainsTheDate() {
        val week = Weeks.weekOf(LocalDate.of(2026, 11, 11))
        assertEquals(LocalDate.of(2026, 11, 9), week.first())
        assertEquals(LocalDate.of(2026, 11, 15), week.last())
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
    }

    @Test
    fun weekCanCrossAYearBoundary() {
        val week = Weeks.weekOf(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
    }

    @Test
    fun monthGridPadsToWholeWeeks() {
        val grid = Weeks.monthGrid(YearMonth.of(2026, 11))
        assertEquals(42, grid.size)
        assertNull(grid[5])
        assertEquals(LocalDate.of(2026, 11, 1), grid[6])
        assertEquals(LocalDate.of(2026, 11, 30), grid[35])
        assertNull(grid[36])
    }

    @Test
    fun leapFebruaryHasTwentyNineDays() {
        val grid = Weeks.monthGrid(YearMonth.of(2028, 2))
        assertEquals(29, grid.count { it != null })
        assertEquals(0, grid.size % Weeks.DAYS_PER_WEEK)
    }
}
