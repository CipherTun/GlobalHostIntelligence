package io.ciphertun.ghi.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun GhiInvestigationScreen(investigate: (String) -> String) {
    var target by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    GhiScreenScaffold("Investigation") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "ATTACK-SURFACE INVESTIGATION",
                    "Runs GHI's real discovery, DNS, HTTP, TLS, certificate and security-analysis engines together."
                )
            }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Domain or host") },
                            placeholder = { Text("example.com") }
                        )
                        Button(
                            onClick = {
                                running = true
                                scope.launch {
                                    val value = withContext(kotlinx.coroutines.Dispatchers.IO) { investigate(target.trim()) }
                                    result = value
                                    running = false
                                }
                            },
                            enabled = target.isNotBlank() && !running,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.Security, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "ANALYZING…" else "START INVESTIGATION")
                        }
                        GhiBusyIndicator(running, "agent investigation")
                    }
                }
            }
            if (result.isNotBlank()) item { HumanReadableResult("INVESTIGATION", result) }
        }
    }
}

@Composable
fun GhiAgentScreen(ask: (String) -> String) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    GhiScreenScaffold("GHI Agent") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "GHI AGENT",
                    "Ask GHI in plain language. Results are presented as readable intelligence cards, like Discovery."
                )
            }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("Ask GHI") },
                            placeholder = { Text("Investigate example.com") }
                        )
                        Text(
                            "Examples: investigate example.com • DNS example.com • TLS example.com • surface example.com • search ProjectDiscovery httpx",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhiSlate300
                        )
                        Button(
                            onClick = {
                                running = true
                                scope.launch {
                                    val value = withContext(kotlinx.coroutines.Dispatchers.IO) { ask(query.trim()) }
                                    result = value
                                    running = false
                                }
                            },
                            enabled = query.isNotBlank() && !running,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "WORKING…" else "RUN")
                        }
                        GhiBusyIndicator(running, "agent intelligence")
                    }
                }
            }
            if (result.isNotBlank()) item { HumanReadableResult("AGENT", result) }
        }
    }
}

@Composable
private fun HumanReadableResult(title: String, raw: String) {
    val clipboard = LocalClipboardManager.current
    val json = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }

    if (json == null) {
        SimpleCard(title, raw, Icons.Filled.ErrorOutline)
        return
    }

    val ok = json.optBoolean("ok", true)
    val agent = json.optString("agent", "")
    val target = json.optString("target", "")
    val query = json.optString("query", "")

    GhiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    null,
                    tint = if (ok) GhiSignalGreen else GhiSignalRed
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (agent.isBlank()) title else humanize(agent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (ok) "Completed successfully" else "Request returned an error",
                        color = if (ok) GhiSignalGreen else GhiSignalRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy raw result")
                }
            }
            if (target.isNotBlank()) DetailLine("Target", target)
            if (query.isNotBlank()) DetailLine("Search", query)
            if (json.optString("error").isNotBlank()) ErrorBox(json.optString("error"))
        }
    }

    val nested = json.optJSONObject("result")
    if (nested != null) {
        when {
            agent.equals("discovery", true) -> DiscoveryResult(nested, clipboard)
            agent.equals("internet-search", true) -> WebSearchResult(nested, clipboard)
            else -> IntelligenceResult(nested, clipboard)
        }
    }

    TechnicalDataCard(raw)
}

@Composable
private fun DiscoveryResult(
    result: JSONObject,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val domain = result.optString("domain", result.optString("query", "—"))
    val hosts = result.optJSONArray("subdomains") ?: JSONArray()
    val counts = result.optJSONObject("source_counts")
    val errors = result.optJSONObject("errors")
    val sources = result.optJSONArray("sources")

    GhiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader("DISCOVERY RESULTS", Icons.Filled.Search)
            Text(domain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${hosts.length()} discovered • ${counts?.length() ?: 0} source reports",
                color = GhiSlate300,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (counts != null && counts.length() > 0) {
        JsonObjectCard("SOURCE SUMMARY", counts, Icons.Filled.Language)
    }

    if (errors != null && errors.length() > 0) {
        JsonObjectCard("SOURCE ERRORS", errors, Icons.Filled.Warning, true)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("DISCOVERED HOSTNAMES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${hosts.length()} hostnames returned", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(
            onClick = {
                clipboard.setText(
                    AnnotatedString(
                        (0 until hosts.length()).joinToString("\n") { hosts.optString(it) }
                    )
                )
            },
            enabled = hosts.length() > 0
        ) {
            Icon(Icons.Filled.ContentCopy, "Copy all hostnames")
        }
    }

    if (hosts.length() == 0) {
        EmptyCard("No subdomains were returned.")
    } else {
        for (i in 0 until hosts.length()) {
            val host = hosts.optString(i)
            if (host.isNotBlank()) HostnameCard(host, clipboard)
        }
    }

    if (sources != null && sources.length() > 0) {
        val names = (0 until sources.length()).map { sources.optString(it) }.filter { it.isNotBlank() }
        GhiCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("QUERY SOURCES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                names.forEach { Text("• $it", color = GhiSlate300, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun WebSearchResult(
    result: JSONObject,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val results = result.optJSONArray("results") ?: JSONArray()

    GhiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader("WEB SEARCH", Icons.Filled.Language)
            Text("${results.length()} results returned", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
        }
    }

    for (i in 0 until results.length()) {
        val item = results.optJSONObject(i) ?: continue
        GhiCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.optString("title", "Untitled result"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val url = item.optString("url", "")
                if (url.isNotBlank()) Text(url, color = GhiAccentBlue, style = MaterialTheme.typography.bodySmall)
                val snippet = item.optString("snippet", "")
                if (snippet.isNotBlank()) Text(snippet, color = GhiSlate300, style = MaterialTheme.typography.bodySmall)
                if (url.isNotBlank()) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                        Icon(Icons.Filled.ContentCopy, "Copy URL")
                    }
                }
            }
        }
    }

    if (results.length() == 0) EmptyCard("No web results were returned.")
}

