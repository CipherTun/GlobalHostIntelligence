package io.ciphertun.ghi.feature.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

private fun JSONObject.eventDate(action: String): String {
    val events = optJSONArray("events") ?: return "—"
    for (i in 0 until events.length()) {
        val e = events.optJSONObject(i) ?: continue
        if (e.optString("action").equals(action, true)) return e.optString("date").substringBefore('T').ifBlank { "—" }
    }
    return "—"
}

/** Registration intelligence (RDAP, the modern WHOIS) for a domain or an IP. */
@Composable
fun RdapScreen(onLookup: (String) -> String) {
    var target by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var failedParse by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    fun run() {
        val value = target.trim()
        if (value.isBlank() || busy) return
        scope.launch {
            busy = true
            val parsed = withContext(Dispatchers.IO) { runCatching { JSONObject(onLookup(value)) }.getOrNull() }
            failedParse = parsed == null
            result = parsed
            busy = false
        }
    }

    GhiScreenScaffold("Registration (RDAP)") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
        ) {
            item { GhiHero("Who is behind it?", "RDAP is the modern WHOIS: registrar, lifecycle dates, name servers and abuse contact for any domain or IP address.") }
            item {
                OutlinedTextField(
                    target, { target = it },
                    label = { Text("Domain or IP address") },
                    placeholder = { Text("example.com") },
                    leadingIcon = { Icon(Icons.Filled.Badge, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            item {
                Button(onClick = { run() }, enabled = target.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("LOOK UP")
                }
                GhiBusyIndicator(busy, "rdap")
            }
            if (failedParse) item { GhiCard { Text("The lookup returned an unreadable answer.", color = GhiSignalAmber, modifier = Modifier.padding(12.dp)) } }
            val obj = result
            if (obj != null && !obj.optBoolean("ok")) {
                item { GhiCard { Text(obj.optString("error", "Lookup failed"), color = GhiSignalAmber, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) } }
            } else if (obj != null) {
                item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(obj.optString("ldhName", obj.optString("name", obj.optString("target"))), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(obj.toString(2))) }) { Icon(Icons.Filled.ContentCopy, "Copy raw JSON") }
                            }
                            Fact("Registrar", obj.optString("registrar"))
                            Fact("Registered", obj.eventDate("registration"))
                            Fact("Expires", obj.eventDate("expiration"))
                            Fact("Last changed", obj.eventDate("last changed"))
                            Fact("Abuse contact", obj.optString("abuse_email"))
                            Fact("Network", obj.optString("name"))
                            Fact("Range", listOf(obj.optString("startAddress"), obj.optString("endAddress")).filter { it.isNotBlank() }.joinToString(" – "))
                            Fact("Country", obj.optString("country"))
                        }
                    }
                }
                val status = obj.optJSONArray("status").strings()
                if (status.isNotEmpty()) item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            status.forEach { GhiStatusPill(it, StatusTone.NEUTRAL) }
                        }
                    }
                }
                val servers = obj.optJSONArray("nameservers").strings()
                if (servers.isNotEmpty()) item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Name servers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            servers.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = dim) }
                        }
                    }
                }
                val entities = obj.optJSONArray("entities")
                if (entities != null && entities.length() > 0) item {
                    GhiCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Entities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            for (i in 0 until entities.length()) {
                                val e = entities.optJSONObject(i) ?: continue
                                val roles = e.optJSONArray("roles").strings().joinToString(", ")
                                val line = listOf(e.optString("name"), roles.takeIf { it.isNotBlank() }?.let { "($it)" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
                                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = dim)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank() || value == "—") return
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
