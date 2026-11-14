package com.example.dailygoals.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.blocker.BlockScreens
import com.example.dailygoals.blocker.PendingKeys
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.BlockMessages
import com.example.dailygoals.engine.MessageCheck
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.components.InstalledApp
import com.example.dailygoals.ui.components.loadInstalledApps
import kotlinx.coroutines.launch

@Composable
internal fun PageHeader(title: Int, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButtonBox(AppIconKind.CLOSE, stringResource(R.string.form_close), onClose)
        Text(stringResource(title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun SafeAppsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val registry = remember { (context.applicationContext as SkritiApp).blocks }
    val changes by registry.changes.collectAsStateWithLifecycle()
    val safe = remember(changes) { registry.safeApps }
    val skriti = remember { context.applicationContext as SkritiApp }
    val scope = rememberCoroutineScope()
    val installed = remember { loadInstalledApps(context) }
    val rows = remember(installed, safe) {
        val known = installed.map { it.packageName }.toSet()
        (installed + (safe - known).map { InstalledApp(it, it) }).sortedWith(compareByDescending<InstalledApp> { it.packageName in safe }.thenBy { it.label.lowercase() })
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageHeader(R.string.safe_apps_title, onClose)
        Text(stringResource(R.string.safe_apps_help), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        PendingChangesList(Modifier.padding(bottom = 8.dp)) { it.settingKey?.startsWith(PendingKeys.SAFE_APP_PREFIX) == true }
        rows.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = app.packageName in safe, onCheckedChange = { on ->
                    scope.launch { skriti.coolingOff.setSafeApp(app.packageName, on) }
                })
            }
        }
    }
}

@Composable
fun BlockedAppsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val registry = remember { (context.applicationContext as SkritiApp).blocks }
    val changes by registry.changes.collectAsStateWithLifecycle()
    val pauseLeft = rememberPauseLeft(registry)
    val now = System.currentTimeMillis()
    val conflicts = remember(changes, pauseLeft) { registry.conflicts(now) }
    val pm = context.packageManager
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageHeader(R.string.blocked_apps_title, onClose)
        Text(stringResource(R.string.blocked_apps_help), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        if (pauseLeft > 0L) {
            Text(stringResource(R.string.blocked_apps_paused, formatLeft(pauseLeft)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        if (conflicts.isEmpty()) {
            Text(stringResource(R.string.blocked_apps_none), fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp))
        }
        conflicts.forEach { app ->
            val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString() }.getOrDefault(app.packageName)
            Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                app.blocks.forEach { block ->
                    val kind = stringResource(if (block.kind == BlockKind.SCHEDULED) R.string.blocked_apps_scheduled else R.string.blocked_apps_punishment)
                    val longest = if (block === app.longest && app.blocks.size > 1) " · ${stringResource(R.string.blocked_apps_longest)}" else ""
                    Text(
                        stringResource(R.string.blocked_apps_line, block.goalName, BlockScreens.untilText(block.endsAtMillis, now), kind) + longest,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        PendingChangesList()
    }
}

@Composable
fun BlockMessagesScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val registry = remember { (context.applicationContext as SkritiApp).blocks }
    var showAttempts by remember { mutableStateOf(registry.showAttempts) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        PageHeader(R.string.messages_title, onClose)
        Text(stringResource(R.string.messages_help), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        MessageEditor(R.string.messages_scheduled, registry.scheduledMessage, BlockKind.SCHEDULED) { registry.scheduledMessage = it }
        MessageEditor(R.string.messages_punishment, registry.punishmentMessage, BlockKind.PUNISHMENT) { registry.punishmentMessage = it }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.messages_attempts), fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = showAttempts, onCheckedChange = {
                showAttempts = it
                registry.showAttempts = it
            })
        }
    }
}

@Composable
private fun MessageEditor(title: Int, saved: String, kind: BlockKind, onSave: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(saved)) }
    val check = BlockMessages.check(field.text)
    val tooLong = check is MessageCheck.TooLong
    Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            (BlockMessages.check(it.text) as? MessageCheck.Ok)?.let { ok -> onSave(ok.text) }
        },
        isError = tooLong,
        supportingText = { Text("${field.text.trim().length}/${BlockMessages.MAX_LENGTH}") },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BlockMessages.PLACEHOLDERS.forEach { placeholder ->
            SettingsButton(placeholder) {
                val start = field.selection.start
                val end = field.selection.end
                val text = field.text.replaceRange(start, end, placeholder)
                field = TextFieldValue(text, TextRange(start + placeholder.length))
                (BlockMessages.check(text) as? MessageCheck.Ok)?.let { onSave(it.text) }
            }
        }
    }
    val preview = (check as? MessageCheck.Ok)?.text ?: BlockMessages.default(kind)
    Text(
        text = stringResource(R.string.messages_preview, BlockMessages.render(preview, BlockMessages.SAMPLE)),
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
    SettingsButton(stringResource(R.string.messages_reset), Modifier.padding(top = 8.dp)) {
        val default = BlockMessages.default(kind)
        field = TextFieldValue(default)
        onSave(default)
    }
}

enum class BlockingPage { BLOCKED_APPS, SAFE_APPS, MESSAGES, TEMP_ACCESS }
