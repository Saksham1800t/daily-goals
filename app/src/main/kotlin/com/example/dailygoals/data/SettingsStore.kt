package com.example.dailygoals.data

import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val dayEndMinute: Int = DEFAULT_DAY_END_MINUTE,
    val minimumWeightPercent: Int = DEFAULT_MINIMUM_WEIGHT_PERCENT,
    val timeShiftMinutes: Long = 0,
) {
    companion object {
        const val DEFAULT_DAY_END_MINUTE = 23 * 60 + 59
        const val DEFAULT_MINIMUM_WEIGHT_PERCENT = 60
    }
}

/** What the last evaluation run knew, so closed dates stay closed and zone jumps are noticed. */
data class EvalState(val finalisedThrough: LocalDate?, val lastToday: LocalDate?, val zoneId: String?, val neutralDates: Set<LocalDate>)

/** Small settings and bookkeeping values kept in the framework preferences, so no library is needed. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = mutableSettings.asStateFlow()

    private val mutableTimers = MutableStateFlow(readTimers())
    val timers: StateFlow<Map<Long, Long>> = mutableTimers.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(mutableSettings.value)
        prefs.edit()
            .putInt(KEY_DAY_END, next.dayEndMinute)
            .putInt(KEY_MINIMUM_WEIGHT, next.minimumWeightPercent)
            .putLong(KEY_TIME_SHIFT, next.timeShiftMinutes)
            .apply()
        mutableSettings.value = next
    }

    fun evalState(): EvalState = EvalState(
        finalisedThrough = prefs.getString(KEY_FINALISED, null)?.let(LocalDate::parse),
        lastToday = prefs.getString(KEY_LAST_TODAY, null)?.let(LocalDate::parse),
        zoneId = prefs.getString(KEY_ZONE, null),
        neutralDates = prefs.getString(KEY_NEUTRAL, "").orEmpty().split(',').filter { it.isNotBlank() }.map(LocalDate::parse).toSet(),
    )

    fun saveEvalState(state: EvalState) {
        prefs.edit()
            .putString(KEY_FINALISED, state.finalisedThrough?.toString())
            .putString(KEY_LAST_TODAY, state.lastToday?.toString())
            .putString(KEY_ZONE, state.zoneId)
            .putString(KEY_NEUTRAL, state.neutralDates.sorted().joinToString(","))
            .apply()
    }

    fun startTimer(goalId: Long, startMillis: Long) = writeTimers(mutableTimers.value + (goalId to startMillis))

    fun stopTimer(goalId: Long): Long? {
        val start = mutableTimers.value[goalId] ?: return null
        writeTimers(mutableTimers.value - goalId)
        return start
    }

    private fun writeTimers(timers: Map<Long, Long>) {
        prefs.edit().putString(KEY_TIMERS, timers.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        mutableTimers.value = timers
    }

    private fun readTimers(): Map<Long, Long> = prefs.getString(KEY_TIMERS, "").orEmpty().split(',').filter { it.contains(':') }
        .associate { entry -> entry.substringBefore(':').toLong() to entry.substringAfter(':').toLong() }

    private fun read() = Settings(
        dayEndMinute = prefs.getInt(KEY_DAY_END, Settings.DEFAULT_DAY_END_MINUTE),
        minimumWeightPercent = prefs.getInt(KEY_MINIMUM_WEIGHT, Settings.DEFAULT_MINIMUM_WEIGHT_PERCENT),
        timeShiftMinutes = prefs.getLong(KEY_TIME_SHIFT, 0),
    )

    private companion object {
        const val KEY_DAY_END = "day_end_minute"
        const val KEY_MINIMUM_WEIGHT = "minimum_weight_percent"
        const val KEY_TIME_SHIFT = "time_shift_minutes"
        const val KEY_FINALISED = "finalised_through"
        const val KEY_LAST_TODAY = "last_evaluated_today"
        const val KEY_ZONE = "last_zone_id"
        const val KEY_NEUTRAL = "neutral_dates"
        const val KEY_TIMERS = "timers"
    }
}
