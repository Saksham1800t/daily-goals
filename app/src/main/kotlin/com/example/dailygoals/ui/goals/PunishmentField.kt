package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.BlockMessages
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.engine.Intensity
import com.example.dailygoals.engine.PunishmentRules
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.components.loadInstalledApps

@Composable
internal fun PunishmentField(form: GoalFormState, viewModel: GoalFormViewModel) {
    var picking by remember { mutableStateOf(false) }
    Field(R.string.form_punishment) {
        SegmentedChoice(
            options = listOf(
                Intensity.NONE to stringResource(R.string.intensity_none),
                Intensity.GENTLE to stringResource(R.string.intensity_gentle),
                Intensity.MODERATE to stringResource(R.string.intensity_moderate),
                Intensity.STRONG to stringResource(R.string.intensity_strong),
            ),
            selected = form.intensity,
            onSelect = { level -> viewModel.edit { it.copy(intensity = level) } },
        )
        HelpText(
            when (form.intensity) {
                Intensity.NONE -> R.string.intensity_none_help
                Intensity.GENTLE -> R.string.intensity_gentle_help
                Intensity.MODERATE -> R.string.intensity_moderate_help
                Intensity.STRONG -> R.string.intensity_strong_help
            },
        )
        if (!PunishmentRules.blocksApps(form.intensity)) return@Field
        Text(stringResource(R.string.punish_duration), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        SegmentedChoice(
            options = PunishmentRules.BLOCK_MINUTE_CHOICES.map { it to durationLabel(it) },
            selected = form.blockMinutes,
            onSelect = { minutes -> viewModel.edit { it.copy(blockMinutes = minutes) } },
        )
        TextButton(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (form.blockPackages.isEmpty()) stringResource(R.string.punish_choose_apps) else stringResource(R.string.punish_apps_chosen, form.blockPackages.size),
            )
        }
        OutlinedTextField(
            value = form.blockMessage,
            onValueChange = { text -> viewModel.edit { it.copy(blockMessage = text) } },
            label = { Text(stringResource(R.string.punish_message)) },
            isError = GoalError.BLOCK_MESSAGE_TOO_LONG in form.errors,
            supportingText = { Text("${form.blockMessage.trim().length}/${BlockMessages.MAX_LENGTH}") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.blockMessage.isNotBlank()) {
            Text(
                stringResource(R.string.messages_preview, BlockMessages.render(form.blockMessage.trim(), BlockMessages.SAMPLE)),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        ErrorText(form.errors, GoalError.BLOCK_MESSAGE_TOO_LONG, R.string.error_block_message_long)
    }
    if (picking) {
        AppPickerDialog(form.blockPackages, onDismiss = { picking = false }) { chosen ->
            viewModel.edit { it.copy(blockPackages = chosen) }
            picking = false
        }
    }
}

@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes % 60 == 0) stringResource(R.string.punish_hours, minutes / 60) else stringResource(R.string.form_grace_minutes, minutes)

/** Safe apps are listed but cannot be picked, since they are never blocked. */
@Composable
private fun AppPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val registry = remember { (context.applicationContext as SkritiApp).blocks }
    val safe = remember { registry.safeApps }
    val apps = remember { loadInstalledApps(context) }
    var chosen by remember { mutableStateOf(initial - safe) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.punish_choose_apps)) },
        text = {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    val isSafe = app.packageName in safe
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = app.packageName in chosen,
                                enabled = !isSafe,
                                role = Role.Checkbox,
                                onValueChange = { on -> chosen = if (on) chosen + app.packageName else chosen - app.packageName },
                            ),
                    ) {
                        Checkbox(checked = app.packageName in chosen, onCheckedChange = null, enabled = !isSafe, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (isSafe) Text(stringResource(R.string.punish_app_safe), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text(stringResource(R.string.form_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}
