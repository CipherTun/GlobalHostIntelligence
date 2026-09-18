package io.ciphertun.ghi.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.GhiAccentBlue
import io.ciphertun.ghi.core.designsystem.GhiAccentCyan
import io.ciphertun.ghi.core.designsystem.GhiInk800
import io.ciphertun.ghi.core.designsystem.GhiInk900
import io.ciphertun.ghi.core.designsystem.GhiInk950
import io.ciphertun.ghi.core.designsystem.GhiSignalAmber
import io.ciphertun.ghi.core.designsystem.GhiSignalGreen
import io.ciphertun.ghi.core.designsystem.GhiSignalRed
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold
import io.ciphertun.ghi.core.ui.components.GhiCard
import io.ciphertun.ghi.core.ui.components.GhiHero
import io.ciphertun.ghi.core.designsystem.GhiSlate300
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val responseMethods = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "OPTIONS")
private val dnsTransports = listOf("UDP", "TCP", "DoT", "DoH")

private data class CheckItem(
    val mode: String,
    val json: JSONObject,
    val target: String,
    val arrived: Boolean = true
)

@Composable
fun ResponseScreen(
    onAnalyze: (String) -> String,
    onCheck: (
        mode: String,
        targets: String,
        proxy: String,
        method: String,
        path: String,
        headers: String,
        body: String,
        followRedirects: Boolean,
        allowInsecure: Boolean,
        timeoutSeconds: Int,
        payloadMode: Boolean,
        dnsTransport: String,
        resolver: String,
        authoritative: String
    ) -> String = { _, target, _, _, _, _, _, _, _, _, _, _, _, _ ->
        onAnalyze(target.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty())
    }
) {
    var mode by remember { mutableStateOf("HTTP") }
    var targets by remember { mutableStateOf("") }
    var proxyManual by remember { mutableStateOf("") }
    var proxyDirect by remember { mutableStateOf(true) }
    var method by remember { mutableStateOf("GET") }
    var path by remember { mutableStateOf("/") }
    var headers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var followRedirects by remember { mutableStateOf(true) }
    var allowInsecure by remember { mutableStateOf(false) }
    var payloadMode by remember { mutableStateOf(false) }
    var timeout by remember { mutableStateOf(10f) }
    var dnsTransport by remember { mutableStateOf("UDP") }
    var resolver by remember { mutableStateOf("") }
    var authoritative by remember { mutableStateOf("") }
    var expandedAdvanced by remember { mutableStateOf(true) }

    val results = remember { mutableStateListOf<CheckItem>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var filter by remember { mutableStateOf("Not detected") }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun clearResults() {
        results.clear()
        filter = "Not detected"
        copied = false
    }

    fun startCheck() {
        if (running || targets.isBlank()) return
        clearResults()
        running = true
        job = scope.launch {
            try {
                val list = targets.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(100)
                if (mode == "HTTP") {
                    for (target in list) {
                        val raw = withContext(Dispatchers.IO) {
                            onCheck(
                                "HTTP", target, if (proxyDirect) "" else proxyManual,
                                method, path, headers, body, followRedirects,
                                allowInsecure, timeout.toInt(), payloadMode,
                                dnsTransport, resolver, authoritative
                            )
                        }
                        appendHttpResults(raw, target, results)
                        delay(28)
                        if (!running) break
                    }
                } else {
                    val raw = withContext(Dispatchers.IO) {
                        onCheck(
                            mode, targets, if (proxyDirect) "" else proxyManual,
                            method, path, headers, body, followRedirects,
                            allowInsecure, timeout.toInt(), payloadMode,
                            dnsTransport, resolver, authoritative
                        )
                    }
                    appendGenericResults(raw, mode, targets, results)
                }
            } finally {
                running = false
            }
        }
    }

    fun stopCheck() {
        running = false
        job?.cancel()
        job = null
    }

    GhiScreenScaffold("Response checker") { modifier ->
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RequestCard(
                    mode = mode,
                    onMode = { mode = it; clearResults() },
                    targets = targets,
                    onTargets = { targets = it },
                    proxyDirect = proxyDirect,
                    onProxyDirect = { proxyDirect = it },
                    proxy = proxyManual,
                    onProxy = { proxyManual = it },
                    method = method,
                    onMethod = { method = it },
                    path = path,
                    onPath = { path = it },
                    headers = headers,
                    onHeaders = { headers = it },
                    body = body,
                    onBody = { body = it },
                    followRedirects = followRedirects,
                    onFollow = { followRedirects = it },
                    allowInsecure = allowInsecure,
                    onInsecure = { allowInsecure = it },
                    payloadMode = payloadMode,
                    onPayloadMode = { payloadMode = it },
                    expandedAdvanced = expandedAdvanced,
                    onExpanded = { expandedAdvanced = !expandedAdvanced },
                    dnsTransport = dnsTransport,
                    onDnsTransport = { dnsTransport = it },
                    resolver = resolver,
                    onResolver = { resolver = it },
                    authoritative = authoritative,
                    onAuthoritative = { authoritative = it }
                )

                if (mode == "HTTP") {
                    SliderRow("Timeout", timeout, 2f, 30f, "${timeout.toInt()}s") { timeout = it }
                }

                if (results.isNotEmpty()) {
                    ResultToolbar(
                        total = results.size,
                        results = results,
                        filter = filter,
                        onFilter = { filter = it },
                        onClear = ::clearResults,
                        onCopyAll = {
                            val text = results.joinToString("\n\n") { it.json.toString(2) }
                            clipboard.setText(AnnotatedString(text))
                            copied = true
                        }
                    )
                    if (copied) {
                        Text(
                            "Copied all results",
                            color = GhiSignalGreen,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    val visibleResults = results.filter {
                        (filter == "Not detected" && (it.mode != "HTTP" || it.json.optString("cdn").isBlank())) ||
                            (filter != "Not detected" && it.json.optString("cdn").equals(filter, true))
                    }
                    visibleResults.forEachIndexed { index, item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                        ) {
                            ResponseResultCard(item, index, clipboard)
                        }
                    }
                    if (mode == "DNS") DnsVerdict(results)
                } else if (running) {
                    LoadingResultCard(targets.lines().firstOrNull { it.isNotBlank() } ?: "Checking…")
                } else if (mode == "DNS") {
                    Text(
                        "Results will appear here after the DNS path is checked.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.height(92.dp))
            }

            FloatingActionButton(
                onClick = { if (running) stopCheck() else startCheck() },
                containerColor = GhiAccentBlue,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 12.dp)
            ) {
                Icon(if (running) Icons.Filled.Stop else Icons.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Stop" else "Check", modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}

private fun appendHttpResults(raw: String, target: String, out: MutableList<CheckItem>) {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
    val arr = obj.optJSONArray("results") ?: JSONArray()
    if (arr.length() == 0) {
        out.add(CheckItem("HTTP", obj.put("target", target), target))
        return
    }
    for (i in 0 until arr.length()) {
        val r = arr.optJSONObject(i) ?: continue
        out.add(CheckItem("HTTP", r, r.optString("target", target)))
    }
}

private fun appendGenericResults(raw: String, mode: String, target: String, out: MutableList<CheckItem>) {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
    if (mode == "DNS") {
        // Keep the actual query result plus a transparent-interception control query,
        // matching the diagnostic workflow shown in HTTP Custom.
        out.add(CheckItem(mode, obj, target))
        return
    }
    val arr = obj.optJSONArray("results")
    if (arr != null && arr.length() > 0) {
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(CheckItem(mode, it, it.optString("target", target))) }
    } else out.add(CheckItem(mode, obj, target))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestCard(
    mode: String,
    onMode: (String) -> Unit,
    targets: String,
    onTargets: (String) -> Unit,
    proxyDirect: Boolean,
    onProxyDirect: (Boolean) -> Unit,
    proxy: String,
    onProxy: (String) -> Unit,
    method: String,
    onMethod: (String) -> Unit,
    path: String,
    onPath: (String) -> Unit,
    headers: String,
    onHeaders: (String) -> Unit,
    body: String,
    onBody: (String) -> Unit,
    followRedirects: Boolean,
    onFollow: (Boolean) -> Unit,
    allowInsecure: Boolean,
    onInsecure: (Boolean) -> Unit,
    payloadMode: Boolean,
    onPayloadMode: (Boolean) -> Unit,
    expandedAdvanced: Boolean,
    onExpanded: () -> Unit,
    dnsTransport: String,
    onDnsTransport: (String) -> Unit,
    resolver: String,
    onResolver: (String) -> Unit,
    authoritative: String,
    onAuthoritative: (String) -> Unit
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = GhiInk950),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Public, null, tint = GhiAccentCyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("HTTP", mode == "HTTP") { onMode("HTTP") }
                ModeButton("UDP", mode == "UDP") { onMode("UDP") }
                ModeButton("DNS", mode == "DNS") { onMode("DNS") }
            }

            when (mode) {
                "HTTP" -> {
                    OutlinedTextField(
                        value = targets,
                        onValueChange = onTargets,
                        label = { Text("Target URLs") },
                        supportingText = { Text("One URL per line. Scheme defaults to http:// when omitted.") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    )
                    Text("Proxy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton("Direct", proxyDirect) { onProxyDirect(true) }
                        ModeButton("Manual", !proxyDirect) { onProxyDirect(false) }
                    }
                    AnimatedVisibility(!proxyDirect) {
                        OutlinedTextField(
                            value = proxy,
                            onValueChange = onProxy,
                            label = { Text("Proxy address") },
                            supportingText = { Text("host:port or http(s)://, socks4://, socks5://") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom headers & body (optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onExpanded) {
                            Icon(if (expandedAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                    }
                    AnimatedVisibility(expandedAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!payloadMode) {
                                Text("Method", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    responseMethods.forEach { m -> ModeButton(m, method == m) { onMethod(m) } }
                                }
                                OutlinedTextField(
                                    value = path,
                                    onValueChange = onPath,
                                    label = { Text("Path / query") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = headers,
                                    onValueChange = onHeaders,
                                    label = { Text("Custom headers") },
                                    supportingText = { Text("One Header: Value per line. Tokens {host}, {path}, {proxy} are expanded.") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp)
                                )
                                OutlinedTextField(
                                    value = body,
                                    onValueChange = onBody,
                                    label = { Text("Request body (optional)") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)
                                )
                            } else {
                                OutlinedTextField(
                                    value = headers,
                                    onValueChange = onHeaders,
                                    label = { Text("Raw HTTP request / payload") },
                                    supportingText = { Text("Use \\\\r\\\\n or new lines. The first line sets method/path.") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
                                )
                            }
                            ToggleRow("Payload mode (raw request)", payloadMode, onPayloadMode,
                                "Send the field above as a complete HTTP request.")
                            ToggleRow("Follow redirects", followRedirects, onFollow, "Up to 10 redirects.")
                            ToggleRow("Allow insecure", allowInsecure, onInsecure, "Skip server certificate verification; diagnostics only.")
                        }
                    }
                }

                "UDP" -> {
                    Text("Feasibility test for UDP Custom: is the UDP path blocked by your provider?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = targets, onValueChange = onTargets,
                        label = { Text("UDP Custom server (optional)") },
                        supportingText = { Text("host:port. Ranges and comma lists are supported. Leave empty for a local UDP socket check.") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    )
                }

                "DNS" -> {
                    Text("Feasibility test for SlowDNS: is the DNS path blocked by your provider?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = targets, onValueChange = onTargets,
                        label = { Text("SlowDNS domain") },
                        supportingText = { Text("Tunnel domain from your provider, e.g. dns.example.com.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = resolver, onValueChange = onResolver,
                        label = { Text("Resolver (optional)") },
                        supportingText = { Text("Empty = device DNS. host, host:port, udp://, tcp://, tls://, https:// are accepted.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Transport", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dnsTransports.forEach { t -> ModeButton(t, dnsTransport.equals(t, true)) { onDnsTransport(t) } }
                    }
                    OutlinedTextField(
                        value = authoritative, onValueChange = onAuthoritative,
                        label = { Text("Authoritative NS (optional)") },
                        supportingText = { Text("Host/IP of your SlowDNS server, used for the direct DNS path.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D4D63)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        ) { Text(label, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        ) { Text(label) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, valueLabel: String, onValue: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = GhiAccentBlue, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValue, valueRange = min..max, steps = 27)
    }
}

@Composable
private fun ResultToolbar(
    total: Int,
    results: List<CheckItem>,
    filter: String,
    onFilter: (String) -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Results = $total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
            TextButton(onClick = onCopyAll) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy all")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Not detected", "Cloudflare", "Fastly").forEach {
                FilterChip(
                    selected = filter == it,
                    onClick = { onFilter(it) },
                    label = { Text(it, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun LoadingResultCard(target: String) {
    OutlinedCard(
        border = CardDefaults.outlinedCardBorder(),
        colors = CardDefaults.outlinedCardColors(containerColor = GhiInk950),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GhiAccentBlue)
            Spacer(Modifier.width(12.dp))
            Text(target, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResponseResultCard(item: CheckItem, index: Int, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    var expanded by remember(item.target, index) { mutableStateOf(false) }
    val r = item.json
    val mode = item.mode
    val status = r.optInt("status", 0)
    val ok = when (mode) {
        "HTTP" -> status in 200..399 && r.optString("error").isBlank()
        else -> r.optBoolean("ok", false)
    }
    val statusLabel = if (mode == "HTTP") {
        if (status > 0) "$status ${statusReason(status)}" else r.optString("error", "Failed")
    } else if (r.optString("error").isNotBlank()) r.optString("error") else if (ok) "NOERROR" else "Failed"
    val statusColor = when {
        ok -> GhiSignalGreen
        status in 300..399 -> GhiSignalAmber
        else -> GhiSignalRed
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = GhiInk950),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp))
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (mode == "HTTP") r.optString("method", "GET") else mode, style = MaterialTheme.typography.labelSmall) },
                    enabled = false,
                    modifier = Modifier.height(28.dp)
                )
                Spacer(Modifier.width(6.dp))
                StatusChip(statusLabel, statusColor)
                Spacer(Modifier.weight(1f))
                Text("${r.optLong("latency_ms", 0)} ms", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(item.json.toString(2))) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Copy result", tint = GhiAccentBlue, modifier = Modifier.size(18.dp))
                }
            }

            Text(
                r.optString("check_label").ifBlank { r.optString("target", r.optString("domain", item.target)) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 34.dp, top = 2.dp)
            )
            if (r.optString("check_label").isNotBlank()) {
                Text(
                    r.optString("domain", item.target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp)
                )
            }

            if (mode == "HTTP") {
                Text(
                    "CDN: ${r.optString("cdn").ifBlank { "Not detected" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.optString("cdn").isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else GhiAccentCyan,
                    modifier = Modifier.padding(start = 34.dp, top = 6.dp)
                )
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 34.dp, top = 10.dp)) {
                    DetailRow("IP", r.optJSONArray("addresses")?.let { array -> (0 until array.length()).joinToString() { i -> array.optString(i) } } ?: "—")
                    if (mode == "HTTP") {
                        DetailRow("Final URL", r.optString("final_url", "—"))
                        DetailRow("Protocol", r.optString("http_protocol", "—"))
                        DetailRow("Server", r.optString("server", "—"))
                        DetailRow("Content-Type", r.optString("content_type", "—"))
                        DetailRow("TLS", "${r.optString("tls_version", "—")} ${r.optString("tls_cipher", "")}".trim())
                        DetailRow("CDN", r.optString("cdn", "Not detected"))
                        DetailRow("Title", r.optString("title", "—"))
                        DetailRow("Technologies", r.optJSONArray("technologies")?.let { array -> (0 until array.length()).joinToString() { i -> array.optString(i) } } ?: "—")
                        DetailRow("Security", r.optJSONArray("security_headers")?.let { array -> (0 until array.length()).joinToString() { i -> array.optString(i) } } ?: "—")
                        DetailRow("Body", "${r.optLong("body_size", 0)} bytes")
                        if (r.optString("body").isNotBlank()) {
                            InfoPanel(r.optString("body").take(5000))
                        }
                    } else {
                        DetailRow("Transport", r.optString("transport", "—"))
                        DetailRow("Resolver", r.optString("resolver", "—"))
                        DetailRow("RCode", r.optInt("rcode", -1).toString())
                        DetailRow("Answers", r.optInt("answers", 0).toString())
                        DetailRow("A records", r.optJSONArray("answer_ips")?.let { array -> (0 until array.length()).joinToString() { i -> array.optString(i) } } ?: "—")
                        DetailRow("Authority", r.optInt("authority_records", 0).toString())
                        r.optString("error").takeIf { it.isNotBlank() }?.let { InfoPanel(it, error = true) }
                    }
                    Text(
                        "Tap a long line to expand • response metadata is local to this check",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = .14f),
        shape = RoundedCornerShape(7.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .35f))
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}


@Composable
private fun DnsVerdict(results: List<CheckItem>) {
    val control = results.firstOrNull { it.json.optString("check_label") == "Resolver control" }?.json
    val intercept = results.firstOrNull { it.json.optString("check_label") == "Transparent interception test" }?.json
    val tunnel = results.firstOrNull { it.json.optString("check_label") == "SlowDNS tunnel via resolver" }?.json
    if (control == null || intercept == null || tunnel == null) return

    val controlOk = control.optBoolean("ok", false)
    val tunnelOk = tunnel.optBoolean("ok", false)
    val interceptionResponse = intercept.optBoolean("ok", false)
    val intercepted = interceptionResponse &&
        control.optInt("rcode", -1) == intercept.optInt("rcode", -2) &&
        control.optInt("answers", -1) == intercept.optInt("answers", -2)

    val (title, message, color) = when {
        tunnelOk -> Triple(
            "DNS path looks usable",
            "The SlowDNS domain returned a DNS response through the selected transport.",
            GhiSignalGreen
        )
        intercepted -> Triple(
            "DNS works, but the tunnel query does not reach the server",
            "DNS is transparently intercepted by the provider; use the recursive resolver, DoT/DoH, or check the SlowDNS domain/NS.",
            GhiSignalAmber
        )
        controlOk -> Triple(
            "DNS works, but the tunnel query failed",
            "The recursive resolver answered the control query. Check the SlowDNS domain, authoritative NS and selected transport.",
            GhiSignalAmber
        )
        else -> Triple(
            "DNS control query failed",
            "No usable recursive DNS response was received. Check the resolver and network path.",
            GhiSignalRed
        )
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = GhiInk950),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .45f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Verdict", style = MaterialTheme.typography.labelSmall)
                    Text(title, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
            VerdictLine("Resolver control", controlOk)
            VerdictLine("Transparent interception test", !intercepted)
            VerdictLine("SlowDNS tunnel via resolver", tunnelOk)
        }
    }
}

@Composable
private fun VerdictLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            null,
            tint = if (ok) GhiSignalGreen else GhiSignalRed,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoPanel(text: String, error: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (error) GhiSignalRed.copy(alpha = .08f) else GhiInk800)
            .border(1.dp, if (error) GhiSignalRed.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (error) Icons.Filled.Error else Icons.Filled.Info,
            null,
            tint = if (error) GhiSignalRed else GhiAccentBlue,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun statusReason(code: Int): String = when (code) {
    200 -> "OK"; 201 -> "Created"; 202 -> "Accepted"; 204 -> "No Content"
    301 -> "Moved Permanently"; 302 -> "Found"; 303 -> "See Other"
    304 -> "Not Modified"; 307 -> "Temporary Redirect"; 308 -> "Permanent Redirect"
    400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
    408 -> "Request Timeout"; 429 -> "Too Many Requests"; 500 -> "Internal Server Error"
    502 -> "Bad Gateway"; 503 -> "Service Unavailable"; 504 -> "Gateway Timeout"
    else -> "HTTP"
}

@Composable
fun IpToolsScreen(onResolveDomain: (String) -> String, onResolveIp: (String) -> String) {
    var mode by remember { mutableStateOf("Domain → IP(s)") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    GhiScreenScaffold("IP / Domain Tools") { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GhiHero("IP ↔ Domain", "Resolve domains to all returned IPs or IPs to reverse-DNS hostnames. Multiple lines are processed concurrently.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton("Domain → IP(s)", mode == "Domain → IP(s)") { mode = "Domain → IP(s)" }
                FilterButton("IP → Domain(s)", mode == "IP → Domain(s)") { mode = "IP → Domain(s)" }
            }
            OutlinedTextField(input, { input = it }, label = { Text(if (mode.startsWith("Domain")) "Domain(s)" else "IP address(es)") }, placeholder = { Text("One per line") }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), minLines = 4)
            Button(enabled = input.isNotBlank() && !running, onClick = {
                running = true
                val values = input.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(200)
                scope.launch {
                    output = values.map { value ->
                        async(Dispatchers.IO) {
                            val raw = if (mode.startsWith("Domain")) onResolveDomain(value) else onResolveIp(value)
                            value to raw
                        }
                    }.awaitAll()
                    running = false
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(if (running) Icons.Filled.Sync else Icons.Filled.Dns, null); Spacer(Modifier.width(8.dp)); Text(if (running) "RESOLVING…" else "RESOLVE") }
            if (output.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Results • ${output.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); IconButton(onClick = { clipboard.setText(AnnotatedString(output.joinToString("\n\n") { it.first + "\n" + it.second })) }) { Icon(Icons.Filled.ContentCopy, "Copy all") } }
                output.forEach { (value, raw) ->
                    GhiCard { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text(value, fontWeight = FontWeight.Bold); Text(raw, style = MaterialTheme.typography.bodySmall, color = GhiSlate300, modifier = Modifier.padding(top = 5.dp)) }; IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) { Icon(Icons.Filled.ContentCopy, "Copy") } } }
                }
            }
        }
    }
}

@Composable private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) { if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) } }
