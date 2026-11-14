package com.example.dailygoals.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.CoolingOff
import com.example.dailygoals.data.backup.Backup
import com.example.dailygoals.data.backup.RestoreMode
import com.example.dailygoals.ui.components.SectionLabel
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.goals.TimeBox
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WEIGHT_CHOICES = listOf(40, 50, 60, 70, 80)
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPermissions: () -> Unit,
    onOpenBlockTest: () -> Unit,
    onOpenBlockingPage: (BlockingPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Backup?>(null) }
    val stamp = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    suspend fun write(uri: android.net.Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    val jsonSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            write(uri, viewModel.exportJson())
            message = context.getString(R.string.settings_export_done)
        }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            write(uri, viewModel.exportCsv())
            message = context.getString(R.string.settings_export_done)
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }.orEmpty() }
            viewModel.parse(text).fold(
                onSuccess = { pending = it },
                onFailure = { message = it.message },
            )
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        Group(R.string.settings_warnings) {
            Text(stringResource(R.string.settings_warnings_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsButton(stringResource(R.string.settings_warnings_open), Modifier.padding(top = 10.dp), onOpenPermissions)
        }

        Group(R.string.settings_blocking) {
            EmergencyPauseCard((context.applicationContext as SkritiApp).blocks)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                SettingsButton(stringResource(R.string.blocked_apps_title)) { onOpenBlockingPage(BlockingPage.BLOCKED_APPS) }
                SettingsButton(stringResource(R.string.safe_apps_title)) { onOpenBlockingPage(BlockingPage.SAFE_APPS) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SettingsButton(stringResource(R.string.messages_title)) { onOpenBlockingPage(BlockingPage.MESSAGES) }
                SettingsButton(stringResource(R.string.temp_settings_title)) { onOpenBlockingPage(BlockingPage.TEMP_ACCESS) }
            }
        }

        Group(R.string.settings_day) {
            Text(stringResource(R.string.settings_day_end), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_day_end_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeBox(R.string.settings_day_end_label, R.string.settings_day_end, settings.dayEndMinute, Modifier.padding(top = 8.dp)) {
                viewModel.setDayEnd(it)
            }
        }

        Group(R.string.settings_scoring) {
            Text(stringResource(R.string.settings_weight), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_weight_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedChoice(
                options = WEIGHT_CHOICES.map { it to "$it%" },
                selected = settings.minimumWeightPercent,
                onSelect = viewModel::setMinimumWeight,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Group(R.string.settings_backup) {
            Text(stringResource(R.string.settings_backup_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                SettingsButton(stringResource(R.string.settings_export_json)) { jsonSaver.launch("skriti-backup-$stamp.json") }
                SettingsButton(stringResource(R.string.settings_export_csv)) { csvSaver.launch("skriti-checkins-$stamp.csv") }
            }
            SettingsButton(stringResource(R.string.settings_restore), Modifier.padding(top = 8.dp)) {
                opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            message?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        }

        if (viewModel.debuggable) {
            Group(R.string.settings_block_test) {
                Text(stringResource(R.string.settings_block_test_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsButton(stringResource(R.string.settings_block_test_open), Modifier.padding(top = 10.dp), onOpenBlockTest)
            }

            Group(R.string.settings_time_travel) {
                val shifted = viewModel.now().format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
                Text(stringResource(R.string.settings_time_travel_now, shifted), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_time_travel_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    SettingsButton("-1 d") { viewModel.shiftTime(-MINUTES_PER_DAY) }
                    SettingsButton("-1 h") { viewModel.shiftTime(-MINUTES_PER_HOUR) }
                    SettingsButton("+1 h") { viewModel.shiftTime(MINUTES_PER_HOUR) }
                    SettingsButton("+1 d") { viewModel.shiftTime(MINUTES_PER_DAY) }
                }
                SettingsButton(stringResource(R.string.settings_time_travel_reset), Modifier.padding(top = 8.dp)) { viewModel.resetTime() }
            }
        }

        Text(
            text = stringResource(R.string.settings_reinstall),
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_counts, backup.goals.size, backup.checkIns.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    viewModel.restore(backup, RestoreMode.MERGE) { message = context.getString(R.string.settings_restore_done) }
                }) { Text(stringResource(R.string.settings_restore_merge)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pending = null
                        val blocks = (context.applicationContext as SkritiApp).blocks.blocks
                        if (CoolingOff.restoreAllowed(replace = true, blocks = blocks, nowMillis = System.currentTimeMillis())) {
                            viewModel.restore(backup, RestoreMode.REPLACE) { message = context.getString(R.string.settings_restore_done) }
                        } else {
                            message = context.getString(R.string.settings_restore_refused)
                        }
                    }) { Text(stringResource(R.string.settings_restore_replace)) }
                    TextButton(onClick = { pending = null }) { Text(stringResource(R.string.form_cancel)) }
                }
            },
        )
    }
}

@Composable
internal fun Group(title: Int, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.padding(top = 18.dp)) {
        SectionLabel(stringResource(title), Modifier.padding(bottom = 8.dp, start = 2.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surface)
                .border(1.dp, scheme.outline, shape)
                .padding(16.dp),
        ) { content() }
    }
}

@Composable
internal fun SettingsButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
