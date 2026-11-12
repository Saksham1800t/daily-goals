package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Period(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun days(): List<LocalDate> = List(length) { start.plusDays(it.toLong()) }

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end
}

/** The week runs Monday to Sunday; the month is the calendar month. */
object Periods {
    fun of(frequency: Frequency, date: LocalDate): Period? = when (frequency) {
        Frequency.PER_WEEK -> Period(
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
        )
        Frequency.PER_MONTH -> YearMonth.from(date).let { Period(it.atDay(1), it.atEndOfMonth()) }
        else -> null
    }
}
