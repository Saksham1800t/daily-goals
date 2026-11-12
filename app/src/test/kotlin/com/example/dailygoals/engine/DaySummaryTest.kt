package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySummaryTest {
    @Test
    fun countsKeptAndFailedGoals() {
        val summary = DaySummary.of(
            listOf(DayStatus.DONE, DayStatus.DONE, DayStatus.FAILED, DayStatus.PENDING, DayStatus.PENDING),
        )
        assertEquals(DaySummary(total = 5, kept = 2, failed = 1), summary)
        assertEquals(0.4f, summary.keptFraction, 0.0001f)
        assertEquals(0.2f, summary.failedFraction, 0.0001f)
        assertFalse(summary.allKept)
    }

    @Test
    fun aDayWithNoGoalsHasZeroFractionsAndIsNotAllKept() {
        val summary = DaySummary.of(emptyList())
        assertEquals(DaySummary.EMPTY, summary)
        assertEquals(0f, summary.keptFraction, 0f)
        assertFalse(summary.allKept)
    }

    @Test
    fun minimumCountsAsKeptButIsWeightedInThePercentage() {
        val summary = DaySummary.of(listOf(DayStatus.DONE, DayStatus.MINIMUM, DayStatus.PENDING, DayStatus.FAILED))
        assertEquals(DaySummary(total = 4, kept = 2, failed = 1, minimum = 1), summary)
        assertEquals(40, summary.percent(60))
        assertEquals(50, summary.percent(100))
        assertEquals(25, summary.percent(0))
    }

    @Test
    fun percentIsZeroWithoutGoalsAndOneHundredWhenAllAreDone() {
        assertEquals(0, DaySummary.EMPTY.percent(60))
        assertEquals(100, DaySummary.of(listOf(DayStatus.DONE, DayStatus.DONE)).percent(60))
    }

    @Test
    fun allKeptWhenEveryGoalIsDone() {
        assertTrue(DaySummary.of(listOf(DayStatus.DONE, DayStatus.DONE)).allKept)
    }
}
