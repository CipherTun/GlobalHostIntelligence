package io.ciphertun.ghi.feature.ip_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.model.IpAddress
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: everything known about one IP — geo, ASN, CDN edge status, domains resolving to it. */
@Composable
fun IpDetailScreen(
    ip: IpAddress,
    onBack: () -> Unit,
    onOpenAsn: (String) -> Unit,
) {
    GhiScreenScaffold(title = ip.address, onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Location: ${ip.geoCity ?: "?"}, ${ip.geoCountryCode ?: "?"}")
            Text("CDN edge: ${if (ip.isCdnEdge) "yes" else "no"}")
            ip.asnId?.let { asnId ->
                Button(onClick = { onOpenAsn(asnId) }, modifier = Modifier.fillMaxWidth()) { Text("View ASN") }
            }
        }
    }
}
