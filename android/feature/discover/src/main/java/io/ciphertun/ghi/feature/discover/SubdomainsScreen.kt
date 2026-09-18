package io.ciphertun.ghi.feature.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SubdomainsScreen(onDiscover: (String, Int) -> String) {
    var domain by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    GhiScreenScaffold("Subdomains") { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { GhiHero("Subdomain intelligence", "Fan out across certificate, passive DNS, URL and historical sources. Results are observed names, not guessed names.") }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(domain, { domain = it }, label = { Text("Root domain") }, placeholder = { Text("example.com") }, leadingIcon = { Icon(Icons.Filled.Dns, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (running) Button(onClick = { running = false }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("STOP") }
                        else Button(enabled = domain.isNotBlank(), onClick = {
                            running = true; results = emptyList(); counts = emptyMap(); error = null
                            scope.launch {
                                val raw = withContext(Dispatchers.IO) { onDiscover(domain.trim(), 500) }
                                runCatching {
                                    val obj = JSONObject(raw)
                                    results = buildList { val arr = obj.optJSONArray("subdomains"); if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i)) }
                                    counts = buildMap { val o = obj.optJSONObject("source_counts"); if (o != null) o.keys().forEach { k -> put(k, o.optInt(k)) } }
                                    error = obj.optString("error").takeIf { it.isNotBlank() }
                                }.onFailure { error = it.message }
                                running = false
                            }
                        }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("DISCOVER UP TO 500") }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${results.size} unique subdomains", style = MaterialTheme.typography.titleMedium); Text("${counts.size} sources returned evidence", color = GhiSlate300, style = MaterialTheme.typography.labelSmall) }
                    if (results.isNotEmpty()) IconButton(onClick = { clipboard.setText(AnnotatedString(results.joinToString("\n"))) }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                }
            }
            error?.let { item { GhiCard { Text(it, color = GhiSignalAmber, modifier = Modifier.padding(14.dp)) } } }
            if (counts.isNotEmpty()) item {
                GhiCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Source yield", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); counts.entries.sortedByDescending { it.value }.take(12).forEach { (source,count) -> Text("$source  •  $count", color = GhiSlate300, style = MaterialTheme.typography.bodySmall) } } }
            }
            items(results, key = { it }) { host ->
                GhiCard { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(host, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); IconButton(onClick = { clipboard.setText(AnnotatedString(host)) }) { Icon(Icons.Filled.ContentCopy, "Copy") } } }
            }
        }
    }
}
