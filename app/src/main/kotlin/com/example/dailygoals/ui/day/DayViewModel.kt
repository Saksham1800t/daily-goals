package com.example.dailygoals.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.engine.DayPlan
import com.example.dailygoals.engine.DayPlanner
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.engine.Weeks
import com.example.dailygoals.model.GoalState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayUiState(
    val now: LocalDateTime,
    val plan: DayPlan,
    val week: List<LocalDate>,
    val weekSummaries: Map<LocalDate, DaySummary>,
    val hasActiveGoals: Boolean,
) {
    val today: LocalDate get() = now.toLocalDate()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(private val repository: GoalRepository, private val clock: Clock) : ViewModel() {
    /** Null follows today, so the screen rolls over at midnight instead of staying on yesterday. */
    private val pickedDate = MutableStateFlow<LocalDate?>(null)

    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            val current = clock.now()
            emit(current.withSecond(0).withNano(0))
            delay((SECONDS_PER_MINUTE - current.second) * MILLIS_PER_SECOND)
        }
    }.distinctUntilChanged()

    private val selection: Flow<Pair<LocalDateTime, LocalDate>> =
        combine(now, pickedDate) { time, picked -> time to (picked ?: time.toLocalDate()) }

    val state: StateFlow<DayUiState?> = selection
        .flatMapLatest { (time, date) ->
            val week = Weeks.weekOf(date)
            combine(repository.goals, repository.checkIns(week.first(), week.last())) { goals, checkIns ->
                DayUiState(
                    now = time,
                    plan = DayPlanner.plan(goals, checkIns, date, time),
                    week = week,
                    weekSummaries = DayPlanner.summaries(goals, checkIns, week),
                    hasActiveGoals = goals.any { it.state == GoalState.ACTIVE },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun monthSummaries(month: YearMonth): Flow<Map<LocalDate, DaySummary>> {
        val days = List(month.lengthOfMonth()) { month.atDay(it + 1) }
        return combine(repository.goals, repository.checkIns(days.first(), days.last())) { goals, checkIns ->
            DayPlanner.summaries(goals, checkIns, days)
        }
    }

    fun select(date: LocalDate) {
        pickedDate.value = date.takeIf { it != clock.now().toLocalDate() }
    }

    fun act(goalId: Long, date: LocalDate, action: CheckAction) {
        viewModelScope.launch { repository.applyAction(goalId, date, action) }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val MILLIS_PER_SECOND = 1000L
        private const val STOP_TIMEOUT_MILLIS = 5000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                DayViewModel(app.repository, app.clock)
            }
        }
    }
}
