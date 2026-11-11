package com.example.dailygoals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.engine.Weeks
import com.example.dailygoals.ui.components.DayRing
import com.example.dailygoals.ui.narrowName
import java.time.LocalDate

private const val FUTURE_ALPHA = 0.45f

/** Seven day rings for the week of [selected]; a sideways swipe moves one week. */
@Composable
fun WeekStrip(
    week: List<LocalDate>,
    summaries: Map<LocalDate, DaySummary>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { _, amount -> dragged += amount },
                    onDragEnd = {
                        val weeks = if (dragged > threshold) -1L else if (dragged < -threshold) 1L else 0L
                        if (weeks != 0L) select(current.plusDays(weeks * Weeks.DAYS_PER_WEEK))
                    },
                )
            },
    ) {
        week.forEach { date ->
            val isSelected = date == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (date > today) FUTURE_ALPHA else 1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) scheme.surfaceVariant else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(date) })
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = date.dayOfWeek.narrowName(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                DayRing(summaries[date] ?: DaySummary.EMPTY, date.dayOfMonth.toString())
            }
        }
    }
}
