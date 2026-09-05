package io.ciphertun.ghi.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*

@Composable
fun GhiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GhiInk800),
        border = androidx.compose.foundation.BorderStroke(1.dp, GhiInk700),
        content = content,
    )
}

@Composable
fun GhiHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    GhiCard(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(GhiNavy700.copy(alpha = .82f), GhiInk800))
            ).padding(18.dp)
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
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun GhiMetric(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    GhiCard(modifier) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = GhiAccentBlue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)) }
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(7.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class StatusTone { OK, WARN, ERROR, INFO, NEUTRAL }

@Composable
fun GhiSearchField(value: String, onValueChange: (String) -> Unit, label: String = "Search", modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
fun GhiActionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = GhiInk800), border = androidx.compose.foundation.BorderStroke(1.dp, GhiInk700)) {
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
fun GhiEmptyState(title: String, message: String, icon: ImageVector = Icons.Filled.Search, modifier: Modifier = Modifier) {
    GhiCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = GhiSlate500, modifier = Modifier.size(38.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun GhiBusyIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val transition = rememberInfiniteTransition(label = "ghi-scan")
        val alpha = transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(GhiSignalGreen.copy(alpha = alpha.value), RoundedCornerShape(50)))
            Text("LIVE", color = GhiSignalGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
