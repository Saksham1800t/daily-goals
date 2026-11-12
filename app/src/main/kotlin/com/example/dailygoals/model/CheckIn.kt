package com.example.dailygoals.model

import java.time.LocalDate

enum class DayStatus { PENDING, MINIMUM, DONE, FAILED }

val DayStatus.isMet: Boolean get() = this == DayStatus.DONE || this == DayStatus.MINIMUM

enum class FailReason { MANUAL, WINDOW_END, DAY_END }

data class CheckIn(
    val goalId: Long,
    val date: LocalDate,
    val status: DayStatus,
    val failReason: FailReason? = null,
    val amount: Int = 0,
    val lateMinutes: Int? = null,
)
