package com.example.dailygoals.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object Weeks {
    const val DAYS_PER_WEEK = 7

    fun weekOf(date: LocalDate): List<LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return List(DAYS_PER_WEEK) { monday.plusDays(it.toLong()) }
    }

    /** Month laid out in Monday-first rows; null marks a cell outside the month. */
    fun monthGrid(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List<LocalDate?>(leading) { null } + List(month.lengthOfMonth()) { month.atDay(it + 1) }
        val trailing = (DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
        return cells + List<LocalDate?>(trailing) { null }
    }
}
