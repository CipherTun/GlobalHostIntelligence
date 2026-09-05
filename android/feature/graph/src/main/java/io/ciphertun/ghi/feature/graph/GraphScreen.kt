package io.ciphertun.ghi.feature.graph

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun GraphScreen(domainId: String?, onBack: () -> Unit, domains: List<String>) {
    GhiScreenScaffold(title = "Relationship Graph", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(domainId ?: "Discovery relationship view", style = MaterialTheme.typography.headlineSmall) }
            item { Text("Discovered nodes are shown from the current passive-discovery cache. Shared certificate/IP relationships are added when host analysis is available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(domains.take(100), key = { it }) { node -> ListItem(headlineContent = { Text(node) }, supportingContent = { Text(if (node == domainId) "Selected node" else "Discovered node") }) }
        }
    }
}
