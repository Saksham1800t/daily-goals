package com.example.dailygoals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.DayItem
import com.example.dailygoals.engine.DaySection
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.ui.components.TickMark
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.windowText

private const val MISSED_BORDER_ALPHA = 0.6f

@Composable
fun GoalCard(item: DayItem, editable: Boolean, onAction: (CheckAction) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val goal = item.goal
    val window = goal.schedule.window
    val isNow = item.section == DaySection.NOW
    val isFailed = item.status == DayStatus.FAILED
    val border = when {
        isFailed -> status.failed.copy(alpha = MISSED_BORDER_ALPHA)
        isNow -> status.pending
        else -> scheme.outline
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, border, shape)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = goal.name,
                    fontSize = if (isNow) 18.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isFailed) TextDecoration.LineThrough else null,
                    color = when (item.status) {
                        DayStatus.FAILED -> status.failed
                        DayStatus.DONE, DayStatus.MINIMUM -> scheme.onSurfaceVariant
                        DayStatus.PENDING -> scheme.onSurface
                    },
                )
                val time = windowText(window)
                Text(
                    text = if (goal.type == GoalType.AVOID) stringResource(R.string.goal_avoid_suffix, time) else time,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
            TickWithMenu(item, editable, onAction)
        }
        val minutesLeft = item.minutesLeft
        if (minutesLeft != null && window != null) {
            Countdown(minutesLeft, minutesLeft.toFloat() / (window.endMinute - window.startMinute))
        }
        if (editable && goal.type == GoalType.AVOID && item.status == DayStatus.PENDING) {
            Text(
                text = stringResource(R.string.goal_i_failed),
                color = status.failed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .border(1.dp, status.failed, CircleShape)
                    .clickable(role = Role.Button) { onAction(CheckAction.MARK_FAILED) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TickWithMenu(item: DayItem, editable: Boolean, onAction: (CheckAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(
        when (item.status) {
            DayStatus.PENDING -> R.string.goal_tick_pending
            DayStatus.DONE -> R.string.goal_tick_done
            DayStatus.MINIMUM -> R.string.goal_tick_minimum
            DayStatus.FAILED -> R.string.goal_tick_failed
        },
        item.goal.name,
    )
    Box {
        TickMark(
            status = item.status,
            enabled = editable,
            description = description,
            onClick = { onAction(CheckAction.TOGGLE_DONE) },
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (item.status != DayStatus.FAILED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.goal_mark_missed)) },
                    onClick = {
                        menuOpen = false
                        onAction(CheckAction.MARK_FAILED)
                    },
                )
            }
            if (item.status != DayStatus.PENDING) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.goal_clear_result)) },
                    onClick = {
                        menuOpen = false
                        onAction(CheckAction.CLEAR)
                    },
                )
            }
        }
    }
}

@Composable
private fun Countdown(minutesLeft: Int, fractionLeft: Float) {
    val scheme = MaterialTheme.colorScheme
    val pending = LocalStatusColors.current.pending
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text(minutesLeft.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = pending)
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.goal_minutes_left),
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Box(
        Modifier
            .padding(top = 6.dp, end = 6.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fractionLeft.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(pending),
        )
    }
}
