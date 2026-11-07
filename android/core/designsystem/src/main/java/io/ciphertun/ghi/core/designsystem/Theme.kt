package io.ciphertun.ghi.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private fun onColorFor(color: Color): Color =
    if (color.luminance() > .45f) Color(0xFF06101C) else Color.White

private fun ghiDarkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val background = if (amoled) Color.Black else GhiInk950
    val surface = if (amoled) Color(0xFF05070B) else GhiInk900
    val variant = if (amoled) Color(0xFF0B0F16) else GhiInk800
    return darkColorScheme(
        primary = accent,
        onPrimary = onColorFor(accent),
        primaryContainer = accent.copy(alpha = .22f),
        onPrimaryContainer = GhiFog100,
        secondary = GhiAccentCyan,
        tertiary = GhiAccentViolet,
        background = background,
        onBackground = GhiFog100,
        surface = surface,
        onSurface = GhiFog100,
        surfaceVariant = variant,
        onSurfaceVariant = GhiSlate300,
        outline = GhiSlate500,
        outlineVariant = GhiSlate500.copy(alpha = .35f),
        error = GhiSignalRed,
    )
}

private fun ghiLightScheme(accent: Color): ColorScheme =
    lightColorScheme(
        primary = accent,
        onPrimary = onColorFor(accent),
        primaryContainer = accent.copy(alpha = .18f),
        onPrimaryContainer = Color(0xFF0B1626),
        secondary = GhiAccentCyan,
        tertiary = GhiAccentViolet,
        background = Color(0xFFEEF3F9),
        onBackground = Color(0xFF0B1626),
        surface = Color(0xFFF7FAFD),
        onSurface = Color(0xFF0B1626),
        surfaceVariant = Color(0xFFDCE6F2),
        onSurfaceVariant = Color(0xFF4A5B70),
        outline = Color(0xFF7A8CA3),
        outlineVariant = Color(0xFF7A8CA3).copy(alpha = .35f),
        error = Color(0xFFD93A52),
    )

/** True when the given style renders with a dark colour scheme. */
@Composable
fun GhiStyle.isDark(): Boolean = when (mode) {
    GhiThemeMode.SYSTEM -> isSystemInDarkTheme()
    GhiThemeMode.LIGHT -> false
    GhiThemeMode.DARK, GhiThemeMode.AMOLED -> true
}

@Composable
fun GhiTheme(
    style: GhiStyle = GhiStyle(),
    content: @Composable () -> Unit,
) {
    val dark = style.isDark()
    val accent = style.accentColor
    val scheme = if (dark) ghiDarkScheme(accent, style.mode == GhiThemeMode.AMOLED) else ghiLightScheme(accent)
    CompositionLocalProvider(LocalGhiStyle provides style) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GhiTypography,
            content = content,
        )
    }
}

/**
 * Source-compatible overload for call sites written before [GhiStyle] existed.
 * The old implementation OR-ed the flag with the system setting, which made the
 * light theme unreachable; this version honours the flag.
 */
@Composable
fun GhiTheme(
    useDarkTheme: Boolean,
    content: @Composable () -> Unit,
) = GhiTheme(
    style = GhiStyle(mode = if (useDarkTheme) GhiThemeMode.DARK else GhiThemeMode.LIGHT),
    content = content,
)
