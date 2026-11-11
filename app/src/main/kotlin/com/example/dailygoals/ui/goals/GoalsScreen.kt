package com.example.dailygoals.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.DailyGoalsApp
import com.example.dailygoals.R
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.AppIcon
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.daysPhrase
import com.example.dailygoals.ui.windowText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GoalsViewModel(repository: GoalRepository) : ViewModel() {
    val goals: StateFlow<List<Goal>?> = repository.goals
        .map { goals -> goals.filter { it.state == GoalState.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L

        val Factory = viewModelFactory {
            initializer { GoalsViewModel((this[APPLICATION_KEY] as DailyGoalsApp).repository) }
        }
    }
}

@Composable
fun GoalsScreen(viewModel: GoalsViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, modifier: Modifier = Modifier) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.goals_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AccentButton(stringResource(R.string.goals_add), onAdd)
            }
        }
        val list = goals
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.goals_empty), color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 32.dp))
            }
        }
        items(list.orEmpty(), key = { it.id }) { goal ->
            GoalRow(goal, onClick = { onEdit(goal.id) })
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val days = daysPhrase(goal.schedule.frequency, goal.schedule.activeDays).replaceFirstChar(Char::uppercase)
    val time = windowText(goal.schedule.window)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(goal.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (goal.type == GoalType.AVOID) {
                    stringResource(R.string.goal_avoid_suffix, "$days · $time")
                } else {
                    "$days · $time"
                },
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        AppIcon(AppIconKind.CHEVRON_RIGHT, scheme.onSurfaceVariant)
    }
}
