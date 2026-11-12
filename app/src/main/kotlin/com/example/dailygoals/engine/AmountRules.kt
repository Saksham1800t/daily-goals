package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.GoalMeasurement

enum class Level { NONE, MINIMUM, TARGET }

data class Resolved(val status: DayStatus, val failReason: FailReason?)

object AmountRules {
    const val MAX_AMOUNT = 100_000

    fun levelOf(measurement: GoalMeasurement, amount: Int): Level = when {
        amount >= measurement.target -> Level.TARGET
        amount >= measurement.minimum -> Level.MINIMUM
        else -> Level.NONE
    }

    fun add(current: Int, delta: Int): Int = (current.toLong() + delta).coerceIn(0L, MAX_AMOUNT.toLong()).toInt()

    /**
     * The status a day should have for a logged [amount]. Reaching the minimum or target keeps the
     * goal, and dropping back below the minimum returns it to PENDING, or to FAILED when a failure
     * rule has already fired. A goal the user marked as missed by hand stays missed until corrected.
     * Below-minimum amounts count as failure, but the amount stays visible.
     */
    fun resolve(
        measurement: GoalMeasurement,
        amount: Int,
        previous: DayStatus,
        previousReason: FailReason?,
        ruleFired: FailReason?,
    ): Resolved = when (levelOf(measurement, amount)) {
        Level.TARGET -> Resolved(DayStatus.DONE, null)
        Level.MINIMUM -> Resolved(DayStatus.MINIMUM, null)
        Level.NONE -> when {
            previous == DayStatus.FAILED && previousReason == FailReason.MANUAL -> Resolved(DayStatus.FAILED, FailReason.MANUAL)
            ruleFired != null -> Resolved(DayStatus.FAILED, ruleFired)
            else -> Resolved(DayStatus.PENDING, null)
        }
    }
}

object QuickLog {
    /** Three quick amounts derived from the target, for example +10, +30 and +60 for a target of 120. */
    fun chips(target: Int): List<Int> {
        val raw = listOf(target / 12, target / 4, target / 2).map { nice(it.coerceAtLeast(1)) }.distinct()
        return raw.ifEmpty { listOf(1) }
    }

    private fun nice(value: Int): Int = when {
        value < 10 -> value
        value < 50 -> (value + 2) / 5 * 5
        else -> (value + 5) / 10 * 10
    }
}

object TimerRules {
    private const val MILLIS_PER_MINUTE = 60_000L

    /** Whole minutes since the timer started; a clock that moved backwards never gives a negative time. */
    fun elapsedMinutes(startMillis: Long, nowMillis: Long): Int =
        ((nowMillis - startMillis) / MILLIS_PER_MINUTE).coerceIn(0L, AmountRules.MAX_AMOUNT.toLong()).toInt()
}
