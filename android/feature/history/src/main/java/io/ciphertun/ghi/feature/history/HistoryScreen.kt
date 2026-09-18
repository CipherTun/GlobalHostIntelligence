package io.ciphertun.ghi.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable fun HistoryScreen(recentLabels: List<String>, onBack: () -> Unit) {
    GhiScreenScaffold(title = "History", onBack = onBack) { modifier ->
        if (recentLabels.isEmpty()) Box(modifier.fillMaxSize().padding(16.dp)) { Text("No discovery history yet.") }
        else LazyColumn(modifier.fillMaxSize()) { items(recentLabels) { ListItem(headlineContent = { Text(it) }, supportingContent = { Text("Passive discovery session") }) } }
    }
}
