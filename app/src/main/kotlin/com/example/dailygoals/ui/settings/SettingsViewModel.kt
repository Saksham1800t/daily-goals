package com.example.dailygoals.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.data.Settings
import com.example.dailygoals.data.SettingsStore
import com.example.dailygoals.data.backup.Backup
import com.example.dailygoals.data.backup.BackupCodec
import com.example.dailygoals.data.backup.BackupException
import com.example.dailygoals.data.backup.BackupService
import com.example.dailygoals.data.backup.RestoreMode
import com.example.dailygoals.engine.Clock
import java.time.LocalDateTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val repository: GoalRepository,
    private val backup: BackupService,
    private val clock: Clock,
    val debuggable: Boolean,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settings

    fun now(): LocalDateTime = clock.now()

    fun setDayEnd(minute: Int) = settingsStore.update { it.copy(dayEndMinute = minute) }

    fun setMinimumWeight(percent: Int) = settingsStore.update { it.copy(minimumWeightPercent = percent) }

    /** Moves the app's clock; ignored in a release build. */
    fun shiftTime(minutes: Long) {
        if (!debuggable) return
        settingsStore.update { it.copy(timeShiftMinutes = it.timeShiftMinutes + minutes) }
        viewModelScope.launch { repository.evaluate() }
    }

    fun resetTime() {
        settingsStore.update { it.copy(timeShiftMinutes = 0) }
    }

    suspend fun exportJson(): String = backup.exportJson()

    suspend fun exportCsv(): String = backup.exportCsv()

    /** Returns the parsed backup, or the reason it cannot be restored. */
    fun parse(text: String): Result<Backup> = try {
        Result.success(BackupCodec.decode(text))
    } catch (error: BackupException) {
        Result.failure(error)
    }

    fun restore(parsed: Backup, mode: RestoreMode, onDone: () -> Unit) {
        viewModelScope.launch {
            backup.restore(parsed, mode)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                SettingsViewModel(app.settings, app.repository, app.backup, app.clock, app.isDebuggable)
            }
        }
    }
}
