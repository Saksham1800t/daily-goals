package com.example.dailygoals

import android.app.Application
import com.example.dailygoals.data.AppDatabase
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.engine.Clock
import java.time.LocalDateTime

class SkritiApp : Application() {
    val clock: Clock = object : Clock {
        override fun now(): LocalDateTime = LocalDateTime.now()
    }

    val repository: GoalRepository by lazy {
        val database = AppDatabase.create(this)
        GoalRepository(database.goalDao(), database.checkInDao(), clock)
    }
}
