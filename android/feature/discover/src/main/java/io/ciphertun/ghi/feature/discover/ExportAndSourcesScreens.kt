package io.ciphertun.ghi.feature.discover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*

private data class SourceItem(val id: String, val name: String, val detail: String)
private val catalog = listOf(
    SourceItem("urlscan", "URLScan", "Indexed URLs and host observations"),
    SourceItem("crt.sh", "crt.sh", "Certificate Transparency"),
    SourceItem("crt.name", "CRT.name", "Independent CT index"),
    SourceItem("ctlogs.dev", "CTLogs.dev", "Certificate log dataset"),
    SourceItem("certspotter", "CertSpotter", "Certificate issuance records"),
    SourceItem("rapiddns", "RapidDNS", "Passive DNS dataset"),
    SourceItem("anubis", "Anubis", "Passive subdomain index"),
    SourceItem("subdomain.center", "Subdomain Center", "Public subdomain intelligence"),
    SourceItem("subdomain.app", "Subdomain API", "Public historical subdomain index"),
    SourceItem("sonar", "Rapid7 Sonar", "Public Project Sonar-derived host data"),
    SourceItem("riddler", "Riddler", "Public indexed host search"),
    SourceItem("jldc", "JLDC / Anubis", "Public passive subdomain index"),
    SourceItem("sublist3r", "Sublist3r API", "Public passive lookup endpoint"),
    SourceItem("hackertarget", "HackerTarget", "Host search / DNS intelligence"),
    SourceItem("wayback", "Wayback", "Historical web URLs"),
    SourceItem("threatminer", "ThreatMiner", "Passive DNS / threat intelligence"),
    SourceItem("commoncrawl", "Common Crawl", "Historical web crawl index"),
    SourceItem("otx", "AlienVault OTX", "Passive DNS / threat intelligence"),
    SourceItem("country", "RIPEstat Country / ASN", "Global country and routed-ASN discovery")
)

@Composable
fun DiscoverySourcesScreen(enabledSources: Set<String>, onSave: (Set<String>) -> Unit) {
    var selected by remember(enabledSources) { mutableStateOf(enabledSources.toMutableSet()) }
    var saved by remember { mutableStateOf(false) }
    GhiScreenScaffold("Discovery Sources") { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { GhiHero("Discovery sources", "Parallel public/passive sources. Enable only the sources you want the discovery engine to query.") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} enabled", color = GhiSlate300, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = catalog.map { it.id }.toMutableSet() }) { Text("Select all") }
                    TextButton(onClick = { selected.clear() }) { Text("Clear") }
                }
            }
            items(catalog, key = { it.id }) { source ->
                GhiCard {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(source.name, style = MaterialTheme.typography.titleMedium); Text(source.detail, color = GhiSlate300, style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = source.id in selected, onCheckedChange = { checked -> selected = selected.toMutableSet().apply { if (checked) add(source.id) else remove(source.id) } })
                    }
                }
            }
            item {
                Button(onClick = { onSave(selected); saved = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("SAVE SOURCES") }
            }
            if (saved) item { Text("Source selection saved for the next discovery run.", color = GhiSignalGreen, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun ExportScreen(results: List<DomainPing>, exportText: (String) -> String) {
    var format by remember { mutableStateOf("TXT") }
    var pendingFormat by remember { mutableStateOf("txt") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var exportError by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            exportError = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(exportText(pendingFormat).toByteArray(Charsets.UTF_8)) }
                    ?: error("Unable to open export destination")
                null
            }.getOrElse { it.message ?: "Export failed" }
        }
    }
    GhiScreenScaffold("Export") { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { GhiHero("Export results", "Export the current discovery results. Nothing is archived as history.") }
            item {
                GhiCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${results.size} validated hosts", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TXT", "CSV", "JSON").forEach { f -> if (format == f) Button(onClick = { format = f }) { Text(f) } else OutlinedButton(onClick = { format = f }) { Text(f) } }
                    }
                } }
            }
            item {
                Button(enabled = results.isNotEmpty(), onClick = {
                    pendingFormat = format.lowercase()
                    val extension = pendingFormat
                    createDocument.launch("ghi-discovery-results.$extension")
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("EXPORT $format") }
            }
            exportError?.let { msg -> item { Text(msg, color = GhiSignalRed, style = MaterialTheme.typography.bodySmall) } }
            if (results.isEmpty()) item { Text("Run a discovery first to export validated results.", color = GhiSlate300) }
        }
    }
}
