package io.ciphertun.ghi.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*
import java.util.Locale

data class DomainPing(val domain: String, val latencyMs: Long, val status: Int)
private data class CountryChoice(val code: String, val name: String, val flag: String)
private fun flag(code: String): String = code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
private val countries: List<CountryChoice> by lazy { Locale.getISOCountries().map { CountryChoice(it, Locale("", it).displayCountry, flag(it)) }.sortedBy { it.name } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    results: List<DomainPing>, running: Boolean, status: String, error: String?, elapsedMs: Long,
    enabledSources: Set<String>, onStart: (String, String) -> Unit, onStop: () -> Unit
) {
    var scopeMode by remember { mutableStateOf("Country") }
    var countrySearch by remember { mutableStateOf("") }
    var domainSeed by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CountryChoice?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val filtered = countries.filter { countrySearch.isBlank() || countrySearch.contains(it.code, true) || it.name.contains(countrySearch, true) }.take(80)

    GhiScreenScaffold("Discover") { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)) {
            item { GhiHero("Global host discovery", "Parallel passive discovery + concurrent HTTP/HTTPS validation. Only live 2xx–3xx hosts are promoted.") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhiStatusPill(if (running) "● LIVE" else status, if (running) StatusTone.INFO else if (status == "COMPLETED") StatusTone.OK else StatusTone.NEUTRAL)
                    Spacer(Modifier.weight(1f))
                    Text("${enabledSources.size} sources", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterButton("Country", scopeMode == "Country") { scopeMode = "Country" }
                    FilterButton("Domain", scopeMode == "Domain") { scopeMode = "Domain" }
                }
            }
            if (scopeMode == "Country") {
                item {
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        OutlinedTextField(countrySearch, { countrySearch = it; expanded = true }, label = { Text("Country / ISO code") }, leadingIcon = { Icon(Icons.Filled.Public, null) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
                        ExposedDropdownMenu(expanded && filtered.isNotEmpty(), { expanded = false }) {
                            filtered.forEach { c -> DropdownMenuItem(text = { Text("${c.flag}  ${c.name}  (${c.code})") }, onClick = { selected = c; countrySearch = c.name; expanded = false }) }
                        }
                    }
                }
                item {
                    Surface(color = GhiInk800, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(selected?.let { "${it.flag} ${it.name}" } ?: "Select a country", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            selected?.let { Text(it.code, color = GhiAccentBlue, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else {
                item { OutlinedTextField(domainSeed, { domainSeed = it }, label = { Text("Domain") }, placeholder = { Text("example.com") }, leadingIcon = { Icon(Icons.Filled.Domain, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            }
            item {
                if (running) Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("STOP DISCOVERY") }
                else Button(onClick = { if (scopeMode == "Country") selected?.let { onStart(it.code.lowercase(), "country") } else onStart(domainSeed.trim(), "domain") }, enabled = if (scopeMode == "Country") selected != null else domainSeed.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("START DISCOVERY") }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Live results", style = MaterialTheme.typography.titleMedium); Text("${results.size} validated hosts • ${elapsedMs}ms", color = GhiSlate300, style = MaterialTheme.typography.labelSmall) }
                    if (results.isNotEmpty()) IconButton(onClick = { clipboard.setText(AnnotatedString(results.joinToString("\n") { it.domain })) }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                }
            }
            error?.let { msg -> item { GhiCard { Text(msg, color = GhiSignalAmber, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) } } }
            items(results, key = { it.domain }) { result ->
                AnimatedVisibility(true, enter = fadeIn() + slideInVertically(initialOffsetY = { 18 })) {
                    GhiCard {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(result.domain, style = MaterialTheme.typography.titleMedium); Text("HTTP ${result.status}", color = GhiSignalGreen, style = MaterialTheme.typography.labelSmall) }
                            Text("${result.latencyMs}ms", color = latencyColor(result.latencyMs), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(result.domain)) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                        }
                    }
                }
            }
        }
    }
}

private fun latencyColor(ms: Long) = when { ms <= 80 -> GhiSignalGreen; ms <= 200 -> GhiSignalAmber; else -> GhiSignalRed }

@Composable private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) { if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) } }
