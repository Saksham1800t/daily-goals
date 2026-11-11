package com.example.dailygoals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.engine.Weeks
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.DayRing
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.narrowName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

private const val FUTURE_ALPHA = 0.45f

@Composable
fun MonthDialog(
    selected: LocalDate,
    today: LocalDate,
    summariesFor: (YearMonth) -> Flow<Map<LocalDate, DaySummary>>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(monthText)
    val summaries by remember(month) { summariesFor(month) }.collectAsStateWithLifecycle(emptyMap())
    val title = remember(month) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = scheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButtonBox(AppIconKind.CHEVRON_LEFT, stringResource(R.string.calendar_previous_month), {
                        monthText = month.minusMonths(1).toString()
                    })
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButtonBox(AppIconKind.CHEVRON_RIGHT, stringResource(R.string.calendar_next_month), {
                        monthText = month.plusMonths(1).toString()
                    })
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            text = day.narrowName(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Weeks.monthGrid(month).chunked(Weeks.DAYS_PER_WEEK).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        row.forEach { date ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    DayRing(
                                        summary = summaries[date] ?: DaySummary.EMPTY,
                                        label = date.dayOfMonth.toString(),
                                        modifier = Modifier
                                            .alpha(if (date > today) FUTURE_ALPHA else 1f)
                                            .clip(CircleShape)
                                            .background(if (date == selected) scheme.surfaceVariant else Color.Transparent)
                                            .clickable(role = Role.Button) { onSelect(date) }
                                            .padding(3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onSelect(today) }) { Text(stringResource(R.string.day_today)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) }
                }
            }
        }
    }
}
