package io.ciphertun.ghi.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*
import kotlin.math.roundToInt

private val userAgentPresets = listOf(
    "Chrome Android" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36",
    "Firefox Android" to "Mozilla/5.0 (Android 14; Mobile; rv:131.0) Gecko/131.0 Firefox/131.0",
    "Safari iOS" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
)

/**
 * Settings: appearance applies live (no save needed); engine settings are
 * edited as a draft and committed from a floating save bar.
 */
@Composable
fun SettingsScreen(
    engine: GhiEngineSettings,
    style: GhiStyle,
    onEngineSave: (GhiEngineSettings) -> Unit,
    onStyleChange: (GhiStyle) -> Unit,
    onResetEngine: () -> Unit,
    onPrivacyOptions: () -> Unit = {},
) {
    var draft by remember(engine) { mutableStateOf(engine) }
    var limitText by remember(engine) { mutableStateOf(engine.discoveryLimit.toString()) }
    var showKey by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(210f) }

    val limitValue = limitText.toIntOrNull()
    val limitOk = limitValue != null && limitValue in 10..5000
    val effective = if (limitOk) draft.copy(discoveryLimit = limitValue!!) else draft
    val dirty = effective != engine

    GhiScreenScaffold("Settings") { modifier ->
        Box(modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp)
            ) {
                item { GhiHero("Make it yours", "Appearance changes apply instantly. Engine changes are saved with the bar at the bottom.") }

                // ------------------------------------------------ appearance
                item { GhiSectionHeader("Appearance", "Theme, colour, glass and shape") }
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            LabeledChips("Theme") {
                                GhiThemeMode.values().forEach { m ->
                                    GhiChip(m.label, style.mode == m, { onStyleChange(style.copy(mode = m)) })
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Accent colour", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    GhiAccent.values().forEach { a ->
                                        Swatch(a.color, style.customAccentArgb == null && style.accent == a, a.label) {
                                            onStyleChange(style.copy(accent = a, customAccentArgb = null))
                                        }
                                    }
                                    Swatch(Color.hsv(hue, .7f, 1f), style.customAccentArgb != null, "Custom") {
                                        onStyleChange(style.copy(customAccentArgb = Color.hsv(hue, .7f, 1f).toArgb()))
                                    }
                                }
                                if (style.customAccentArgb != null) {
                                    Slider(
                                        value = hue,
                                        onValueChange = {
                                            hue = it
                                            onStyleChange(style.copy(customAccentArgb = Color.hsv(it, .7f, 1f).toArgb()))
                                        },
                                        valueRange = 0f..360f
                                    )
                                }
                            }
                            Column {
                                Row {
                                    Text("Glass opacity", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    Text("${(style.glass * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = style.glass,
                                    onValueChange = { onStyleChange(style.copy(glass = it)) },
                                    valueRange = .35f..0.95f
                                )
                            }
                            LabeledChips("Corners") {
                                GhiCorners.values().forEach { c ->
                                    GhiChip(c.label, style.corners == c, { onStyleChange(style.copy(corners = c)) })
                                }
                            }
                            LabeledChips("Density") {
                                GhiDensity.values().forEach { d ->
                                    GhiChip(d.label, style.density == d, { onStyleChange(style.copy(density = d)) })
                                }
                            }
                            SwitchRow("Animations", "Hero glow, thinking spark, list transitions", style.animations) { onStyleChange(style.copy(animations = it)) }
                            SwitchRow("Backdrop glow", "Soft accent light behind every screen", style.backdrop) { onStyleChange(style.copy(backdrop = it)) }
                            SwitchRow("Compact results", "Hide the raw technical block in host details", style.compactResults) { onStyleChange(style.copy(compactResults = it)) }
                            TextButton(onClick = { onStyleChange(GhiStyle()) }) {
                                Icon(Icons.Filled.Restore, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reset appearance")
                            }
                        }
                    }
                }

                // ------------------------------------------------ engine
                item { GhiSectionHeader("Discovery engine", "Applied to the next run") }
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = limitText,
                                onValueChange = { limitText = it.filter(Char::isDigit).take(4) },
                                label = { Text("Maximum validated hosts (10–5000)") },
                                isError = !limitOk,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            SliderRow("Validation workers", draft.validationThreads, 1..256, "Hosts probed at the same time.") { draft = draft.copy(validationThreads = it) }
                            SliderRow("Parallel sources", draft.sourceParallelism, 1..32, "Passive sources queried at the same time.") { draft = draft.copy(sourceParallelism = it) }
                            SliderRow("Timeout (seconds)", draft.validationTimeout, 2..60) { draft = draft.copy(validationTimeout = it) }
                            SliderRow("Retries on timeout", draft.validationRetries, 0..2, "Extra attempts for hosts that time out.") { draft = draft.copy(validationRetries = it) }
                            SwitchRow("Adaptive concurrency", "Automatically back off when many probes time out", draft.adaptiveConcurrency) { draft = draft.copy(adaptiveConcurrency = it) }
                            SwitchRow("Any HTTP answer is reachable", "Also keep 401, 403, 404 and 5xx hosts (default: only 2xx–3xx)", draft.acceptAnyResponse) { draft = draft.copy(acceptAnyResponse = it) }
                        }
                    }
                }

                item { GhiSectionHeader("Network identity", "User-Agent sent while validating hosts") }
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                userAgentPresets.forEach { (name, value) ->
                                    GhiChip(name, draft.userAgent == value, { draft = draft.copy(userAgent = value) })
                                }
                            }
                            OutlinedTextField(
                                value = draft.userAgent,
                                onValueChange = { draft = draft.copy(userAgent = it) },
                                label = { Text("Validation User-Agent") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }
                }

                item { GhiSectionHeader("API keys", "Stored encrypted on this device (Android Keystore)") }
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp)) {
                            OutlinedTextField(
                                value = draft.projectDiscoveryApiKey,
                                onValueChange = { draft = draft.copy(projectDiscoveryApiKey = it) },
                                label = { Text("ProjectDiscovery Chaos key (optional)") },
                                supportingText = { Text("Used only by the ProjectDiscovery source.") },
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle key visibility")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                // ------------------------------------------------ privacy & reset
                item { GhiSectionHeader("Privacy & data") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPrivacyOptions, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PrivacyTip, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ad privacy options")
                        }
                        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Restore, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset engine to defaults")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = dirty,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                GhiCard {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (limitOk) "Unsaved engine changes" else "Fix the host limit (10–5000)",
                            Modifier.weight(1f),
                            color = if (limitOk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            draft = engine
                            limitText = engine.discoveryLimit.toString()
                        }) { Text("Discard") }
                        Button(onClick = { onEngineSave(effective) }, enabled = limitOk) {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset engine settings?") },
            text = { Text("Restores limits, workers, timeouts, sources and the User-Agent. Appearance and API keys of other providers are kept.") },
            confirmButton = { TextButton(onClick = { onResetEngine(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LabeledChips(label: String, chips: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = chips)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(2.dp, ring), CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, description, tint = Color.Black.copy(alpha = .75f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, hint: String? = null, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChecked)
    }
}
