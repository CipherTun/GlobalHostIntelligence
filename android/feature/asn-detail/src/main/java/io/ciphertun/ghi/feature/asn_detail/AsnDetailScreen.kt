package io.ciphertun.ghi.feature.asn_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import io.ciphertun.ghi.core.model.Asn
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: everything known about one network operator — org, country, IP blocks seen. */
@Composable
fun AsnDetailScreen(asn: Asn, onBack: () -> Unit) {
    GhiScreenScaffold(title = "AS${asn.asnNumber}", onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(asn.name ?: "Unnamed network")
            Text("Country: ${asn.countryCode ?: "Unknown"}")
        }
    }
}
