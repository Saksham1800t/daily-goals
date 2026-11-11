package com.example.dailygoals.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The logo is defined on the 108-unit launcher grid; only its central 72 units carry ink, so
 * drawing maps that square onto the canvas.
 */
private const val GRID_INSET = 18f
private const val GRID_SPAN = 72f
private const val RING_START_ANGLE = -15f
private const val RING_SWEEP_ANGLE = 310f

class LogoGeometry(scope: DrawScope, strokeUnits: Float) {
    private val unit = scope.size.minDimension / GRID_SPAN
    val stroke = Stroke(width = strokeUnits * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val center = point(54f, 54f)
    val ringRadius = 24f * unit
    val ringTopLeft = point(30f, 30f)
    val ringSize = Size(48f * unit, 48f * unit)

    val tick: Path = Path().apply {
        moveTo(point(42f, 55f))
        lineTo(point(51f, 64f))
        lineTo(point(77f, 33f))
    }

    val cross: Path = Path().apply {
        moveTo(point(43f, 43f))
        lineTo(point(65f, 65f))
        moveTo(point(65f, 43f))
        lineTo(point(43f, 65f))
    }

    private fun point(x: Float, y: Float) = Offset((x - GRID_INSET) * unit, (y - GRID_INSET) * unit)

    private fun Path.moveTo(offset: Offset) = moveTo(offset.x, offset.y)

    private fun Path.lineTo(offset: Offset) = lineTo(offset.x, offset.y)
}

fun DrawScope.drawLogoRing(geometry: LogoGeometry, brush: Brush) {
    drawArc(
        brush = brush,
        startAngle = RING_START_ANGLE,
        sweepAngle = RING_SWEEP_ANGLE,
        useCenter = false,
        topLeft = geometry.ringTopLeft,
        size = geometry.ringSize,
        style = geometry.stroke,
    )
}
