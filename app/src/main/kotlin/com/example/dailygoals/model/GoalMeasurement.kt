package com.example.dailygoals.model

enum class MeasureType { CHECK, AMOUNT }

/**
 * A CHECK goal is yes or no and may name a smaller minimum version. An AMOUNT goal logs a number
 * against a [target] with a smaller [minimum] that still counts as a win.
 */
data class GoalMeasurement(
    val type: MeasureType = MeasureType.CHECK,
    val unit: String = "",
    val target: Int = 1,
    val minimum: Int = 1,
    val minimumLabel: String? = null,
) {
    val isAmount: Boolean get() = type == MeasureType.AMOUNT
    val hasMinimumStep: Boolean get() = if (isAmount) minimum < target else !minimumLabel.isNullOrBlank()
}
