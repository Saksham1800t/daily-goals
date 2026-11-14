package com.example.dailygoals

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.dailygoals.blocker.BlockRegistry
import com.example.dailygoals.blocker.PocStore
import com.example.dailygoals.blocker.ScheduledBlockSync
import com.example.dailygoals.blocker.BlockingRunner
import com.example.dailygoals.data.AppDatabase
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.data.SettingsStore
import com.example.dailygoals.data.backup.BackupService
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.notify.AlarmScheduler
import com.example.dailygoals.notify.FailureNotifier
import com.example.dailygoals.notify.NotificationChannels
import com.example.dailygoals.notify.PermissionChecker
import com.example.dailygoals.notify.WarningNotifier
import com.example.dailygoals.notify.WarningRunner
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class SkritiApp : Application() {
    /** Lives as long as the process; used for work that must finish after a screen or receiver is gone. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    val repository: GoalRepository by lazy { GoalRepository(database, settings, clock, BlockingRunner(this, blocks, FailureNotifier(this))) }

    val backup: BackupService by lazy { BackupService(database, settings) }

    val permissions: PermissionChecker by lazy { PermissionChecker(this) }

    val pocStore: PocStore by lazy { PocStore(this, blocks) }

    val blocks: BlockRegistry by lazy { BlockRegistry(this) }

    val warnings: WarningRunner by lazy {
        WarningRunner(repository, AlarmScheduler(this, permissions), permissions, WarningNotifier(this), ScheduledBlockSync(this, blocks))
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        scope.launch {
            combine(repository.changes, settings.settings) { _, _ -> }
                .debounce(CHANGE_DEBOUNCE_MILLIS)
                .collect { warnings.run() }
        }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MILLIS = 500L
    }
}
