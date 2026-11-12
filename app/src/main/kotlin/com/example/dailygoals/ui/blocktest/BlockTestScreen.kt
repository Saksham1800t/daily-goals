package com.example.dailygoals.ui.blocktest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.blocker.BlockMethod
import com.example.dailygoals.blocker.BlockerService
import com.example.dailygoals.engine.ForegroundRules
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.components.SectionLabel
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val SUGGESTIONS = listOf("com.android.chrome", "com.google.android.youtube", "com.instagram.android")
private val DURATIONS = listOf<Int?>(15, 60, 480, null)
private const val REFRESH_MILLIS = 1000L

private fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val component = ComponentName(context, BlockerService::class.java)
    return enabled.split(':').any { it == component.flattenToString() || it == component.flattenToShortString() }
}

/** The hidden test screen of Phase 2A: guided setup, one test app, the block method and a live log. */
@Composable
fun BlockTestScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as SkritiApp).pocStore }
    val clipboard = LocalClipboardManager.current
    var tick by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(store.target) }
    var method by remember { mutableStateOf(store.method) }
    var minutes by remember { mutableStateOf<Int?>(15) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(REFRESH_MILLIS)
        }
    }
    val refresh = tick
    val enabledInSettings = remember(refresh) { isServiceEnabled(context) }
    val running = remember(refresh) { BlockerService.instance != null }
    val session = remember(refresh) { store.session()?.takeIf { ForegroundRules.isActive(it, System.currentTimeMillis()) } }
    val log = remember(refresh) { store.readLog() }
    val colors = LocalStatusColors.current

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox(AppIconKind.CLOSE, stringResource(R.string.form_close), onClose)
            Text(
                text = stringResource(R.string.poc_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Text(
            text = stringResource(R.string.poc_intro),
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )

        Section(R.string.poc_step_restricted) {
            Text(stringResource(R.string.poc_restricted_help), fontSize = 13.5.sp)
            PillButton(stringResource(R.string.poc_open_app_info), Modifier.padding(top = 10.dp)) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }

        Section(R.string.poc_step_service) {
            Text(stringResource(R.string.poc_service_help), fontSize = 13.5.sp)
            StatusLine(R.string.poc_status_enabled, enabledInSettings, colors.done, colors.pending)
            StatusLine(R.string.poc_status_running, running, colors.done, colors.pending)
            PillButton(stringResource(R.string.poc_open_accessibility), Modifier.padding(top = 10.dp)) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Text(
                text = stringResource(R.string.poc_privacy_note),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Section(R.string.poc_step_test) {
            OutlinedTextField(
                value = target,
                onValueChange = {
                    target = it
                    store.target = it
                },
                label = { Text(stringResource(R.string.poc_target)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                SUGGESTIONS.forEach { name ->
                    PillButton(name.substringAfterLast('.')) {
                        target = name
                        store.target = name
                    }
                }
            }
            Text(stringResource(R.string.poc_method), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            BlockMethod.entries.forEach { option ->
                MethodRow(option, selected = option == method) {
                    method = option
                    store.method = option
                }
            }
            Text(stringResource(R.string.poc_duration), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            SegmentedChoice(
                options = DURATIONS.map { it to durationLabel(it) },
                selected = minutes,
                onSelect = { minutes = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                AccentButton(stringResource(R.string.poc_start), onClick = { store.start(minutes) })
                PillButton(stringResource(R.string.poc_stop), Modifier.padding(top = 2.dp)) { store.stop() }
            }
            Text(
                text = session?.let { active ->
                    val until = active.untilMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
                    stringResource(R.string.poc_session_on, active.targetPackage, until ?: stringResource(R.string.poc_until_off))
                } ?: stringResource(R.string.poc_session_off),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (session != null) colors.pending else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Section(R.string.poc_step_log) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.poc_copy_log)) { clipboard.setText(AnnotatedString(store.readLog(Int.MAX_VALUE).joinToString("\n"))) }
                PillButton(stringResource(R.string.poc_clear_log)) { store.clearLog() }
            }
            Text(
                text = if (log.isEmpty()) stringResource(R.string.poc_log_empty) else log.reversed().joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun durationLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.poc_duration_off)
    60 -> stringResource(R.string.poc_duration_hour)
    480 -> stringResource(R.string.poc_duration_hours, 8)
    else -> stringResource(R.string.poc_duration_minutes, minutes)
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
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
private fun StatusLine(label: Int, on: Boolean, onColor: androidx.compose.ui.graphics.Color, offColor: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(top = 6.dp)) {
        Text(stringResource(label), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (on) R.string.permission_on else R.string.permission_off),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (on) onColor else offColor,
        )
    }
}

@Composable
private fun MethodRow(method: BlockMethod, selected: Boolean, onClick: () -> Unit) {
    val (title, help) = when (method) {
        BlockMethod.ACTIVITY -> R.string.poc_method_activity to R.string.poc_method_activity_help
        BlockMethod.OVERLAY -> R.string.poc_method_overlay to R.string.poc_method_overlay_help
        BlockMethod.HOME -> R.string.poc_method_home to R.string.poc_method_home_help
        BlockMethod.OVERLAY_THEN_HOME -> R.string.poc_method_overlay_home to R.string.poc_method_overlay_home_help
        BlockMethod.ACTIVITY_THEN_HOME -> R.string.poc_method_activity_home to R.string.poc_method_activity_home_help
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(top = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(stringResource(title), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
