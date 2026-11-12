package com.example.dailygoals.engine

import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.QuotaFailureMode
import java.time.LocalDate
import java.time.LocalDateTime

enum class PeriodStatus { OPEN, MET, FAILED, SKIPPED }

data class PeriodOutcome(
    val period: Period,
    val originalRequired: Int,
    val adjustedRequired: Int,
    val eligibleDays: Int,
    val completed: Int,
    val status: PeriodStatus,
    val failedOn: LocalDate?,
)

/** Rules for "X times per week" and "X times per month" goals. */
object QuotaRules {
    fun required(schedule: GoalSchedule, period: Period): Int = schedule.count.coerceIn(1, period.length)

    /**
     * [metDates] are the dates inside the period with a DONE or MINIMUM result. [neutralDays] are
     * days the goal was not active (paused or rescued); they lower the requirement, never fail it.
     */
    fun evaluate(
        schedule: GoalSchedule,
        period: Period,
        metDates: Set<LocalDate>,
        neutralDays: Set<LocalDate>,
        now: LocalDateTime,
        dayEndMinute: Int,
    ): PeriodOutcome {
        val original = required(schedule, period)
        val eligible = period.days().filter { it >= schedule.startDate && it !in neutralDays }
        val adjusted = minOf(original, eligible.size)
        val met = metDates.filter { it in period }
        fun outcome(status: PeriodStatus, failedOn: LocalDate? = null) =
            PeriodOutcome(period, original, adjusted, eligible.size, met.size, status, failedOn)

        if (eligible.isEmpty()) return outcome(PeriodStatus.SKIPPED)
        if (met.size >= adjusted) return outcome(PeriodStatus.MET)

        val today = now.toLocalDate()
        val lastClosedDay = if (now.minuteOfDay >= dayEndMinute) today else today.minusDays(1)
        if (lastClosedDay >= period.end) return outcome(PeriodStatus.FAILED, failedOn = period.end)
        if (schedule.quotaFailureMode == QuotaFailureMode.END_OF_PERIOD) return outcome(PeriodStatus.OPEN)

        for (day in eligible) {
            if (day > lastClosedDay) break
            val needed = adjusted - met.count { it <= day }
            val daysLeft = eligible.count { it > day }
            if (needed > daysLeft) return outcome(PeriodStatus.FAILED, failedOn = day)
        }
        return outcome(PeriodStatus.OPEN)
    }
}
