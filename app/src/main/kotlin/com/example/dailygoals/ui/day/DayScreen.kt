package com.example.dailygoals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.DayItem
import com.example.dailygoals.engine.DayPlan
import com.example.dailygoals.engine.DaySection
import com.example.dailygoals.engine.minuteOfDay
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.DayDial
import com.example.dailygoals.ui.components.DialArc
import com.example.dailygoals.ui.components.DialTone
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.components.SectionLabel
import com.example.dailygoals.ui.formatMinute
import com.example.dailygoals.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DayScreen(viewModel: DayViewModel, onAddGoal: () -> Unit, modifier: Modifier = Modifier) {
    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return
    val plan = state.plan
    val isToday = plan.date == state.today
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf("") }
    val nowMillis = state.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dismissedIds = dismissed.split(',').mapNotNull(String::toLongOrNull).toSet()
    val missedToday = plan.items.filter {
        it.status == DayStatus.FAILED && it.failReason != FailReason.MANUAL && it.failReason != null && it.goal.id !in dismissedIds
    }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item(key = "header") {
            DayHeader(plan.date, state.today, onOpenCalendar = { calendarOpen = true }, onToday = { viewModel.select(state.today) })
            WeekStrip(state.week, state.weekSummaries, plan.date, state.today, viewModel::select)
        }
        if (isToday) {
            items(missedToday, key = { "banner-${it.goal.id}" }) { item ->
                FailureBanner(
                    item = item,
                    onDidIt = { viewModel.act(item.goal.id, plan.date, CheckAction.TOGGLE_DONE) },
                    onDismiss = { dismissed = "$dismissed,${item.goal.id}" },
                )
            }
        }
        if (plan.items.isEmpty()) {
            item(key = "empty") { EmptyDay(state.hasActiveGoals, onAddGoal) }
        } else {
            item(key = "dial") {
                DialBlock(plan, nowMinute = state.now.minuteOfDay.takeIf { isToday }, minimumWeightPercent = state.minimumWeightPercent)
            }
            plan.items.groupBy { it.section }.forEach { (section, sectionItems) ->
                item(key = section.name) { SectionHeader(section, plan.date, state.today) }
                items(sectionItems, key = { it.goal.id }) { item ->
                    val goalId = item.goal.id
                    GoalCard(
                        item = item,
                        editable = plan.editable,
                        stats = state.stats[goalId],
                        timerStart = state.timers[goalId],
                        nowMillis = nowMillis,
                        actions = CardActions(
                            onAction = { viewModel.act(goalId, plan.date, it) },
                            onLog = { viewModel.log(goalId, plan.date, it) },
                            onUndo = { viewModel.undoLog(goalId, plan.date) },
                            onTimerToggle = {
                                if (state.timers[goalId] == null) viewModel.startTimer(goalId) else viewModel.stopTimer(goalId)
                            },
                        ),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            item(key = "hint") {
                Text(
                    text = stringResource(if (plan.editable) R.string.day_hold_hint else R.string.day_view_only),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    if (calendarOpen) {
        MonthDialog(
            selected = plan.date,
            today = state.today,
            summariesFor = viewModel::monthSummaries,
            onSelect = {
                viewModel.select(it)
                calendarOpen = false
            },
            onDismiss = { calendarOpen = false },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, onOpenCalendar: () -> Unit, onToday: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        today.plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val pattern = if (date.year == today.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(date.format(DateTimeFormatter.ofPattern(pattern)), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        if (date != today) {
            Text(
                text = stringResource(R.string.day_back_to_today),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onToday).padding(12.dp),
            )
        }
        IconButtonBox(AppIconKind.CALENDAR, stringResource(R.string.day_open_calendar), onOpenCalendar)
    }
}

@Composable
private fun FailureBanner(item: DayItem, onDidIt: () -> Unit, onDismiss: () -> Unit) {
    val failed = LocalStatusColors.current.failed
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val window = item.goal.schedule.window
    Column(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(failed.copy(alpha = 0.14f).compositeOver(scheme.surface))
            .border(1.dp, failed, shape)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.banner_title, item.goal.name), color = failed, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        val body = if (item.failReason == FailReason.WINDOW_END && window != null) {
            stringResource(R.string.banner_window, formatMinute(window.endMinute))
        } else {
            stringResource(R.string.banner_day)
        }
        Text(body, fontSize = 13.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerButton(stringResource(R.string.banner_did_it), onDidIt)
            BannerButton(stringResource(R.string.banner_dismiss), onDismiss)
        }
    }
}

@Composable
private fun BannerButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DialBlock(plan: DayPlan, nowMinute: Int?, minimumWeightPercent: Int) {
    val scheme = MaterialTheme.colorScheme
    val summary = plan.summary
    val description = stringResource(R.string.day_dial_description, summary.kept, summary.total, summary.failed)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        DayDial(summary, plan.items.mapNotNull(::dialArc), nowMinute) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(summary.kept.toString(), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "/${summary.total}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = if (summary.failed > 0) {
                        stringResource(R.string.day_kept_and_missed, summary.failed)
                    } else {
                        stringResource(R.string.day_goals_kept)
                    },
                    fontSize = 12.5.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.day_percent, summary.percent(minimumWeightPercent)),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (nowMinute != null) {
            val deadline = plan.nextDeadlineMinute
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.day_now, formatMinute(nowMinute)), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                if (deadline != null) {
                    Text(
                        text = stringResource(R.string.day_next_deadline, formatMinute(deadline)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun dialArc(item: DayItem): DialArc? {
    val window = item.goal.schedule.window ?: return null
    val tone = when {
        item.status == DayStatus.FAILED -> DialTone.MISSED
        item.status == DayStatus.DONE || item.status == DayStatus.MINIMUM -> DialTone.KEPT
        item.section == DaySection.NOW || item.section == DaySection.OVERDUE -> DialTone.OPEN
        else -> DialTone.IDLE
    }
    return DialArc(window.startMinute, window.endMinute, tone)
}

@Composable
private fun SectionHeader(section: DaySection, date: LocalDate, today: LocalDate) {
    val status = LocalStatusColors.current
    val label = stringResource(
        when (section) {
            DaySection.MISSED -> R.string.section_missed
            DaySection.NOW -> R.string.section_now
            DaySection.OVERDUE -> if (date == today) R.string.section_window_passed else R.string.section_not_ticked
            DaySection.ANY_TIME -> R.string.section_any_time
            DaySection.LATER -> if (date == today) R.string.section_later else R.string.section_planned
            DaySection.KEPT -> R.string.section_kept
        },
    )
    val color = when (section) {
        DaySection.MISSED -> status.failed
        DaySection.NOW -> status.pending
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 2.dp)) {
        SectionLabel(label, color = color)
    }
}

@Composable
private fun EmptyDay(hasActiveGoals: Boolean, onAddGoal: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp),
    ) {
        if (hasActiveGoals) {
            Text(stringResource(R.string.day_nothing_due), color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.day_no_goals_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.day_no_goals_body), color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box { AccentButton(stringResource(R.string.day_add_first_goal), onAddGoal) }
        }
    }
}
