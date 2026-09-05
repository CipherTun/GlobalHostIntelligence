package io.ciphertun.ghi.core.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** App-level hamburger action supplied by the activity shell. */
val LocalGhiOpenDrawer: ProvidableCompositionLocal<(() -> Unit)?> =
    staticCompositionLocalOf { null }
