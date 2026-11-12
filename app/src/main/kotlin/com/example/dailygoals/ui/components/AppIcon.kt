package com.example.dailygoals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class AppIconKind { LOGO, LIST, CALENDAR, CLOSE, PLUS, CHEVRON_LEFT, CHEVRON_RIGHT, SETTINGS }

private const val LINE_WIDTH = 0.09f

/** Icons are drawn here so the app does not need an icon library. Coordinates are fractions of the box. */
@Composable
fun AppIcon(kind: AppIconKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val side = size.minDimension
        val width = side * LINE_WIDTH
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * side, y1 * side), Offset(x2 * side, y2 * side), width, StrokeCap.Round)
        when (kind) {
            AppIconKind.LOGO -> drawLogo(tint)
            AppIconKind.LIST -> listOf(0.25f, 0.5f, 0.75f).forEach { y ->
                line(0.12f, y, 0.14f, y)
                line(0.36f, y, 0.88f, y)
            }
            AppIconKind.CALENDAR -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(0.14f * side, 0.2f * side),
                    size = Size(0.72f * side, 0.66f * side),
                    cornerRadius = CornerRadius(0.14f * side),
                    style = Stroke(width),
                )
                line(0.14f, 0.42f, 0.86f, 0.42f)
                line(0.34f, 0.1f, 0.34f, 0.26f)
                line(0.66f, 0.1f, 0.66f, 0.26f)
            }
            AppIconKind.CLOSE -> {
                line(0.24f, 0.24f, 0.76f, 0.76f)
                line(0.76f, 0.24f, 0.24f, 0.76f)
            }
            AppIconKind.PLUS -> {
                line(0.5f, 0.18f, 0.5f, 0.82f)
                line(0.18f, 0.5f, 0.82f, 0.5f)
            }
            AppIconKind.SETTINGS -> {
                line(0.12f, 0.3f, 0.88f, 0.3f)
                line(0.12f, 0.7f, 0.88f, 0.7f)
                drawCircle(tint, radius = 0.12f * side, center = Offset(0.62f * side, 0.3f * side))
                drawCircle(tint, radius = 0.12f * side, center = Offset(0.38f * side, 0.7f * side))
            }
            AppIconKind.CHEVRON_LEFT -> {
                line(0.6f, 0.2f, 0.34f, 0.5f)
                line(0.34f, 0.5f, 0.6f, 0.8f)
            }
            AppIconKind.CHEVRON_RIGHT -> {
                line(0.4f, 0.2f, 0.66f, 0.5f)
                line(0.66f, 0.5f, 0.4f, 0.8f)
            }
        }
    }
}

private fun DrawScope.drawLogo(tint: Color) {
    val logo = LogoGeometry(this, strokeUnits = 9f)
    drawLogoRing(logo, SolidColor(tint))
    drawPath(logo.tick, tint, style = logo.stroke)
}
