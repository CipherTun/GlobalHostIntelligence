package io.ciphertun.ghi.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

data class DomainPing(val domain: String, val latencyMs: Long, val status: Int, val live: Boolean = true)
private data class CountryChoice(val code: String, val name: String, val flag: String)
private fun flag(code: String): String = code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
private val countries: List<CountryChoice> by lazy { Locale.getISOCountries().map { CountryChoice(it, Locale("", it).displayCountry, flag(it)) }.sortedBy { it.name } }

private val byLatency = compareByDescending<DomainPing> { it.live }
    .thenBy { if (it.live) it.latencyMs else Long.MAX_VALUE }
    .thenBy { it.domain }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    discoveredResults: List<DomainPing>,
    liveResults: List<DomainPing>,
    running: Boolean,
    status: String,
    error: String?,
    elapsedMs: Long,
    enabledSources: Set<String>,
    onStart: (String, String) -> Unit,
    onStop: () -> Unit,
    onAnalyze: (String) -> String,
    progress: DiscoveryProgress = DiscoveryProgress(),
    acceptAnyResponse: Boolean = false,
) {
    var scopeMode by rememberSaveable { mutableStateOf("Country") }
    var countrySearch by rememberSaveable { mutableStateOf("") }
    var domainSeed by rememberSaveable { mutableStateOf("") }
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllDiscovered by rememberSaveable { mutableStateOf(true) }
    var fastestFirst by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val selected = remember(selectedCode) { selectedCode?.let { code -> countries.firstOrNull { it.code == code } } }
    val filteredCountries = remember(countrySearch) {
        countries.filter { countrySearch.isBlank() || it.code.contains(countrySearch, true) || it.name.contains(countrySearch, true) }.take(80)
    }
    val liveByDomain = remember(liveResults) { liveResults.associateBy { it.domain } }
    val visible = remember(discoveredResults, liveResults, showAllDiscovered, fastestFirst, filter) {
        val base = if (showAllDiscovered) discoveredResults.map { liveByDomain[it.domain] ?: it } else liveResults
        val matched = if (filter.isBlank()) base else base.filter { it.domain.contains(filter.trim(), true) }
        if (fastestFirst) matched.sortedWith(byLatency) else matched
    }

    GhiScreenScaffold("Discovery") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "Global host discovery",
                    "Independent passive sources run concurrently. Candidates stay visible; " +
                        if (acceptAnyResponse) "any hosts that answer over HTTP(S) are marked LIVE." else "only 2xx–3xx hosts are marked LIVE."
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhiStatusPill(
                        if (running) "● SCANNING" else status,
                        if (running) StatusTone.INFO else if (status == "COMPLETED") StatusTone.OK else if (status == "PARTIAL") StatusTone.WARN else StatusTone.NEUTRAL
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${enabledSources.size} sources", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhiChip("Country", scopeMode == "Country", { scopeMode = "Country" }, icon = Icons.Filled.Public)
                    GhiChip("Domain", scopeMode == "Domain", { scopeMode = "Domain" }, icon = Icons.Filled.Domain)
                }
            }
            if (scopeMode == "Country") {
                item {
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        OutlinedTextField(countrySearch, { countrySearch = it; expanded = true }, label = { Text("Country / ISO code") }, leadingIcon = { Icon(Icons.Filled.Public, null) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
                        ExposedDropdownMenu(expanded && filteredCountries.isNotEmpty(), { expanded = false }) {
                            filteredCountries.forEach { c -> DropdownMenuItem(text = { Text("${c.flag}  ${c.name}  (${c.code})") }, onClick = { selectedCode = c.code; countrySearch = c.name; expanded = false }) }
                        }
                    }
                }
                item {
                    GhiCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(selected?.let { "${it.flag} ${it.name}" } ?: "Select a country", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            selected?.let { Text(it.code, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else {
                item { OutlinedTextField(domainSeed, { domainSeed = it }, label = { Text("Domain") }, placeholder = { Text("example.com") }, leadingIcon = { Icon(Icons.Filled.Domain, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            }
            item {
                if (running) Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("STOP DISCOVERY") }
                else Button(
                    onClick = { if (scopeMode == "Country") selected?.let { onStart(it.code.lowercase(), "country") } else onStart(domainSeed.trim(), "domain") },
                    enabled = if (scopeMode == "Country") selected != null else domainSeed.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("START DISCOVERY") }
                GhiBusyIndicator(running, "discovery")
            }

            if (running || progress.phase == "done" || progress.phase == "stopped") {
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (progress.phase) {
                                        "sources" -> "Querying sources…"
                                        "validating" -> "Validating candidates…"
                                        "stopped" -> "Stopped"
                                        else -> "Finished"
                                    },
                                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
                                )
                                if (progress.phase == "validating") Text("${progress.concurrency} workers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val fraction = when {
                                progress.phase == "validating" && progress.candidates > 0 -> progress.validated.toFloat() / progress.candidates
                                progress.phase == "sources" && progress.sourcesTotal > 0 -> progress.sourcesDone.toFloat() / progress.sourcesTotal
                                !running -> 1f
                                else -> null
                            }
                            GhiProgressBar(fraction)
                            GhiStatRow(
                                listOf(
                                    "Sources" to "${progress.sourcesDone}/${progress.sourcesTotal}",
                                    "Candidates" to "${progress.candidates}",
                                    "Checked" to "${progress.validated}",
                                    "Live" to "${progress.live}"
                                )
                            )
                            if (progress.failedSources.isNotEmpty()) {
                                Text(
                                    "Sources with errors: " + progress.failedSources.joinToString(", "),
                                    color = GhiSignalAmber,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Discovery results", style = MaterialTheme.typography.titleMedium)
                        Text("${discoveredResults.size} found • ${liveResults.size} live • ${elapsedMs}ms", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(discoveredResults.joinToString("\n") { it.domain })) }, enabled = discoveredResults.isNotEmpty()) { Icon(Icons.Filled.ContentCopy, "Copy all found") }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(liveResults.joinToString("\n") { it.domain })) }, enabled = liveResults.isNotEmpty()) { Icon(Icons.Filled.Verified, "Copy live") }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhiChip("All found", showAllDiscovered, { showAllDiscovered = true })
                    GhiChip("Live only", !showAllDiscovered, { showAllDiscovered = false })
                    GhiChip("Fastest first", fastestFirst, { fastestFirst = !fastestFirst }, icon = Icons.Filled.Speed)
                }
            }
            item { GhiSearchField(filter, { filter = it }, "Filter results") }
            error?.let { msg -> item { GhiCard { Text(msg, color = GhiSignalAmber, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) } } }
            if (visible.isEmpty() && !running && discoveredResults.isNotEmpty()) {
                item { GhiEmptyState("No matches", "Nothing matches the current filter.") }
            }
            items(visible, key = { it.domain }) { result ->
                DomainResultCard(result, onAnalyze, clipboard)
            }
        }
    }
}

@Composable
private fun DomainResultCard(result: DomainPing, onAnalyze: (String) -> String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    val style = LocalGhiStyle.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember(result.domain) { mutableStateOf(false) }
    var details by remember(result.domain) { mutableStateOf<JSONObject?>(null) }
    var failed by remember(result.domain) { mutableStateOf(false) }
    // The analysis is a blocking network call: it must never run on the main thread.
    LaunchedEffect(expanded) {
        if (expanded && details == null) {
            val parsed = withContext(Dispatchers.IO) { runCatching { JSONObject(onAnalyze(result.domain)) }.getOrNull() }
            if (parsed == null) failed = true else { details = parsed; failed = false }
        }
    }
    val enter = if (style.animations) fadeIn() + slideInVertically(initialOffsetY = { 18 }) else EnterTransition.None
    AnimatedVisibility(true, enter = enter) {
        GhiCard {
            Column(Modifier.fillMaxWidth().padding((12 * style.density.scale).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.domain, style = MaterialTheme.typography.titleMedium)
                        Text(if (result.live) "LIVE • HTTP ${result.status}" else "DISCOVERED • not validated live", color = if (result.live) GhiSignalGreen else dim, style = MaterialTheme.typography.labelSmall)
                    }
                    if (result.live) Text("${result.latencyMs}ms", color = latencyColor(result.latencyMs), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(result.domain)) }) { Icon(Icons.Filled.ContentCopy, "Copy hostname") }
                    IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Details") }
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Host details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val obj = details
                        when {
                            obj != null -> {
                                DetailLine("Address", obj.optString("addresses", "—"))
                                DetailLine("HTTPS", statusText(obj.optInt("https_status", -1)))
                                DetailLine("HTTP", statusText(obj.optInt("http_status", -1)))
                                DetailLine("TLS", obj.optString("tls_version", "—"))
                                DetailLine("Server", obj.optString("https_server", obj.optString("http_server", "—")))
                                DetailLine("CDN", obj.optString("cdn", "—"))
                                DetailLine("Response time", "${obj.optLong("elapsed_ms", result.latencyMs)} ms")
                                if (!style.compactResults) {
                                    Text("Technical / raw", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                    Text(obj.toString(2), color = dim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            failed -> Text("Could not load host details.", color = GhiSignalAmber, style = MaterialTheme.typography.bodySmall)
                            else -> Text("Loading host details…", color = dim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.width(105.dp), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall); Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall) } }
private fun statusText(code: Int): String = if (code > 0) code.toString() else "—"
private fun latencyColor(ms: Long) = when { ms <= 80 -> GhiSignalGreen; ms <= 200 -> GhiSignalAmber; else -> GhiSignalRed }
