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
    fun allKeptWhenEveryGoalIsDone() {
        assertTrue(DaySummary.of(listOf(DayStatus.DONE, DayStatus.DONE)).allKept)
    }
}
