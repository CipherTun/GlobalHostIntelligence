package io.ciphertun.ghi.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.GhiSignalGreen
import io.ciphertun.ghi.core.ui.components.*

@Composable
fun SettingsScreen(
    discoveryLimit: Int, validationThreads: Int, sourceParallelism: Int, validationTimeout: Int, userAgent: String,
    enabledSources: Set<String>, animationsEnabled: Boolean, compactResults: Boolean,
    onSave: (Int, Int, Int, Int, String, Set<String>, Boolean, Boolean) -> Unit, onReset: () -> Unit
) {
    var limit by remember(discoveryLimit) { mutableStateOf(discoveryLimit.toString()) }
    var threads by remember(validationThreads) { mutableStateOf(validationThreads.toString()) }
    var parallel by remember(sourceParallelism) { mutableStateOf(sourceParallelism.toString()) }
    var timeout by remember(validationTimeout) { mutableStateOf(validationTimeout.toString()) }
    var agent by remember(userAgent) { mutableStateOf(userAgent) }
    var sources by remember(enabledSources) { mutableStateOf(enabledSources.toMutableSet()) }
    var animations by remember(animationsEnabled) { mutableStateOf(animationsEnabled) }
    var compact by remember(compactResults) { mutableStateOf(compactResults) }
    var saved by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }

    GhiScreenScaffold("Settings") { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { GhiHero("Engine settings", "Configure discovery, validation, sources, networking and UI behavior from one place.") }
            item { GhiSectionHeader("Discovery engine", "Controls are applied to new discovery runs.") }
            item {
                GhiCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(limit, { limit = it }, "Maximum validated hosts (10–5000)")
                    NumberField(threads, { threads = it }, "Validation threads (1–256)")
                    NumberField(parallel, { parallel = it }, "Parallel discovery sources (1–32)")
                    NumberField(timeout, { timeout = it }, "Validation timeout seconds (2–60)")
                    OutlinedTextField(agent, { agent = it }, label = { Text("Validation User-Agent") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                } }
            }
            item { GhiSectionHeader("Interface", "Only visual behavior; discovery and network capabilities are unchanged.") }
            item { GhiCard { Column { SwitchRow("Animations", animations) { animations = it }; SwitchRow("Compact result cards", compact) { compact = it } } } }
            item {
                Button(onClick = { onSave(limit.toIntOrNull() ?: discoveryLimit, threads.toIntOrNull() ?: validationThreads, parallel.toIntOrNull() ?: sourceParallelism, timeout.toIntOrNull() ?: validationTimeout, agent, sources, animations, compact); saved = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("SAVE SETTINGS") }
            }
            if (saved) item { Text("Settings saved and will be used by the next run.", color = GhiSignalGreen, style = MaterialTheme.typography.labelSmall) }
            item { OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("RESET TO DEFAULTS") } }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset settings?") }, text = { Text("Restore the engine and interface to the default configuration.") }, confirmButton = { TextButton(onClick = { onReset(); reset = false }) { Text("RESET") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("CANCEL") } })
}

@Composable private fun NumberField(value: String, onValue: (String) -> Unit, label: String) { OutlinedTextField(value, { onValue(it.filter(Char::isDigit).take(4)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
@Composable private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChecked) } }
