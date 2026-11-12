package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.TimeWindow
import com.example.dailygoals.model.isMet
import java.time.LocalDate
import java.time.LocalDateTime

/** Declared in the order the sections appear on the day screen. */
enum class DaySection { MISSED, NOW, OVERDUE, ANY_TIME, LATER, KEPT }

object DaySections {
    fun sectionOf(window: TimeWindow?, status: DayStatus, date: LocalDate, now: LocalDateTime): DaySection {
        if (status == DayStatus.FAILED) return DaySection.MISSED
        if (status.isMet) return DaySection.KEPT
        val today = now.toLocalDate()
        if (date < today) return DaySection.OVERDUE
        if (date > today) return DaySection.LATER
        if (window == null) return DaySection.ANY_TIME
        val minute = now.minuteOfDay
        return when {
            minute < window.startMinute -> DaySection.LATER
            minute < window.endMinute -> DaySection.NOW
            else -> DaySection.OVERDUE
        }
    }

    fun minutesLeft(window: TimeWindow?, status: DayStatus, date: LocalDate, now: LocalDateTime): Int? {
        if (window == null || sectionOf(window, status, date, now) != DaySection.NOW) return null
        return window.endMinute - now.minuteOfDay
    }

    /** End minute of the soonest window that is still pending and has not closed, today only. */
    fun nextDeadline(pendingWindows: Collection<TimeWindow>, date: LocalDate, now: LocalDateTime): Int? {
        if (date != now.toLocalDate()) return null
        return pendingWindows.map { it.endMinute }.filter { it > now.minuteOfDay }.minOrNull()
    }
}
