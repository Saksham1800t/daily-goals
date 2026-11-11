package com.example.dailygoals.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.model.GoalType
import com.example.dailygoals.ui.daysPhrase
import com.example.dailygoals.ui.windowPhrase

/** Reads the form back in plain words so the goal can be checked before saving. */
@Composable
fun GoalSentence(form: GoalFormState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val highlight = SpanStyle(
        color = scheme.primary,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
    )
    val opening = stringResource(if (form.type == GoalType.DO) R.string.sentence_do else R.string.sentence_avoid)
    val name = form.name.trim().ifEmpty { stringResource(R.string.sentence_name_placeholder) }
    val shownName = if (form.type == GoalType.DO) name.replaceFirstChar(Char::lowercase) else "“$name”"
    val days = daysPhrase(form.frequency, form.days)
    val window = windowPhrase(form.window)
    val sentence = buildAnnotatedString {
        append(opening)
        append(' ')
        withStyle(highlight) { append(shownName) }
        append(' ')
        withStyle(highlight) { append(days) }
        append(", ")
        withStyle(highlight) { append(window) }
        append('.')
    }
    val shape = RoundedCornerShape(22.dp)
    Text(
        text = sentence,
        fontSize = 19.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(16.dp),
    )
}