@Composable
private fun IntelligenceResult(
    result: JSONObject,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val keys = result.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (key == "ok" || key == "error") continue
        when (val value = result.opt(key)) {
            is JSONObject -> JsonObjectCard(humanize(key), value, Icons.Filled.Security)
            is JSONArray -> JsonArrayCard(humanize(key), value, clipboard)
            else -> JsonDetailCard(humanize(key), valueText(value), Icons.Filled.Speed)
        }
    }
    if (result.optString("error").isNotBlank()) ErrorBox(result.optString("error"))
}

@Composable
private fun HostnameCard(
    hostname: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    var expanded by remember(hostname) { mutableStateOf(false) }

    AnimatedVisibility(true, enter = fadeIn() + slideInVertically(initialOffsetY = { 12 })) {
        GhiCard {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hostname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("DISCOVERED HOST", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(hostname)) }) {
                        Icon(Icons.Filled.ContentCopy, "Copy hostname")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Details")
                    }
                }
                AnimatedVisibility(expanded) {
                    Text(
                        "Hostname: $hostname",
                        Modifier.padding(top = 8.dp),
                        color = GhiSlate300,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonObjectCard(
    title: String,
    value: JSONObject,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    errorTone: Boolean = false
) {
    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultHeader(title, icon, errorTone)
            HorizontalDivider()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = value.opt(key)
                if (item is JSONObject || item is JSONArray) {
                    Text(humanize(key), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(compactJson(item), color = GhiSlate300, style = MaterialTheme.typography.bodySmall)
                } else {
                    DetailLine(humanize(key), valueText(item))
                }
            }
        }
    }
}

@Composable
private fun JsonArrayCard(
    title: String,
    value: JSONArray,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${value.length()} items", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(compactJson(value))) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy data")
                }
            }
            HorizontalDivider()
            val limit = minOf(value.length(), 20)
            for (i in 0 until limit) {
                Text("• ${valueText(value.opt(i))}", color = GhiSlate300, style = MaterialTheme.typography.bodySmall)
            }
            if (value.length() > limit) {
                Text(
                    "Showing first $limit of ${value.length()} items.",
                    color = GhiSlate300,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun JsonDetailCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GhiCard {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = GhiAccentCyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
                Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ResultHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    errorTone: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (errorTone) GhiSignalAmber else GhiAccentCyan)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SimpleCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GhiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultHeader(title, icon)
            HorizontalDivider()
            Text(value, color = GhiSlate300, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    GhiCard {
        Text(message, Modifier.padding(16.dp), color = GhiSlate300, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBox(message: String) {
    if (message.isBlank()) return
    Surface(
        color = GhiInk800,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = GhiSignalAmber)
            Spacer(Modifier.width(8.dp))
            Text(message, color = GhiSignalAmber, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TechnicalDataCard(raw: String) {
    var expanded by remember(raw) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TECHNICAL DATA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Raw JSON • for export/debugging", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy raw JSON")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Show raw JSON")
                }
            }
            AnimatedVisibility(expanded) {
                Text(
                    remember(raw) { runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw) },
                    color = GhiSlate300,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(112.dp), color = GhiSlate500, style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun valueText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "—"
    is Boolean -> if (value) "Yes" else "No"
    else -> value.toString()
}

private fun compactJson(value: Any): String = value.toString()

private fun humanize(value: String): String =
    value.replace('_', ' ').replace('-', ' ').trim()
        .split(Regex("\\s+"))
        .joinToString(" ") { word ->
            if (word.isBlank()) word else word.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
        }
