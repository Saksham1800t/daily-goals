package com.example.dailygoals.data

import com.example.dailygoals.engine.BlockLift
import com.example.dailygoals.model.Goal
import java.time.LocalDate

/** What the repository needs from the blocker, so the data layer never needs to know how blocking works. */
interface BlockingHooks {
    /** A goal's day has just become FAILED. Returns true when the emergency pause skipped the punishment. */
    fun onFailed(goal: Goal, date: LocalDate, failedAtMillis: Long): Boolean

    /** A failed day was corrected, so whatever that failure caused ends now. */
    fun onCorrected(goalId: Long, date: LocalDate)

    /** Recent times when blocks did not apply, in real epoch millis. */
    fun lifts(): List<BlockLift>

    /** A block was lifted during the goal's blocked time, so only the user knows whether the day was kept. */
    fun askWhetherUsed(goal: Goal, date: LocalDate)
}
