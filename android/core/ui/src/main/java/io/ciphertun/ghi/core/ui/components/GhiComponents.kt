package io.ciphertun.ghi.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import kotlinx.coroutines.delay

/** Corner radius of glass surfaces, driven by the user's style. */
@Composable
private fun glassShape(extra: Int = 0): RoundedCornerShape =
    RoundedCornerShape((LocalGhiStyle.current.corners.radius + extra).coerceAtLeast(6).dp)

@Composable
private fun glassColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LocalGhiStyle.current.glass)

@Composable
fun GhiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = glassShape(),
        colors = CardDefaults.cardColors(containerColor = glassColor()),
        border = ghiGlassBorder(),
        content = content
    )
}

@Composable
fun GhiHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val style = LocalGhiStyle.current
    val scheme = MaterialTheme.colorScheme
    val alpha = if (style.animations) {
        val transition = rememberInfiniteTransition(label = "ghi-hero-glow")
        val animated by transition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
            label = "ghi-hero-alpha"
        )
        animated
    } else {
        0.28f
    }
    GhiCard(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.primary.copy(alpha = alpha),
                            scheme.surfaceVariant.copy(alpha = 0.30f),
                            scheme.tertiary.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding((18 * style.density.scale).dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GhiSectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun GhiMetric(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val density = LocalGhiStyle.current.density.scale
    GhiCard(modifier) {
        Row(Modifier.fillMaxWidth().padding((14 * density).dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class StatusTone { OK, WARN, ERROR, INFO, NEUTRAL }

@Composable
fun GhiStatusPill(text: String, tone: StatusTone = StatusTone.NEUTRAL) {
    val color = when (tone) {
        StatusTone.OK -> GhiSignalGreen
        StatusTone.WARN -> GhiSignalAmber
        StatusTone.ERROR -> GhiSignalRed
        StatusTone.INFO -> MaterialTheme.colorScheme.primary
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(7.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GhiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Search",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        shape = glassShape(-4)
    )
}

@Composable
fun GhiActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalGhiStyle.current.density.scale
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = glassShape(-2),
        colors = CardDefaults.cardColors(containerColor = glassColor()),
        border = ghiGlassBorder()
    ) {
        Row(Modifier.padding((15 * density).dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .16f), shape = glassShape(-6)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun GhiEmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Filled.Search,
    modifier: Modifier = Modifier
) {
    GhiCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(38.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Gemini-inspired thinking state, implemented locally.
 *
 * The spark drifts through the hue spectrum (one full turn every ~3 s; it was
 * 0.4 s before, which strobed). A new contextual micro-message is selected from
 * the supplied hint every ~1.8 s. When animations are turned off in Settings the
 * spark stays static.
 */
@Composable
fun GhiBusyIndicator(
    visible: Boolean,
    hint: String = "network",
    modifier: Modifier = Modifier
) {
    val animate = LocalGhiStyle.current.animations
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "ghi-thinking")
        val hue by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ghi-spectrum"
        )
        val pulse by transition.animateFloat(
            initialValue = .9f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ghi-spark-pulse"
        )

        var messageIndex by remember(hint) { mutableIntStateOf((System.currentTimeMillis() / 1800L).toInt()) }
        var sparkVisible by remember { mutableStateOf(false) }
        LaunchedEffect(visible, hint) {
            if (!visible) {
                sparkVisible = false
                return@LaunchedEffect
            }
            sparkVisible = false
            delay(420)
            sparkVisible = true
            while (true) {
                delay(1800)
                messageIndex++
            }
        }

        val messages = thinkingMessages(hint)
        val message = messages[messageIndex % messages.size]
        val sparkColor = if (animate) Color.hsv(hue, .72f, 1f, .95f) else MaterialTheme.colorScheme.primary

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sparkVisible) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "Working",
                    tint = sparkColor,
                    modifier = Modifier.size(if (animate) 23.dp * pulse else 23.dp)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
        }
    }
}

private fun thinkingMessages(hint: String): List<String> {
    val key = hint.lowercase()
    return when {
        "discover" in key || "subdomain" in key -> listOf(
            "Reading the discovery sources…",
            "Comparing observed hostnames…",
            "Checking live evidence…",
            "Merging source observations…",
            "Preparing the clearest result…"
        )
        "dns" in key -> listOf(
            "Reading DNS answers…",
            "Comparing resolver evidence…",
            "Checking authoritative signals…",
            "Mapping returned records…",
            "Preparing the DNS view…"
        )
        "tls" in key || "certificate" in key -> listOf(
            "Reading the TLS handshake…",
            "Inspecting certificate evidence…",
            "Comparing protocol signals…",
            "Mapping the security details…",
            "Preparing the clearest result…"
        )
        "rdap" in key || "whois" in key -> listOf(
            "Asking the registry…",
            "Reading registration records…",
            "Mapping owners and contacts…",
            "Preparing the registration view…"
        )
        "payload" in key || "request" in key -> listOf(
            "Building the HTTP request…",
            "Checking request syntax…",
            "Calculating headers and length…",
            "Preparing a usable request…",
            "Formatting the final payload…"
        )
        "agent" in key || "intelligen" in key -> listOf(
            "Reading the available evidence…",
            "Connecting the useful signals…",
            "Comparing the returned data…",
            "Organizing the intelligence…",
            "Preparing the clearest answer…"
        )
        else -> listOf(
            "Reading the available network evidence…",
            "Checking the live response…",
            "Comparing the returned signals…",
            "Organizing what was found…",
            "Preparing the clearest result…"
        )
    }
}
