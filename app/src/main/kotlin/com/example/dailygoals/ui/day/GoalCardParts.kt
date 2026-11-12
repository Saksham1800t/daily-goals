package com.example.dailygoals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.AmountRules
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.ConsistencyStats
import com.example.dailygoals.engine.DayItem
import com.example.dailygoals.engine.DaySection
import com.example.dailygoals.engine.Level
import com.example.dailygoals.engine.QuickLog
import com.example.dailygoals.engine.TimerRules
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.ui.components.TickMark
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.windowText

private const val MAX_DIGITS = 6

@Composable
internal fun Notes(item: DayItem) {
    val scheme = MaterialTheme.colorScheme
    val failed = LocalStatusColors.current.failed
    item.quota?.let { quota ->
        val text = if (item.goal.schedule.frequency == Frequency.PER_WEEK) {
            stringResource(R.string.quota_week, quota.done, quota.required)
        } else {
            stringResource(R.string.quota_month, quota.done, quota.required)
        }
        Text(text, fontSize = 13.sp, color = scheme.onSurfaceVariant)
    }
    val late = item.lateMinutes
    if (late != null && late > 0 && (item.status == DayStatus.DONE || item.status == DayStatus.MINIMUM)) {
        Text(stringResource(R.string.goal_late, late), fontSize = 13.sp, color = scheme.onSurfaceVariant)
    }
    when (item.failReason.takeIf { item.status == DayStatus.FAILED }) {
        FailReason.WINDOW_END -> Text(stringResource(R.string.goal_missed_window), fontSize = 13.sp, color = failed)
        FailReason.DAY_END -> Text(stringResource(R.string.goal_missed_day), fontSize = 13.sp, color = failed)
        FailReason.MANUAL, null -> Unit
    }
}

@Composable
internal fun AmountSection(item: DayItem, editable: Boolean, timerStart: Long?, nowMillis: Long, actions: CardActions) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val measurement = item.goal.measurement
    val level = AmountRules.levelOf(measurement, item.amount)
    val fill = when (level) {
        Level.TARGET -> status.done
        Level.MINIMUM -> status.pending
        Level.NONE -> if (item.amount > 0) status.pending.copy(alpha = 0.5f) else scheme.surfaceVariant
    }
    val fraction = (item.amount.toFloat() / measurement.target).coerceIn(0f, 1f)
    val minimumMark = measurement.minimum.toFloat() / measurement.target
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text("${item.amount} / ${measurement.target} ${measurement.unit}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (level == Level.MINIMUM) {
            Text(
                text = stringResource(R.string.goal_minimum_reached),
                fontSize = 13.sp,
                color = status.pending,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    Box(Modifier.padding(top = 8.dp, end = 6.dp).fillMaxWidth().height(8.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(CircleShape).background(fill))
        if (measurement.hasMinimumStep) {
            Box(Modifier.fillMaxWidth(minimumMark.coerceIn(0f, 1f)).height(8.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.dp).height(14.dp).background(scheme.onSurfaceVariant))
            }
        }
    }
    if (!editable) return
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val chips = remember(measurement.target) { QuickLog.chips(measurement.target) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        chips.forEach { amount ->
            ActionChip(stringResource(R.string.goal_log_chip, amount), onClick = { actions.onLog(amount) })
        }
        ActionChip(stringResource(R.string.goal_log_custom), onClick = { customOpen = true })
        if (item.amount > 0) ActionChip(stringResource(R.string.goal_log_undo), onClick = actions.onUndo)
        if (measurement.isTimeBased) {
            val running = timerStart != null
            ActionChip(
                text = if (running) {
                    stringResource(R.string.goal_timer_stop, TimerRules.elapsedMinutes(timerStart ?: nowMillis, nowMillis))
                } else {
                    stringResource(R.string.goal_timer_start)
                },
                onClick = actions.onTimerToggle,
                highlighted = running,
            )
        }
    }
    if (customOpen) {
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text(stringResource(R.string.goal_log_custom_title, measurement.unit)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (text.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        customOpen = false
                        actions.onLog(text.toInt())
                        text = ""
                    },
                ) { Text(stringResource(R.string.form_ok)) }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(stringResource(R.string.form_cancel)) } },
        )
    }
}

@Composable
internal fun ActionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val shape = CircleShape
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) scheme.onPrimary else scheme.onSurface,
        modifier = modifier
            .clip(shape)
            .background(if (highlighted) scheme.primary else scheme.surfaceVariant)
            .border(1.dp, if (highlighted) Color.Transparent else scheme.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ConsistencyLine(stats: ConsistencyStats) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats.percent}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.consistency_summary, stats.met, stats.due),
                fontSize = 12.5.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
        Text(
            text = if (stats.streakBroken) {
                stringResource(R.string.consistency_broken, stats.bestStreak, stats.percent)
            } else {
                stringResource(R.string.consistency_streaks, stats.bestStreak, stats.currentStreak)
            },
            fontSize = 12.5.sp,
            color = scheme.onSurfaceVariant,
        )
        if (stats.minimumOnly > 0) {
            Text(stringResource(R.string.consistency_minimum_days, stats.minimumOnly), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
    }
}

