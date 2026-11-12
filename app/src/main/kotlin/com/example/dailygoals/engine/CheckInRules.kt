package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.TimeWindow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class CheckAction { TOGGLE_DONE, MARK_MINIMUM, MARK_FAILED, CLEAR }

sealed interface CheckResult {
    data class Changed(val status: DayStatus, val failReason: FailReason?) : CheckResult
    data object NoChange : CheckResult
    data object FutureDate : CheckResult
}

object CheckInRules {
    fun canEdit(date: LocalDate, today: LocalDate): Boolean = date <= today

    fun apply(current: DayStatus, action: CheckAction, date: LocalDate, today: LocalDate): CheckResult {
        if (!canEdit(date, today)) return CheckResult.FutureDate
        val next = when (action) {
            CheckAction.TOGGLE_DONE -> if (current == DayStatus.DONE) DayStatus.PENDING else DayStatus.DONE
            CheckAction.MARK_MINIMUM -> if (current == DayStatus.MINIMUM) DayStatus.PENDING else DayStatus.MINIMUM
            CheckAction.MARK_FAILED -> DayStatus.FAILED
            CheckAction.CLEAR -> DayStatus.PENDING
        }
        if (next == current) return CheckResult.NoChange
        return CheckResult.Changed(next, if (next == DayStatus.FAILED) FailReason.MANUAL else null)
    }

    /** Whole minutes after the window ended, for a tick made today after the end; null otherwise. */
    fun lateMinutes(window: TimeWindow?, date: LocalDate, now: LocalDateTime, zone: ZoneId): Int? {
        if (window == null || date != now.toLocalDate()) return null
        val end = LocalTimes.resolve(date, window.endMinute, zone).toInstant()
        val at = now.atZone(zone).toInstant()
        if (!at.isAfter(end)) return null
        return Duration.between(end, at).toMinutes().toInt()
    }
}
