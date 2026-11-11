package com.example.dailygoals.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.dailygoals.R
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val MIN_RANGE_LENGTH = 3

fun formatMinute(minute: Int): String = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun DayOfWeek.narrowName(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())

@Composable
fun windowText(window: TimeWindow?): String = if (window == null) {
    stringResource(R.string.goal_all_day)
} else {
    stringResource(R.string.goal_window, formatMinute(window.startMinute), formatMinute(window.endMinute))
}

/** "Mon to Fri" for an unbroken run of three or more days, otherwise the days listed. */
@Composable
fun dayListText(days: Set<DayOfWeek>): String {
    val sorted = days.sorted()
    val isRun = sorted.size >= MIN_RANGE_LENGTH && sorted.last().value - sorted.first().value == sorted.size - 1
    return if (isRun) {
        stringResource(R.string.sentence_day_range, sorted.first().shortName(), sorted.last().shortName())
    } else {
        sorted.joinToString { it.shortName() }
    }
}

@Composable
fun daysPhrase(frequency: Frequency, days: Set<DayOfWeek>): String = when {
    frequency == Frequency.DAILY || days.size == DayOfWeek.entries.size -> stringResource(R.string.sentence_every_day)
    days.isEmpty() -> stringResource(R.string.sentence_no_days)
    else -> stringResource(R.string.sentence_on_days, dayListText(days))
}

@Composable
fun windowPhrase(window: TimeWindow?): String = if (window == null) {
    stringResource(R.string.sentence_all_day)
} else {
    stringResource(R.string.sentence_between, formatMinute(window.startMinute), formatMinute(window.endMinute))
}
