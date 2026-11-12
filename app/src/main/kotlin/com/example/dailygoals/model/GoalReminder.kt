package com.example.dailygoals.model

enum class WarningMode { NONE, ONCE, REPEATING }

/** How a goal nags: once, or every [intervalMinutes] until it is ticked, fails, or the day ends. */
data class GoalReminder(
    val mode: WarningMode = WarningMode.NONE,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    /** The check time for a goal with no window. */
    val allDayMinute: Int = DEFAULT_ALL_DAY_MINUTE,
    val strongAlert: Boolean = false,
) {
    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 10
        const val DEFAULT_ALL_DAY_MINUTE = 20 * 60
        val INTERVAL_CHOICES = listOf(5, 10, 15, 30)
    }
}
