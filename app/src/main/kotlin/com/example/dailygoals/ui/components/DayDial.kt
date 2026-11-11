package com.example.dailygoals.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.ui.theme.LocalStatusColors
import com.example.dailygoals.ui.theme.accentBrush
import kotlin.math.cos
import kotlin.math.sin

enum class DialTone { IDLE, OPEN, KEPT, MISSED }

data class DialArc(val startMinute: Int, val endMinute: Int, val tone: DialTone)

private const val MINUTES_PER_DAY = 1440f
private const val FULL_CIRCLE = 360f
private const val TOP_ANGLE = -90f
private val HOUR_LABELS = listOf(0, 6, 12, 18)

private fun angleOf(minute: Int): Float = minute / MINUTES_PER_DAY * FULL_CIRCLE + TOP_ANGLE

private fun DrawScope.ringArc(radius: Float, start: Float, sweep: Float, width: Float, cap: StrokeCap, color: Color) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width, cap = cap),
    )
}

/**
 * The day as a 24-hour dial: goal windows are arcs on the outer ring, the dot is the time now,
 * and the inner ring closes as goals are kept.
 */
@Composable
fun DayDial(
    summary: DaySummary,
    arcs: List<DialArc>,
    nowMinute: Int?,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val accent = accentBrush
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
    val kept by animateFloatAsState(summary.keptFraction, label = "kept")
    Box(modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(232.dp)) {
            val outer = size.minDimension / 2 - 24.dp.toPx()
            val inner = outer - 24.dp.toPx()
            val innerWidth = 15.dp.toPx()
            drawCircle(scheme.surfaceVariant, radius = outer, style = Stroke(5.dp.toPx()))
            HOUR_LABELS.forEach { hour ->
                val layout = measurer.measure("%02d".format(hour), labelStyle)
                val angle = Math.toRadians(angleOf(hour * 60).toDouble())
                val distance = outer + 15.dp.toPx()
                val x = this.center.x + distance * cos(angle).toFloat() - layout.size.width / 2
                val y = this.center.y + distance * sin(angle).toFloat() - layout.size.height / 2
                drawText(layout, topLeft = Offset(x, y))
            }
            arcs.forEach { arc ->
                val color = when (arc.tone) {
                    DialTone.IDLE -> scheme.onSurfaceVariant
                    DialTone.OPEN -> status.pending
                    DialTone.KEPT -> status.done
                    DialTone.MISSED -> status.failed
                }
                val sweep = (arc.endMinute - arc.startMinute) / MINUTES_PER_DAY * FULL_CIRCLE
                ringArc(outer, angleOf(arc.startMinute), sweep, 10.dp.toPx(), StrokeCap.Round, color)
            }
            drawCircle(scheme.surface, radius = inner, style = Stroke(innerWidth))
            val keptSweep = FULL_CIRCLE * kept
            if (summary.failed > 0) {
                val failedSweep = FULL_CIRCLE * summary.failedFraction
                ringArc(inner, TOP_ANGLE + keptSweep, failedSweep, innerWidth, StrokeCap.Butt, status.failed)
            }
            if (keptSweep > 0f) {
                drawArc(
                    brush = accent,
                    startAngle = TOP_ANGLE,
                    sweepAngle = keptSweep,
                    useCenter = false,
                    topLeft = Offset(this.center.x - inner, this.center.y - inner),
                    size = Size(inner * 2, inner * 2),
                    style = Stroke(innerWidth, cap = StrokeCap.Round),
                )
            }
            if (nowMinute != null) {
                val angle = Math.toRadians(angleOf(nowMinute).toDouble())
                val dot = Offset(
                    this.center.x + outer * cos(angle).toFloat(),
                    this.center.y + outer * sin(angle).toFloat(),
                )
                drawCircle(scheme.background, radius = 9.dp.toPx(), center = dot)
                drawCircle(scheme.onSurface, radius = 6.dp.toPx(), center = dot)
            }
        }
        center()
    }
}
