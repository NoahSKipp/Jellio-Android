package com.jellio.android.ui.theme

import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val JellioColorScheme = darkColorScheme(
    background = JellioBg,
    onBackground = JellioText,
    surface = JellioBgElevated,
    onSurface = JellioText,
    surfaceVariant = JellioBgElevated,
    onSurfaceVariant = JellioTextSecondary,
    primary = JellioSecondary,
    onPrimary = JellioBg,
    secondary = JellioSecondaryVariant,
    onSecondary = JellioBg,
    border = JellioBorder,
    borderVariant = JellioBorder,
)

private val JellioM3ColorScheme = m3DarkColorScheme(
    primary = JellioSecondary,
    onPrimary = JellioBg,
    primaryContainer = JellioBgElevated,
    onPrimaryContainer = JellioText,
    secondary = JellioSecondaryVariant,
    onSecondary = JellioBg,
    secondaryContainer = Color(0xFF26262A),
    onSecondaryContainer = JellioText,
    background = JellioBg,
    onBackground = JellioText,
    surface = JellioBgElevated,
    onSurface = JellioText,
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = JellioTextSecondary,
    surfaceContainerLowest = JellioBg,
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = JellioBgElevated,
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2A2A2E),
    outline = JellioBorder,
    outlineVariant = JellioBorder.copy(alpha = 0.6f),
    error = JellioDanger,
    onError = JellioBg,
    inverseSurface = JellioSecondary,
    inverseOnSurface = JellioBg,
)

@Composable
fun JellioTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JellioColorScheme,
        typography = JellioTypography,
    ) {
        M3MaterialTheme(
            colorScheme = JellioM3ColorScheme,
            typography = JellioM3Typography,
        ) {
            CompositionLocalProvider(LocalTvScale provides rememberTvScale(), content = content)
        }
    }
}
