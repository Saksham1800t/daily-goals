package com.example.dailygoals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.theme.accentBrush

private const val FULL_CIRCLE = 360f
private const val TOP_ANGLE = -90f

/** A small ring filled by the share of goals kept that day; the number turns red after a miss. */
@Composable
fun DayRing(summary: DaySummary, label: String, modifier: Modifier = Modifier, diameter: Dp = 34.dp) {
    val accent = accentBrush
    val track = MaterialTheme.colorScheme.outline
    val labelColor = if (summary.failed > 0) LocalStatusColors.current.failed else MaterialTheme.colorScheme.onSurface
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val width = 3.5.dp.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawCircle(track, radius = size.minDimension / 2 - inset, style = Stroke(width))
            if (summary.kept > 0) {
                drawArc(
                    brush = accent,
                    startAngle = TOP_ANGLE,
                    sweepAngle = FULL_CIRCLE * summary.keptFraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = labelColor)
    }
}
