package com.example.dailygoals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Every colour dialogs, pickers and menus read is set here, so none of them falls back to Material's
 * default purple-grey tones.
 */
private val DarkColors = darkColorScheme(
    primary = VoltLime,
    onPrimary = VoltOnAccent,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = VoltMint,
    onSecondary = VoltOnAccent,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = VoltMint,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkFailed,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    inverseSurface = DarkText,
    inverseOnSurface = LightText,
    inversePrimary = LeafGreen,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LeafTeal,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LeafTeal,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightFailed,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    inverseSurface = LightText,
    inverseOnSurface = DarkText,
    inversePrimary = VoltLime,
    scrim = Color.Black,
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
fun SkritiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme) {
            // Without this, any text that has no colour of its own is drawn black, which disappears on the dark background.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
