package io.ciphertun.ghi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.LocalGhiStyle

/**
 * Full-screen backdrop: solid background plus two soft accent glows. Wrapping
 * the NavHost in this lets every (transparent) screen scaffold share it.
 */
@Composable
fun GhiBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = LocalGhiStyle.current
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val tertiary = scheme.tertiary
    val glow = if (style.backdrop) {
        Modifier.drawBehind {
            val radius = size.minDimension * .9f
            val topRight = Offset(size.width * .90f, size.height * .04f)
            val bottomLeft = Offset(size.width * .05f, size.height * .92f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = .20f), Color.Transparent),
                    center = topRight,
                    radius = radius,
                ),
                radius = radius,
                center = topRight,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = .10f), Color.Transparent),
                    center = bottomLeft,
                    radius = radius,
                ),
                radius = radius,
                center = bottomLeft,
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier.fillMaxSize().background(scheme.background).then(glow),
        content = content,
    )
}

/** Selectable chip used for filters / sort options. */
@Composable
fun GhiChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val radius = minOf(LocalGhiStyle.current.corners.radius, 16)
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(radius.dp),
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (icon != null) {
            { Icon(icon, null, Modifier.size(16.dp)) }
        } else {
            null
        },
    )
}

/** Row of small glass tiles: label + value pairs (counters, timings...). */
@Composable
fun GhiStatRow(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (stat in stats) {
            GhiCard(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(stat.second, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        stat.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Rounded progress bar; pass null for an indeterminate bar. */
@Composable
fun GhiProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    if (progress == null) {
        LinearProgressIndicator(modifier.fillMaxWidth().clip(shape))
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().clip(shape),
        )
    }
}

/** Glass edge shared by cards: a faint light-to-dark gradient stroke. */
@Composable
fun ghiGlassBorder(): BorderStroke {
    val on = MaterialTheme.colorScheme.onSurface
    return BorderStroke(1.dp, Brush.verticalGradient(listOf(on.copy(alpha = .16f), on.copy(alpha = .05f))))
}
