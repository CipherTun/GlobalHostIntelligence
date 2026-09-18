package io.ciphertun.ghi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun GhiWebSurfaceScreen(inspect: (String) -> String) {
    var target by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    GhiScreenScaffold("Web Surface") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "WEB SURFACE INSPECTOR",
                    "One bounded landing-page read plus standard metadata files. No recursive crawling and no guessed paths."
                )
            }
            item {
                GhiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            target,
                            { target = it },
                            label = { Text("URL / host") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = target.isNotBlank() && !running,
                            onClick = {
                                running = true
                                scope.launch {
                                    raw = withContext(Dispatchers.IO) { inspect(target.trim()) }
                                    running = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.Language, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "INSPECTING…" else "INSPECT SURFACE")
                        }
                        GhiBusyIndicator(running, "web surface")
                    }
                }
            }
            if (raw.isNotBlank()) {
                SurfaceResult(raw, clipboard)
            }
        }
    }
}

@Composable
private fun SurfaceResult(raw: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    val obj = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    if (obj == null) {
        GhiCard { Text(raw, Modifier.padding(14.dp), color = GhiSlate300) }
        return
    }
    if (!obj.optBoolean("ok", false)) {
        GhiCard {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, null, tint = GhiSignalAmber)
                Spacer(Modifier.width(8.dp))
                Text(obj.optString("error", "Inspection failed"), color = GhiSignalAmber, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = GhiAccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("LANDING PAGE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) { Icon(Icons.Filled.ContentCopy, "Copy raw result") }
            }
            DetailLine("Status", obj.optInt("status", 0).toString())
            DetailLine("Server", obj.optString("server"))
            DetailLine("Content type", obj.optString("content_type"))
            DetailLine("Bytes", obj.optInt("content_length", 0).toString())
            DetailLine("Title", obj.optString("title"))
            DetailLine("Canonical", obj.optString("canonical"))
            DetailLine("SHA-256", obj.optString("body_sha256"))
        }
    }

    val endpoints = obj.optJSONArray("endpoints") ?: JSONArray()
    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("STANDARD ENDPOINTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for (i in 0 until endpoints.length()) {
                val item = endpoints.optJSONObject(i) ?: continue
                val status = item.optInt("status", 0)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(item.optString("path"), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (status > 0) "HTTP $status • ${item.optInt("bytes", 0)} bytes" else item.optString("error", "No response"),
                            color = if (status in 200..399) GhiSignalGreen else GhiSlate300,
                            style = MaterialTheme.typography.bodySmall
                        )
                        item.optString("location").takeIf { it.isNotBlank() }?.let { Text("→ $it", color = GhiSlate300, style = MaterialTheme.typography.labelSmall) }
                        item.optString("preview").takeIf { it.isNotBlank() }?.let { Text(it, color = GhiSlate300, style = MaterialTheme.typography.bodySmall, maxLines = 5) }
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(item.optString("url"))) }) { Icon(Icons.Filled.ContentCopy, "Copy URL") }
                }
                if (i < endpoints.length() - 1) HorizontalDivider()
            }
        }
    }

    val links = obj.optJSONArray("links") ?: JSONArray()
    GhiCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAME-ORIGIN LINKS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${links.length()}", color = GhiSlate300)
                IconButton(onClick = { clipboard.setText(AnnotatedString((0 until links.length()).joinToString("\n") { links.optString(it) })) }) {
                    Icon(Icons.Filled.ContentCopy, "Copy links")
                }
            }
            val limit = minOf(links.length(), 30)
            for (i in 0 until limit) Text(links.optString(i), color = GhiSlate300, style = MaterialTheme.typography.bodySmall)
            if (links.length() > limit) Text("Showing first $limit links.", color = GhiSlate300, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(92.dp), color = GhiSlate500, style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}
