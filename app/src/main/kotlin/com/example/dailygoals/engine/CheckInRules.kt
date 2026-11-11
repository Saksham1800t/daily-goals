package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import java.time.LocalDate

enum class CheckAction { TOGGLE_DONE, MARK_FAILED, CLEAR }

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
            CheckAction.MARK_FAILED -> DayStatus.FAILED
            CheckAction.CLEAR -> DayStatus.PENDING
        }
        if (next == current) return CheckResult.NoChange
        return CheckResult.Changed(next, if (next == DayStatus.FAILED) FailReason.MANUAL else null)
    }
}
