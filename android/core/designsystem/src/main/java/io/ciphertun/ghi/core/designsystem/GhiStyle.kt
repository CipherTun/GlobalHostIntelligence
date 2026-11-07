package io.ciphertun.ghi.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Light / dark behaviour of the whole app. */
enum class GhiThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    AMOLED("AMOLED"),
    LIGHT("Light (beta)"),
}

/** Built-in accent presets. A custom colour can override them via [GhiStyle.customAccentArgb]. */
enum class GhiAccent(val label: String, val color: Color) {
    OCEAN("Ocean", Color(0xFF4DA3FF)),
    CYAN("Cyan", Color(0xFF22D3EE)),
    VIOLET("Violet", Color(0xFF8B7CFF)),
    MINT("Mint", Color(0xFF35D07F)),
    SUNSET("Sunset", Color(0xFFFF9F43)),
    ROSE("Rose", Color(0xFFFF6B9A)),
    LIME("Lime", Color(0xFFB6F04A)),
}

/** Corner radius of glass cards, fields and sheets (in dp). */
enum class GhiCorners(val label: String, val radius: Int) {
    SHARP("Sharp", 10),
    SOFT("Soft", 18),
    ROUND("Round", 28),
}

/** Spacing scale applied to list rows and cards. */
enum class GhiDensity(val label: String, val scale: Float) {
    COMPACT("Compact", .82f),
    COMFORT("Comfort", 1f),
    AIRY("Airy", 1.18f),
}

/**
 * Every user-customisable visual property lives here so the Settings screen,
 * the theme and all components share one source of truth.
 *
 * The defaults reproduce the pre-5.0 look (dark, ocean blue, soft corners).
 */
@Immutable
data class GhiStyle(
    val mode: GhiThemeMode = GhiThemeMode.DARK,
    val accent: GhiAccent = GhiAccent.OCEAN,
    /** ARGB colour chosen by the user; when non-null it replaces [accent]. */
    val customAccentArgb: Int? = null,
    /** Opacity of glass surfaces, 0.35 (airy) .. 0.95 (solid). */
    val glass: Float = .72f,
    val corners: GhiCorners = GhiCorners.SOFT,
    val density: GhiDensity = GhiDensity.COMFORT,
    val animations: Boolean = true,
    /** Soft accent glow behind screens. */
    val backdrop: Boolean = true,
    val compactResults: Boolean = false,
) {
    val accentColor: Color
        get() = customAccentArgb?.let { Color(it or 0xFF000000.toInt()) } ?: accent.color
}

val LocalGhiStyle = compositionLocalOf { GhiStyle() }
