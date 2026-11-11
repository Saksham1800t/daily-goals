package com.example.dailygoals.model

enum class GoalType { DO, AVOID }

enum class GoalState { ACTIVE, ARCHIVED }

data class Goal(
    val id: Long = 0,
    val name: String,
    val type: GoalType = GoalType.DO,
    val state: GoalState = GoalState.ACTIVE,
    val sortOrder: Int = 0,
    val schedule: GoalSchedule,
)
