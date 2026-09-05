package io.ciphertun.ghi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge
import io.ciphertun.ghi.core.designsystem.GhiAccentBlue
import io.ciphertun.ghi.core.designsystem.GhiInk800
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

@Composable
fun GhiToolScreen(mode: String, session: GhiSession, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val domains by session.domains.collectAsState()
    val history by session.history.collectAsState()
    val bookmarks by session.bookmarks.collectAsState()
    val title = when (mode) {
        "hosts" -> "Hosts"
        "domains" -> "Domains"
        "ips" -> "IP Addresses"
        "asns" -> "ASNs / Networks"
        "certificates" -> "Certificates"
        "countries" -> "Countries"
        "search" -> "Search"
        "graph" -> "Connection Graph"
        "crawler" -> "Carrier Discovery"
        "history" -> "History"
        "bookmarks" -> "Bookmarks"
        else -> mode.replaceFirstChar { it.uppercase() }
    }
    val rows = when (mode) {
        "bookmarks" -> bookmarks
        "history" -> history
        else -> session.search(input)
    }

    GhiScreenScaffold(title, onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (mode) {
                "hosts", "domains", "history", "bookmarks", "search" -> {
                    OutlinedTextField(input, { input = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (rows.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${rows.size} results", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(rows.joinToString("\n"))) }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(rows, key = { it }) { value ->
                            ListItem(
                                headlineContent = { SelectionContainer { Text(value) } },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                                        if (mode == "bookmarks") IconButton(onClick = { session.toggleBookmark(value) }) { Icon(Icons.Filled.Delete, "Remove") }
                                    }
                                }
                            )
                        }
                    }
                }
                "countries" -> {
                    val countries = Locale.getISOCountries().map { code -> code to Locale("", code).displayCountry }.sortedBy { it.second }
                    OutlinedTextField(input, { input = it }, label = { Text("Search countries") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(countries.filter { input.isBlank() || it.second.contains(input, true) }.take(120), key = { it.first }) { (code, name) ->
                            ListItem(
                                headlineContent = { Text("${flag(code)}  $name") },
                                trailingContent = { IconButton(onClick = { session.startDiscovery(code.lowercase()); onBack() }) { Icon(Icons.Filled.PlayArrow, "Discover") } }
                            )
                        }
                    }
                }
                "ips" -> {
                    OutlinedTextField(input, { input = it }, label = { Text("Domain or IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = input.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch(Dispatchers.IO) {
                            val value = if (input.trim().matches(Regex("[0-9a-fA-F:.]+"))) session.resolveIp(input.trim()) else session.resolve(input.trim())
                            withContext(Dispatchers.Main) { output = value; busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "LOOKING UP…" else "LOOK UP") }
                    OutputCard(output, clipboard)
                }
                "asns" -> {
                    OutlinedTextField(input, { input = it }, label = { Text("ASxxx") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = input.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch(Dispatchers.IO) {
                            val value = GhiMobileBridge.discoverCarrier(input.trim(), session.discoveryLimit())
                            withContext(Dispatchers.Main) { output = value; busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "QUERYING…" else "QUERY CURRENT ASN") }
                    OutputCard(output, clipboard)
                }
                "certificates", "graph" -> {
                    OutlinedTextField(input, { input = it }, label = { Text("Domain or host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = input.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch(Dispatchers.IO) {
                            val value = session.checkHost(input.trim(), "GET", false, false)
                            withContext(Dispatchers.Main) { output = value; busy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "ANALYZING…" else "ANALYZE") }
                    OutputCard(output, clipboard)
                }
                "crawler" -> {
                    OutlinedTextField(input, { input = it }, label = { Text("Country code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = input.length == 2 && !busy, onClick = { session.startDiscovery(input.trim().lowercase()); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("START LIVE CRAWL") }
                }
            }
        }
    }
}

@Composable
private fun OutputCard(raw: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    if (raw.isNotBlank()) {
        Card(colors = CardDefaults.cardColors(containerColor = GhiInk800), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Text(raw, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
            }
        }
    }
}

private fun flag(code: String): String = code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
