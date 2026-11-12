package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.formatMinute

private const val MAX_DIGITS = 6

@Composable
internal fun FailureRulesField(form: GoalFormState, viewModel: GoalFormViewModel) {
    Field(R.string.form_fail_rules) {
        if (form.hasWindow) {
            RuleRow(
                checked = form.failOnWindowEnd,
                title = stringResource(R.string.rule_window_end),
                subtitle = stringResource(R.string.rule_window_end_help, formatMinute(form.endMinute), form.graceMinutes),
                onChange = { checked -> viewModel.edit { it.copy(failOnWindowEnd = checked) } },
            )
        }
        RuleRow(
            checked = form.failOnDayEnd,
            title = stringResource(R.string.rule_day_end),
            subtitle = stringResource(R.string.rule_day_end_help),
            onChange = { checked -> viewModel.edit { it.copy(failOnDayEnd = checked) } },
        )
        RuleRow(
            checked = form.failOnManual,
            title = stringResource(R.string.rule_manual),
            subtitle = stringResource(R.string.rule_manual_help),
            onChange = { checked -> viewModel.edit { it.copy(failOnManual = checked) } },
        )
        if (form.hasWindow && form.failOnWindowEnd) {
            Text(
                text = stringResource(R.string.form_grace),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = GoalSchedule.GRACE_CHOICES.map { minutes -> minutes to stringResource(R.string.form_grace_minutes, minutes) },
                selected = form.graceMinutes,
                onSelect = { minutes -> viewModel.edit { it.copy(graceMinutes = minutes) } },
            )
            HelpText(R.string.form_grace_help)
        }
        ErrorText(form.errors, GoalError.WINDOW_RULE_NEEDS_WINDOW, R.string.error_rule_needs_window)
    }
}

@Composable
private fun RuleRow(checked: Boolean, title: String, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MeasurementField(form: GoalFormState, viewModel: GoalFormViewModel) {
    Field(R.string.form_measure) {
        SegmentedChoice(
            options = listOf(
                false to stringResource(R.string.measure_check),
                true to stringResource(R.string.measure_amount),
            ),
            selected = form.isAmount,
            onSelect = { amount -> viewModel.edit { it.copy(isAmount = amount) } },
        )
        if (form.isAmount) {
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
            OutlinedTextField(
                value = form.unit,
                onValueChange = { text -> viewModel.edit { it.copy(unit = text.take(MAX_UNIT)) } },
                label = { Text(stringResource(R.string.measure_unit)) },
                placeholder = { Text(stringResource(R.string.measure_unit_hint)) },
                singleLine = true,
                isError = GoalError.UNIT_BLANK in form.errors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ErrorText(form.errors, GoalError.UNIT_BLANK, R.string.error_unit_blank)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = form.targetText,
                    onValueChange = { text -> viewModel.edit { it.copy(targetText = text.filter(Char::isDigit).take(MAX_DIGITS)) } },
                    label = { Text(stringResource(R.string.measure_target)) },
                    singleLine = true,
                    keyboardOptions = numbers,
                    isError = GoalError.TARGET_INVALID in form.errors,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.minimumText,
                    onValueChange = { text -> viewModel.edit { it.copy(minimumText = text.filter(Char::isDigit).take(MAX_DIGITS)) } },
                    label = { Text(stringResource(R.string.measure_minimum)) },
                    singleLine = true,
                    keyboardOptions = numbers,
                    isError = GoalError.MINIMUM_INVALID in form.errors || GoalError.MINIMUM_ABOVE_TARGET in form.errors,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
            }
            ErrorText(form.errors, GoalError.TARGET_INVALID, R.string.error_target)
            ErrorText(form.errors, GoalError.MINIMUM_INVALID, R.string.error_minimum_invalid)
            ErrorText(form.errors, GoalError.MINIMUM_ABOVE_TARGET, R.string.error_minimum_above_target)
            HelpText(R.string.measure_amount_help)
        } else {
            OutlinedTextField(
                value = form.minimumLabel,
                onValueChange = { text -> viewModel.edit { it.copy(minimumLabel = text) } },
                label = { Text(stringResource(R.string.measure_minimum_label)) },
                placeholder = { Text(stringResource(R.string.measure_minimum_label_hint)) },
                singleLine = true,
                isError = GoalError.LABEL_TOO_LONG in form.errors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            ErrorText(form.errors, GoalError.LABEL_TOO_LONG, R.string.error_label_too_long)
            HelpText(R.string.measure_minimum_label_help)
        }
    }
}

private const val MAX_UNIT = 20
