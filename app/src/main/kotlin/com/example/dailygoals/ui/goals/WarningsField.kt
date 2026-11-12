package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.model.GoalReminder
import com.example.dailygoals.model.WarningMode
import com.example.dailygoals.ui.components.SegmentedChoice

@Composable
internal fun WarningsField(form: GoalFormState, viewModel: GoalFormViewModel) {
    Field(R.string.form_warnings) {
        SegmentedChoice(
            options = listOf(
                WarningMode.NONE to stringResource(R.string.warnings_none),
                WarningMode.ONCE to stringResource(R.string.warnings_once),
                WarningMode.REPEATING to stringResource(R.string.warnings_repeating),
            ),
            selected = form.warningMode,
            onSelect = { mode -> viewModel.edit { it.copy(warningMode = mode) } },
        )
        if (form.warningMode == WarningMode.NONE) {
            HelpText(R.string.warnings_none_help)
            return@Field
        }
        if (form.warningMode == WarningMode.REPEATING) {
            Text(
                text = stringResource(R.string.warnings_interval),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = GoalReminder.INTERVAL_CHOICES.map { minutes -> minutes to stringResource(R.string.form_grace_minutes, minutes) },
                selected = form.intervalMinutes,
                onSelect = { minutes -> viewModel.edit { it.copy(intervalMinutes = minutes) } },
            )
        }
        if (form.hasWindow) {
            HelpText(R.string.warnings_from_window)
        } else {
            Row(Modifier.padding(top = 10.dp)) {
                TimeBox(R.string.warnings_check_time, R.string.warnings_check_time, form.allDayMinute, Modifier.weight(1f)) { minute ->
                    viewModel.edit { it.copy(allDayMinute = minute) }
                }
            }
            HelpText(R.string.warnings_from_check_time)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .toggleable(value = form.strongAlert, role = Role.Checkbox, onValueChange = { on -> viewModel.edit { it.copy(strongAlert = on) } }),
        ) {
            Checkbox(checked = form.strongAlert, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(stringResource(R.string.warnings_strong), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.warnings_strong_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
