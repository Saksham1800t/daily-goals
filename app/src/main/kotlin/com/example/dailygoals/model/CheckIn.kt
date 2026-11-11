package com.example.dailygoals.model

import java.time.LocalDate

enum class DayStatus { PENDING, DONE, FAILED }

enum class FailReason { MANUAL }

data class CheckIn(
    val goalId: Long,
    val date: LocalDate,
    val status: DayStatus,
    val failReason: FailReason? = null,
)
