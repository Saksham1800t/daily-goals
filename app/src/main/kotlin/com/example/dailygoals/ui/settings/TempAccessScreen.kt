package com.example.dailygoals.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.blocker.PendingKeys
import com.example.dailygoals.engine.TempAccessSettings
import kotlinx.coroutines.launch

@Composable
fun TempAccessScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as SkritiApp }
    val changes by app.blocks.changes.collectAsStateWithLifecycle()
    val settings = remember(changes) { app.blocks.tempSettings }
    val scope = rememberCoroutineScope()
    val save: (TempAccessSettings) -> Unit = { next -> scope.launch { app.coolingOff.setTempSettings(next) } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageHeader(R.string.temp_settings_title, onClose)
        Text(
            stringResource(R.string.temp_settings_help),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.temp_settings_on), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = settings.enabled, onCheckedChange = { save(settings.copy(enabled = it)) })
        }
        Stepper(R.string.temp_settings_uses, settings.maxUsesPerDay.toString(), TempAccessSettings.USES_RANGE, settings.maxUsesPerDay, 1) {
            save(settings.copy(maxUsesPerDay = it))
        }
        Stepper(
            R.string.temp_settings_minutes,
            stringResource(R.string.form_grace_minutes, settings.maxMinutes),
            TempAccessSettings.MINUTES_RANGE,
            settings.maxMinutes,
            1,
        ) { save(settings.copy(maxMinutes = it)) }
        Stepper(
            R.string.temp_settings_wait,
            stringResource(R.string.temp_settings_seconds, settings.waitSeconds),
            TempAccessSettings.WAIT_RANGE,
            settings.waitSeconds,
            WAIT_STEP,
        ) { save(settings.copy(waitSeconds = it)) }
        PendingChangesList { it.settingKey == PendingKeys.TEMP_ACCESS }
    }
}

@Composable
private fun Stepper(label: Int, shown: String, range: IntRange, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−", fontSize = 20.sp) }
        Text(shown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+", fontSize = 20.sp) }
    }
}

private const val WAIT_STEP = 15
