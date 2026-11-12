package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.isMet

/** [kept] counts DONE and MINIMUM; [minimum] is how many of those stopped at the minimum. */
data class DaySummary(val total: Int, val kept: Int, val failed: Int, val minimum: Int = 0) {
    val keptFraction: Float get() = if (total == 0) 0f else kept.toFloat() / total
    val failedFraction: Float get() = if (total == 0) 0f else failed.toFloat() / total
    val allKept: Boolean get() = total > 0 && kept == total

    /** DONE counts 100%, MINIMUM counts [minimumWeightPercent], everything else 0%. */
    fun percent(minimumWeightPercent: Int): Int {
        if (total == 0) return 0
        val full = kept - minimum
        return (full * 100 + minimum * minimumWeightPercent + total / 2) / total
    }

    companion object {
        const val DEFAULT_MINIMUM_WEIGHT_PERCENT = 60
        val EMPTY = DaySummary(0, 0, 0)

        fun of(statuses: Collection<DayStatus>): DaySummary = DaySummary(
            total = statuses.size,
            kept = statuses.count { it.isMet },
            failed = statuses.count { it == DayStatus.FAILED },
            minimum = statuses.count { it == DayStatus.MINIMUM },
        )
    }
}
