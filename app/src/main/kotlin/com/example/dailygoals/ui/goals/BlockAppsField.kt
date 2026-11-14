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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.BlockMessages
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.ui.components.loadInstalledApps

/** The apps a goal blocks, shared by scheduled blocking and punishment blocks. */
@Composable
internal fun BlockAppsField(form: GoalFormState, viewModel: GoalFormViewModel) {
    if (!form.canScheduleBlock && !form.punishmentBlocks) return
    var picking by remember { mutableStateOf(false) }
    Field(R.string.form_block_apps) {
        if (form.canScheduleBlock) {
            CheckRow(
                checked = form.scheduledBlock,
                title = R.string.scheduled_block_switch,
                help = if (form.hasWindow) R.string.scheduled_block_help_window else R.string.scheduled_block_help_all_day,
            ) { on -> viewModel.edit { it.copy(scheduledBlock = on) } }
            if (form.scheduledBlock) {
                CheckRow(form.autoKeep, R.string.scheduled_block_auto_keep, R.string.scheduled_block_auto_keep_help) { on ->
                    viewModel.edit { it.copy(autoKeep = on) }
                }
            }
        }
        if (!form.blocksAnything) return@Field
        TextButton(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (form.blockPackages.isEmpty()) {
                    stringResource(R.string.punish_choose_apps)
                } else {
                    pluralStringResource(R.plurals.punish_apps_chosen, form.blockPackages.size, form.blockPackages.size)
                },
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
        CheckRow(form.allowTempAccess, R.string.temp_goal_switch, R.string.temp_goal_switch_help) { on ->
            viewModel.edit { it.copy(allowTempAccess = on) }
        }
        if (form.attemptsToday > 0) {
            Text(
                pluralStringResource(R.plurals.attempts_today, form.attemptsToday, form.attemptsToday),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (picking) {
        AppPickerDialog(form.blockPackages, onDismiss = { picking = false }) { chosen ->
            viewModel.edit { it.copy(blockPackages = chosen) }
            picking = false
        }
    }
}

@Composable
internal fun CheckRow(checked: Boolean, title: Int, help: Int, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
