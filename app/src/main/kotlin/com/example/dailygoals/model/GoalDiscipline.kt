package com.example.dailygoals.model

import com.example.dailygoals.engine.Intensity
import com.example.dailygoals.engine.PunishmentRules

/** The three rules that can fail a goal, and what a failure then costs. */
data class GoalDiscipline(
    val failOnWindowEnd: Boolean = false,
    val failOnDayEnd: Boolean = false,
    val failOnManual: Boolean = false,
    val intensity: Intensity = Intensity.NONE,
    val blockMinutes: Int = PunishmentRules.DEFAULT_BLOCK_MINUTES,
    /** A message for this goal's blocks; null uses the defaults from Settings. */
    val blockMessage: String? = null,
    /** For an avoid goal: block its apps during the window (or all day) on due days, before any failure. */
    val scheduledBlockEnabled: Boolean = false,
    val autoKeepAtWindowEnd: Boolean = true,
    val allowTempAccess: Boolean = true,
)
