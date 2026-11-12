package com.example.dailygoals.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.engine.GoalValidator
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.GoalReminder
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.MeasureType
import com.example.dailygoals.model.TimeWindow
import com.example.dailygoals.model.WarningMode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalFormState(
    val goalId: Long = 0,
    val name: String = "",
    val type: GoalType = GoalType.DO,
    val frequency: Frequency = Frequency.DAILY,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val hasWindow: Boolean = false,
    val startMinute: Int = DEFAULT_START_MINUTE,
    val endMinute: Int = DEFAULT_END_MINUTE,
    val graceMinutes: Int = GoalSchedule.DEFAULT_GRACE_MINUTES,
    val failOnWindowEnd: Boolean = true,
    val failOnDayEnd: Boolean = false,
    val failOnManual: Boolean = false,
    val isAmount: Boolean = false,
    val unit: String = "",
    val targetText: String = "",
    val minimumText: String = "",
    val minimumLabel: String = "",
    val warningMode: WarningMode = WarningMode.REPEATING,
    val intervalMinutes: Int = GoalReminder.DEFAULT_INTERVAL_MINUTES,
    val allDayMinute: Int = GoalReminder.DEFAULT_ALL_DAY_MINUTE,
    val strongAlert: Boolean = false,
    val errors: Set<GoalError> = emptySet(),
    val original: Goal? = null,
) {
    val isEdit: Boolean get() = goalId != 0L
    val window: TimeWindow? get() = if (hasWindow) TimeWindow(startMinute, endMinute) else null

    /** A rule that needs a window is switched off while the goal has none. */
    val discipline: GoalDiscipline
        get() = GoalDiscipline(failOnWindowEnd && hasWindow, failOnDayEnd, failOnManual)

    val reminder: GoalReminder
        get() = GoalReminder(warningMode, intervalMinutes, allDayMinute, strongAlert && warningMode != WarningMode.NONE)

    val measurement: GoalMeasurement
        get() = if (isAmount) {
            GoalMeasurement(MeasureType.AMOUNT, unit.trim(), targetText.toIntOrNull() ?: 0, minimumText.toIntOrNull() ?: 0)
        } else {
            GoalMeasurement(MeasureType.CHECK, minimumLabel = minimumLabel.trim().ifEmpty { null })
        }

    fun schedule(startDate: LocalDate): GoalSchedule {
        val kept = original?.schedule
        return (kept ?: GoalSchedule(startDate = startDate)).copy(
            frequency = frequency,
            activeDays = days,
            window = window,
            startDate = kept?.startDate ?: startDate,
            graceMinutes = graceMinutes,
        )
    }

    private companion object {
        const val DEFAULT_START_MINUTE = 6 * 60
        const val DEFAULT_END_MINUTE = 7 * 60
    }
}

class GoalFormViewModel(private val repository: GoalRepository, private val clock: Clock) : ViewModel() {
    private val mutableState = MutableStateFlow(GoalFormState())
    val state: StateFlow<GoalFormState> = mutableState.asStateFlow()

    fun open(goalId: Long) {
        mutableState.value = GoalFormState(goalId = goalId)
        if (goalId == 0L) return
        viewModelScope.launch {
            val goal = repository.goal(goalId) ?: return@launch
            val window = goal.schedule.window
            mutableState.update { current ->
                if (current.goalId != goalId) return@update current
                current.copy(
                    name = goal.name,
                    type = goal.type,
                    frequency = goal.schedule.frequency,
                    days = goal.schedule.activeDays,
                    hasWindow = window != null,
                    startMinute = window?.startMinute ?: current.startMinute,
                    endMinute = window?.endMinute ?: current.endMinute,
                    graceMinutes = goal.schedule.graceMinutes,
                    failOnWindowEnd = goal.discipline.failOnWindowEnd,
                    failOnDayEnd = goal.discipline.failOnDayEnd,
                    failOnManual = goal.discipline.failOnManual,
                    isAmount = goal.measurement.isAmount,
                    unit = goal.measurement.unit,
                    targetText = if (goal.measurement.isAmount) goal.measurement.target.toString() else "",
                    minimumText = if (goal.measurement.isAmount) goal.measurement.minimum.toString() else "",
                    minimumLabel = goal.measurement.minimumLabel.orEmpty(),
                    warningMode = goal.reminder.mode,
                    intervalMinutes = goal.reminder.intervalMinutes,
                    allDayMinute = goal.reminder.allDayMinute,
                    strongAlert = goal.reminder.strongAlert,
                    original = goal,
                )
            }
        }
    }

    /** Reloads after the process was restarted while the form was showing. */
    fun ensureOpen(goalId: Long) {
        if (mutableState.value.goalId != goalId) open(goalId)
    }

    fun edit(change: (GoalFormState) -> GoalFormState) {
        mutableState.update { current ->
            val changed = change(current)
            if (current.errors.isEmpty()) changed else changed.copy(errors = validate(changed))
        }
    }

    /** A new avoid goal starts with the rules that suit it; the user can still change them. */
    fun chooseType(type: GoalType) = edit {
        if (it.isEdit) {
            it.copy(type = type)
        } else {
            it.copy(type = type, failOnDayEnd = type == GoalType.AVOID, failOnManual = type == GoalType.AVOID)
        }
    }

    private var saving = false

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val form = mutableState.value
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(errors = errors) }
            return
        }
        saving = true
        val goal = (form.original ?: Goal(name = "", schedule = form.schedule(clock.now().toLocalDate()))).copy(
            name = form.name.trim(),
            type = form.type,
            schedule = form.schedule(clock.now().toLocalDate()),
            discipline = form.discipline,
            measurement = form.measurement,
            reminder = form.reminder,
        )
        viewModelScope.launch {
            repository.save(goal)
            saving = false
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val goalId = mutableState.value.goalId
        viewModelScope.launch {
            repository.archive(goalId)
            onDeleted()
        }
    }

    private fun validate(form: GoalFormState): Set<GoalError> {
        val schedule = form.schedule(clock.now().toLocalDate())
        return GoalValidator.validateName(form.name) +
            GoalValidator.validateSchedule(schedule) +
            GoalValidator.validateDiscipline(form.discipline, schedule) +
            GoalValidator.validateMeasurement(form.measurement)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                GoalFormViewModel(app.repository, app.clock)
            }
        }
    }
}
