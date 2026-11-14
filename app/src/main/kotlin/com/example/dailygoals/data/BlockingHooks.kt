package com.example.dailygoals.data

import com.example.dailygoals.model.Goal
import java.time.LocalDate

/** Where the repository reports failures, so the data layer never needs to know about blocking. */
interface PunishmentSink {
    /** A goal's day has just become FAILED. Returns true when the emergency pause skipped the punishment. */
    fun onFailed(goal: Goal, date: LocalDate, failedAtMillis: Long): Boolean

    /** A failed day was corrected, so whatever that failure caused ends now. */
    fun onCorrected(goalId: Long, date: LocalDate)
}
