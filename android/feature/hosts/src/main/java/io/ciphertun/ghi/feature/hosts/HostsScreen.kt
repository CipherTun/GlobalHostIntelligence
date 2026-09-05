package io.ciphertun.ghi.feature.hosts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable fun HostsScreen(hosts: List<String>, onBack: () -> Unit, onOpenHost: (String) -> Unit) {
    GhiScreenScaffold(title = "Hosts", onBack = onBack) { modifier ->
        if (hosts.isEmpty()) Box(modifier.fillMaxSize().padding(16.dp)) { Text("No hosts discovered yet. Start a passive discovery run first.") }
        else LazyColumn(modifier.fillMaxSize()) { items(hosts, key = { it }) { host -> ListItem(headlineContent = { Text(host) }, supportingContent = { Text("Discovered host") }, modifier = Modifier.fillMaxWidth().clickable { onOpenHost(host) }) } }
    }
}
