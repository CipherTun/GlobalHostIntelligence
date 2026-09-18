package io.ciphertun.ghi.feature.ips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.model.IpAddress
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: browse discovered IP addresses, funnel into IP Detail. */
@Composable
fun IpsScreen(ips: List<IpAddress>, onBack: () -> Unit, onOpenIp: (String) -> Unit) {
    GhiScreenScaffold(title = "IP Addresses", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(ips, key = { it.id }) { ip ->
                ListItem(
                    headlineContent = { Text(ip.address) },
                    supportingContent = { Text(ip.geoCity ?: ip.geoCountryCode ?: "Unknown location") },
                    modifier = Modifier.clickable { onOpenIp(ip.id) },
                )
            }
        }
    }
}
