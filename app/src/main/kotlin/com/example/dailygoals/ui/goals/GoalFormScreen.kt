package com.example.dailygoals.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.components.SectionLabel
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.formatMinute
import com.example.dailygoals.ui.narrowName
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.theme.accentBrush
import java.time.DayOfWeek

private const val LAST_PICKABLE_HOUR = 23

@Composable
fun GoalFormScreen(viewModel: GoalFormViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox(AppIconKind.CLOSE, stringResource(R.string.form_close), onClose)
            Text(
                text = stringResource(if (form.isEdit) R.string.form_edit_goal else R.string.form_new_goal),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            AccentButton(stringResource(R.string.form_save), onClick = { viewModel.save(onClose) })
        }
        Spacer(Modifier.height(16.dp))
        GoalSentence(form)

        Field(R.string.form_name) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { text -> viewModel.edit { it.copy(name = text) } },
                placeholder = { Text(stringResource(R.string.form_name_hint)) },
                singleLine = true,
                isError = GoalError.NAME_BLANK in form.errors || GoalError.NAME_TOO_LONG in form.errors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(form.errors, GoalError.NAME_BLANK, R.string.error_name_blank)
            ErrorText(form.errors, GoalError.NAME_TOO_LONG, R.string.error_name_too_long)
        }

        Field(R.string.form_type) {
            SegmentedChoice(
                options = listOf(
                    GoalType.DO to stringResource(R.string.form_type_do),
                    GoalType.AVOID to stringResource(R.string.form_type_avoid),
                ),
                selected = form.type,
                onSelect = viewModel::chooseType,
            )
            HelpText(if (form.type == GoalType.DO) R.string.form_type_do_help else R.string.form_type_avoid_help)
        }

        Field(R.string.form_frequency) {
            SegmentedChoice(
                options = listOf(
                    Frequency.DAILY to stringResource(R.string.form_every_day),
                    Frequency.WEEKDAYS to stringResource(R.string.form_specific_days),
                ),
                selected = form.frequency,
                onSelect = { frequency -> viewModel.edit { it.copy(frequency = frequency) } },
            )
            if (form.frequency == Frequency.WEEKDAYS) {
                DayPicker(form.days) { day ->
                    viewModel.edit { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
                }
                ErrorText(form.errors, GoalError.NO_DAYS, R.string.error_no_days)
            }
        }

        Field(R.string.form_window) {
            SegmentedChoice(
                options = listOf(
                    false to stringResource(R.string.form_all_day),
                    true to stringResource(R.string.form_set_times),
                ),
                selected = form.hasWindow,
                onSelect = { hasWindow -> viewModel.edit { it.copy(hasWindow = hasWindow) } },
            )
            if (form.hasWindow) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TimeBox(R.string.form_starts, R.string.form_pick_start, form.startMinute, Modifier.weight(1f)) { minute ->
                        viewModel.edit { it.copy(startMinute = minute) }
                    }
                    TimeBox(R.string.form_ends, R.string.form_pick_end, form.endMinute, Modifier.weight(1f)) { minute ->
                        viewModel.edit { it.copy(endMinute = minute) }
                    }
                }
                ErrorText(form.errors, GoalError.WINDOW_END_NOT_AFTER_START, R.string.error_window_order)
            }
        }

        FailureRulesField(form, viewModel)
        MeasurementField(form, viewModel)
        WarningsField(form, viewModel)
        PunishmentField(form, viewModel)

        if (form.isEdit) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.form_delete), color = LocalStatusColors.current.failed)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.form_delete_title)) },
            text = { Text(stringResource(R.string.form_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) { Text(stringResource(R.string.form_delete_confirm), color = LocalStatusColors.current.failed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.form_cancel)) }
            },
        )
    }
}

@Composable
internal fun Field(label: Int, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionLabel(stringResource(label), Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
internal fun HelpText(text: Int) {
    Text(
        text = stringResource(text),
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun ErrorText(errors: Set<GoalError>, error: GoalError, text: Int) {
    if (error !in errors) return
    Text(
        text = stringResource(text),
        fontSize = 12.5.sp,
        color = LocalStatusColors.current.failed,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun DayPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentBrush
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        DayOfWeek.entries.forEach { day ->
            val chosen = day in selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (chosen) accent else SolidColor(scheme.surface))
                    .border(1.dp, if (chosen) scheme.primary else scheme.outline, CircleShape)
                    .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onToggle(day) }),
            ) {
                Text(
                    text = day.narrowName(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (chosen) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeBox(label: Int, dialogTitle: Int, minute: Int, modifier: Modifier, onPicked: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(stringResource(label), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
        Text(formatMinute(minute), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
    if (open) {
        val picker = rememberTimePickerState(
            initialHour = (minute / 60).coerceAtMost(LAST_PICKABLE_HOUR),
            initialMinute = minute % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(dialogTitle)) },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onPicked(picker.hour * 60 + picker.minute)
                }) { Text(stringResource(R.string.form_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.form_cancel)) }
            },
        )
    }
}
