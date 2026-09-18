package io.ciphertun.ghi.feature.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.GhiInk900
import io.ciphertun.ghi.core.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun TlsAnalyzerScreen(onAnalyze: (String) -> String) {
    ToolJsonScreen("TLS / SSL Analyzer", "Extract the live certificate, TLS version, cipher and ALPN from a host.",
        Icons.Filled.Security, "example.com", "ANALYZE TLS", "tls", onAnalyze)
}

@Composable
fun DnsInspectorScreen(onInspect: (String) -> String) {
    ToolJsonScreen("DNS Inspector", "Inspect A, AAAA, CNAME, MX, NS, TXT, SRV and PTR records.",
        Icons.Filled.Dns, "example.com", "LOOK UP DNS", "dns", onInspect)
}

@Composable
fun CertificateSearchScreen(onSearch: (String) -> String) {
    var domain by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    GhiScreenScaffold("Certificate Search") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                GhiHero("Certificate Transparency",
                    "Search public certificate records and inspect SANs, issuer, validity and fingerprint evidence.")
            }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(domain, { domain = it }, label = { Text("Domain") },
                            placeholder = { Text("example.com") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Button(
                            enabled = domain.isNotBlank() && !running,
                            onClick = {
                                running = true
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val value = onSearch(domain.trim())
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        raw = value
                                        running = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "SEARCHING…" else "SEARCH CERTIFICATES")
                        }
                        GhiBusyIndicator(running, "certificate")
                    }
                }
            }
            if (raw.isNotBlank()) {
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text("RESULT", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) {
                            Icon(Icons.Filled.ContentCopy, "Copy")
                        }
                    }
                }
                certificateRows(raw).forEach { row ->
                    item {
                        GhiCard {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(row.title, style = MaterialTheme.typography.titleSmall)
                                Text(row.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    Surface(color = GhiInk900.copy(alpha = .86f), shape = MaterialTheme.shapes.medium) {
                        Text(raw, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolJsonScreen(
    title: String,
    hero: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    action: String,
    hint: String,
    onRun: (String) -> String
) {
    var input by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    GhiScreenScaffold(title) { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { GhiHero(hero, "Real network operation • bounded timeout • source-aware result") }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(input, { input = it }, label = { Text("Host / domain / IP") },
                            placeholder = { Text(placeholder) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Button(
                            enabled = input.isNotBlank() && !running,
                            onClick = {
                                running = true
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val value = onRun(input.trim())
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        raw = value
                                        running = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(icon, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "WORKING…" else action)
                        }
                        GhiBusyIndicator(running, hint)
                    }
                }
            }
            if (raw.isNotBlank()) {
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text("OUTPUT", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) {
                            Icon(Icons.Filled.ContentCopy, "Copy")
                        }
                    }
                }
                item {
                    Surface(color = GhiInk900.copy(alpha = .86f), shape = MaterialTheme.shapes.medium) {
                        Text(prettyJson(raw), modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class CertificateRow(val title: String, val detail: String)

private fun certificateRows(raw: String): List<CertificateRow> =
    runCatching {
        val obj = JSONObject(raw)
        val rows = obj.optJSONArray("rows") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val match = row.optString("match", "certificate")
                val issuer = row.optString("issuer", "unknown issuer")
                val from = row.optString("not_before", "?")
                val to = row.optString("not_after", "?")
                add(CertificateRow(match, "$issuer • $from → $to • SANs ${row.optInt("san_count", 0)}"))
            }
        }
    }.getOrDefault(emptyList())

private fun prettyJson(raw: String): String =
    runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }

