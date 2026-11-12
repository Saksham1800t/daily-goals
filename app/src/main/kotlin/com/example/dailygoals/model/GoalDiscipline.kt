package com.example.dailygoals.model

/** The three rules that can fail a goal; any combination may be on. */
data class GoalDiscipline(
    val failOnWindowEnd: Boolean = false,
    val failOnDayEnd: Boolean = false,
    val failOnManual: Boolean = false,
)
