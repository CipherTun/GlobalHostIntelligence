package io.ciphertun.ghi.feature.asns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.model.Asn
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: browse discovered ASNs (network operators), funnel into ASN Detail. */
@Composable
fun AsnsScreen(asns: List<Asn>, onBack: () -> Unit, onOpenAsn: (String) -> Unit) {
    GhiScreenScaffold(title = "ASNs", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(asns, key = { it.id }) { asn ->
                ListItem(
                    headlineContent = { Text("AS${asn.asnNumber} ${asn.name ?: ""}".trim()) },
                    supportingContent = { Text(asn.countryCode ?: "Unknown") },
                    modifier = Modifier.clickable { onOpenAsn(asn.id) },
                )
            }
        }
    }
}
