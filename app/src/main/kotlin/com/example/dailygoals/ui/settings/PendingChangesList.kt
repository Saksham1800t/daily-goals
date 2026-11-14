package com.example.dailygoals.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.example.dailygoals.blocker.BlockScreens
import com.example.dailygoals.blocker.PendingKeys
import com.example.dailygoals.data.PendingChangeEntity
import kotlinx.coroutines.launch

/** Held changes, each with the time it applies and a way to cancel it. Shows nothing when there are none. */
@Composable
fun PendingChangesList(modifier: Modifier = Modifier, include: (PendingChangeEntity) -> Boolean = { true }) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as SkritiApp }
    val all by app.repository.pendingChanges.collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by app.repository.goals.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val shown = all.filter(include)
    if (shown.isEmpty()) return
    Column(modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.pending_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.pending_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        shown.forEach { change ->
            val key = change.settingKey
            val what = when {
                change.goalId != null -> {
                    val name = goals.firstOrNull { it.id == change.goalId }?.name.orEmpty()
                    if (app.repository.isArchive(change)) stringResource(R.string.pending_goal_delete, name) else stringResource(R.string.pending_goal_edit, name)
                }
                key == PendingKeys.TEMP_ACCESS -> stringResource(R.string.pending_temp_settings)
                key != null && key.startsWith(PendingKeys.SAFE_APP_PREFIX) -> stringResource(R.string.pending_safe_app, appLabel(context, change.newValue))
                else -> key.orEmpty()
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(what, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.pending_applies_at, BlockScreens.untilText(change.appliesAt, System.currentTimeMillis())),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { scope.launch { app.repository.cancelPending(change.id) } }) { Text(stringResource(R.string.pending_cancel)) }
            }
        }
    }
}

private fun appLabel(context: android.content.Context, packageName: String): String {
    val pm = context.packageManager
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
}
