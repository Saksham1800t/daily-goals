package com.example.dailygoals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = VoltLime,
    onPrimary = VoltOnAccent,
    secondary = VoltMint,
    onSecondary = VoltOnAccent,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    error = DarkFailed,
)

private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    secondary = LeafTeal,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerHigh = LightSurface,
    outline = LightOutline,
    error = LightFailed,
)

@Immutable
data class StatusColors(val done: Color, val pending: Color, val failed: Color)

private val DarkStatus = StatusColors(done = DarkDone, pending = DarkPending, failed = DarkFailed)
private val LightStatus = StatusColors(done = LightDone, pending = LightPending, failed = LightFailed)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

val accentBrush: Brush
    @Composable get() = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )

@Composable
fun DailyGoalsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
