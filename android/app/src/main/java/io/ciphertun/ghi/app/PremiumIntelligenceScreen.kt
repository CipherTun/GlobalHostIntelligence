package io.ciphertun.ghi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PremiumIntelligenceScreen(mode: String, session: GhiSession, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<List<String>>(emptyList()) }
    var raw by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val title = when (mode) {
        "hosts" -> "🛰 Hosts"
        "domains" -> "🌍 Domains"
        "ips" -> "📍 IP Addresses"
        "asns" -> "🏢 ASNs / Networks"
        "certificates" -> "🔐 Certificates"
        "search" -> "🔎 Intelligence Search"
        "graph" -> "🕸 Relationship Graph"
        "crawler" -> "🚀 Carrier Discovery"
        "history" -> "🕘 Discovery History"
        "bookmarks" -> "🔖 Bookmarks"
        else -> mode
    }

    fun run(block: suspend () -> String) {
        running = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching { block() }.getOrElse {
                JSONObject().put("error", it.message ?: "Library operation failed").toString()
            }
            val rows = runCatching {
                val obj = JSONObject(result)
                val results = obj.optJSONArray("results") ?: obj.optJSONArray("domains")
                if (results != null) {
                    (0 until results.length()).mapNotNull { index ->
                        val value = results.opt(index)
                        when (value) {
                            is String -> value.takeIf { it.isNotBlank() }
                            is JSONObject -> value.optString("domain").takeIf { it.isNotBlank() }
                            else -> null
                        }
                    }
                } else emptyList()
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                raw = result
                output = rows
                running = false
            }
        }
    }

    GhiScreenScaffold(title, onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(if (mode == "crawler") "Country code or ASxxx" else "Domain, host, IP or ASN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = query.isNotBlank() && !running,
                    onClick = {
                        when (mode) {
                            "crawler" -> run { GhiMobileBridge.discoverCarrier(query.trim(), session.discoveryLimit()) }
                            "ips" -> run { GhiMobileBridge.resolveDomain(query.trim()) }
                            "asns", "graph" -> run { GhiMobileBridge.analyzeHost(query.trim()) }
                            "certificates" -> run { GhiMobileBridge.discoverCandidates(query.trim(), "certspotter", session.discoveryLimit()) }
                            "history" -> { output = session.search(query.trim()); raw = ""; running = false }
                            "bookmarks" -> { output = session.bookmarks.value.filter { it.contains(query.trim(), true) }; raw = ""; running = false }
                            else -> run { GhiMobileBridge.discoverCandidates(query.trim(), "all", session.discoveryLimit()) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "RUNNING…" else "RUN")
                }
                if (output.isNotEmpty()) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output.joinToString("\n"))) }) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Spacer(Modifier.width(5.dp))
                        Text("COPY ALL")
                    }
                }
            }
            if (output.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(output, key = { it }) { value ->
                        ListItem(
                            headlineContent = { Text(value) },
                            trailingContent = {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                                    Icon(Icons.Filled.ContentCopy, "Copy")
                                }
                            }
                        )
                    }
                }
            } else if (raw.isNotBlank()) {
                Text(raw, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
