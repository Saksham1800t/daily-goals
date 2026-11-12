package com.example.dailygoals

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.dailygoals.data.AppDatabase
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.data.SettingsStore
import com.example.dailygoals.data.backup.BackupService
import com.example.dailygoals.engine.Clock
import java.time.LocalDateTime

class SkritiApp : Application() {
    val isDebuggable: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    val settings: SettingsStore by lazy { SettingsStore(getSharedPreferences("settings", MODE_PRIVATE)) }

    /** The debug time shift only applies to a debuggable build, so a release build always shows real time. */
    val clock: Clock = object : Clock {
        override fun now(): LocalDateTime {
            val shift = if (isDebuggable) settings.settings.value.timeShiftMinutes else 0L
            return LocalDateTime.now().plusMinutes(shift)
        }
    }

    private val database: AppDatabase by lazy { AppDatabase.create(this) }

    val repository: GoalRepository by lazy { GoalRepository(database, settings, clock) }

    val backup: BackupService by lazy { BackupService(database, settings) }
}
