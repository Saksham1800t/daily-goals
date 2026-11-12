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
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.model.TimeWindow
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
    val errors: Set<GoalError> = emptySet(),
    val original: Goal? = null,
) {
    val isEdit: Boolean get() = goalId != 0L
    val window: TimeWindow? get() = if (hasWindow) TimeWindow(startMinute, endMinute) else null

    fun schedule(startDate: LocalDate) = GoalSchedule(frequency, days, window, original?.schedule?.startDate ?: startDate)

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
        val schedule = form.schedule(clock.now().toLocalDate())
        val goal = form.original?.copy(name = form.name.trim(), type = form.type, schedule = schedule)
            ?: Goal(name = form.name.trim(), type = form.type, schedule = schedule)
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

    private fun validate(form: GoalFormState): Set<GoalError> =
        GoalValidator.validateName(form.name) + GoalValidator.validateSchedule(form.schedule(clock.now().toLocalDate()))

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                GoalFormViewModel(app.repository, app.clock)
            }
        }
    }
}
