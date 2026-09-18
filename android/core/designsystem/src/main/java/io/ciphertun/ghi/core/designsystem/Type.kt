package io.ciphertun.ghi.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Compact rounded/sans presentation to keep technical screens readable on
// small Android displays while leaving identifiers in monospace.
private val GhiSans = FontFamily.SansSerif

val GhiTypography = Typography(
    titleLarge = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = GhiSans, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)

val GhiMonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
