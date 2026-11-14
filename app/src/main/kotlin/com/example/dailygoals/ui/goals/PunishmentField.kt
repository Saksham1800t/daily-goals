package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.Intensity
import com.example.dailygoals.engine.PunishmentRules
import com.example.dailygoals.ui.components.SegmentedChoice

@Composable
internal fun PunishmentField(form: GoalFormState, viewModel: GoalFormViewModel) {
    Field(R.string.form_punishment) {
        SegmentedChoice(
            options = listOf(
                Intensity.NONE to stringResource(R.string.intensity_none),
                Intensity.GENTLE to stringResource(R.string.intensity_gentle),
                Intensity.MODERATE to stringResource(R.string.intensity_moderate),
                Intensity.STRONG to stringResource(R.string.intensity_strong),
            ),
            selected = form.intensity,
            onSelect = { level -> viewModel.edit { it.copy(intensity = level) } },
        )
        HelpText(
            when (form.intensity) {
                Intensity.NONE -> R.string.intensity_none_help
                Intensity.GENTLE -> R.string.intensity_gentle_help
                Intensity.MODERATE -> R.string.intensity_moderate_help
                Intensity.STRONG -> R.string.intensity_strong_help
            },
        )
        if (!form.punishmentBlocks) return@Field
        Text(
            stringResource(R.string.punish_duration),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        SegmentedChoice(
            options = PunishmentRules.BLOCK_MINUTE_CHOICES.map { it to durationLabel(it) },
            selected = form.blockMinutes,
            onSelect = { minutes -> viewModel.edit { it.copy(blockMinutes = minutes) } },
        )
    }
}

@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes % 60 == 0) stringResource(R.string.punish_hours, minutes / 60) else stringResource(R.string.form_grace_minutes, minutes)
