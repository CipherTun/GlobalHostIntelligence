package io.ciphertun.ghi.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GhiDarkColors = darkColorScheme(
    background = GhiInk950,
    surface = GhiInk900,
    surfaceVariant = GhiInk800,
    primary = GhiAccentBlue,
    secondary = GhiAccentCyan,
    tertiary = GhiAccentViolet,
    onBackground = GhiFog100,
    onSurface = GhiFog100,
    outline = GhiSlate500,
    error = GhiSignalRed,
)

private val GhiLightColors = lightColorScheme(
    primary = GhiAccentBlue,
    secondary = GhiAccentCyan,
    tertiary = GhiAccentViolet,
    error = GhiSignalRed,
)

@Composable
fun GhiTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme || isSystemInDarkTheme()) GhiDarkColors else GhiLightColors,
        typography = GhiTypography,
        content = content,
    )
}
