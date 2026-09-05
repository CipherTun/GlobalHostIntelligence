package io.ciphertun.ghi.feature.exports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun ExportsScreen(domains: List<String>, onBack: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf("") }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            message = "Export cancelled"
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pending.toByteArray()) }
                    ?: error("Unable to open export destination")
            }.onSuccess { message = "Export saved" }
                .onFailure { message = "Export failed: ${it.message}" }
        }
    }
    GhiScreenScaffold(title = "Export Center", onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Export ${domains.size} cached discovery result${if (domains.size == 1) "" else "s"}.")
            Button(onClick = { pending = domains.joinToString("\n"); launcher.launch("ghi-domains.txt") }, modifier = Modifier.fillMaxWidth()) { Text("Export domain list") }
            Button(onClick = { pending = "{\"domains\":[${domains.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }}]}"; launcher.launch("ghi-domains.json") }, modifier = Modifier.fillMaxWidth()) { Text("Export JSON") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
