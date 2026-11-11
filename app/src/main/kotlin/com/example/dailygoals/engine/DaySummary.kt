package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus

data class DaySummary(val total: Int, val kept: Int, val failed: Int) {
    val keptFraction: Float get() = if (total == 0) 0f else kept.toFloat() / total
    val failedFraction: Float get() = if (total == 0) 0f else failed.toFloat() / total
    val allKept: Boolean get() = total > 0 && kept == total

    companion object {
        val EMPTY = DaySummary(0, 0, 0)

        fun of(statuses: Collection<DayStatus>): DaySummary = DaySummary(
            total = statuses.size,
            kept = statuses.count { it == DayStatus.DONE },
            failed = statuses.count { it == DayStatus.FAILED },
        )
    }
}
