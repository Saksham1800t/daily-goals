package com.example.dailygoals.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailygoals.R
import com.example.dailygoals.ui.components.AppIcon
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.day.DayScreen
import com.example.dailygoals.ui.day.DayViewModel
import com.example.dailygoals.ui.goals.GoalFormScreen
import com.example.dailygoals.ui.goals.GoalFormViewModel
import com.example.dailygoals.ui.goals.GoalsScreen
import com.example.dailygoals.ui.goals.GoalsViewModel
import com.example.dailygoals.ui.theme.accentBrush

private enum class Tab(val label: Int, val icon: AppIconKind) {
    TODAY(R.string.tab_today, AppIconKind.LOGO),
    GOALS(R.string.tab_goals, AppIconKind.LIST),
}

private const val NEW_GOAL_ID = 0L

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var formGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val formViewModel: GoalFormViewModel = viewModel(factory = GoalFormViewModel.Factory)
    val openForm = { goalId: Long ->
        formViewModel.open(goalId)
        formGoalId = goalId
    }
    LaunchedEffect(formGoalId) { formGoalId?.let(formViewModel::ensureOpen) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (formGoalId != null) {
            BackHandler { formGoalId = null }
            GoalFormScreen(formViewModel, onClose = { formGoalId = null }, modifier = Modifier.weight(1f))
        } else {
            BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.TODAY -> DayScreen(
                        viewModel = viewModel(factory = DayViewModel.Factory),
                        onAddGoal = { openForm(NEW_GOAL_ID) },
                    )
                    Tab.GOALS -> GoalsScreen(
                        viewModel = viewModel(factory = GoalsViewModel.Factory),
                        onAdd = { openForm(NEW_GOAL_ID) },
                        onEdit = openForm,
                    )
                }
            }
            BottomBar(tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentBrush
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(vertical = 8.dp),
    ) {
        Tab.entries.forEach { tab ->
            val chosen = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onSelect(tab) }),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 56.dp, height = 30.dp)
                        .clip(CircleShape)
                        .background(if (chosen) accent else SolidColor(scheme.surface)),
                ) {
                    AppIcon(tab.icon, if (chosen) scheme.onPrimary else scheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(tab.label),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (chosen) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
