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

private val glassShape = RoundedCornerShape(18.dp)

@Composable
fun GhiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = glassShape,
        colors = CardDefaults.cardColors(containerColor = GhiInk800.copy(alpha = .72f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GhiSlate500.copy(alpha = .22f)),
        content = content
    )
}

@Composable
fun GhiHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ghi-hero-glow")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ghi-hero-alpha"
    )
    GhiCard(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            GhiNavy700.copy(alpha = alpha),
                            GhiInk800.copy(alpha = 0.48f),
                            GhiAccentCyan.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = GhiSlate300)
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
    GhiCard(modifier) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, tint = GhiAccentBlue, modifier = Modifier.size(20.dp))
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
        StatusTone.INFO -> GhiAccentBlue
        StatusTone.NEUTRAL -> GhiSlate300
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
        shape = RoundedCornerShape(14.dp)
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GhiInk800.copy(alpha = .72f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GhiSlate500.copy(alpha = .18f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = GhiNavy700.copy(alpha = .45f), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, null, tint = GhiAccentBlue, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = GhiSlate500)
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
            Icon(icon, null, tint = GhiSlate500, modifier = Modifier.size(38.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Gemini-inspired thinking state, implemented locally.
 *
 * The spark sweeps through the full HSV hue spectrum. A new contextual
 * micro-message is selected from the supplied hint every ~1.8s. Messages
 * are intentionally short, thin and low-contrast so they communicate work
 * without becoming a second result panel.
 */
@Composable
fun GhiBusyIndicator(
    visible: Boolean,
    hint: String = "network",
    modifier: Modifier = Modifier
) {
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
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ghi-spectrum"
        )
        val pulse by transition.animateFloat(
            initialValue = .86f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
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
            // The quiet context message appears first; the animated spark follows.
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
        val sparkColor = Color.hsv(hue, .82f, 1f, .95f)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sparkVisible) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "Working",
                    tint = sparkColor,
                    modifier = Modifier.size(23.dp * pulse)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
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
