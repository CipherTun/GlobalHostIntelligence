package io.ciphertun.ghi.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun SearchScreen(onBack: () -> Unit, onQueryChange: (String) -> List<String>) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<String>()) }
    GhiScreenScaffold(title = "Search") { modifier ->
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it; results = onQueryChange(it) }, label = { Text("Search domains, hosts, IPs or ASNs") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("${results.size} result${if (results.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(results, key = { it }) { ListItem(headlineContent = { Text(it) }, supportingContent = { Text("Cached discovery result") }) } }
        }
    }
}
