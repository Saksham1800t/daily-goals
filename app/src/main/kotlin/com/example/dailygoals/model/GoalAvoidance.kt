package com.example.dailygoals.model

/** The apps a goal blocks, for a punishment now and for scheduled blocking later. */
data class GoalAvoidance(val temptingPackages: Set<String> = emptySet())
