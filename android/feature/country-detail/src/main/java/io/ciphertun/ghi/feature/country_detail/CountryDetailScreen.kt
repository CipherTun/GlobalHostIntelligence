package io.ciphertun.ghi.feature.country_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/**
 * Purpose: everything known about one country — domain count, top
 * organizations/ASNs hosted there, and a way into the filtered Domains
 * list for just this country.
 */
@Composable
fun CountryDetailScreen(
    countryCode: String,
    onBack: () -> Unit,
    onViewDomains: (countryCode: String) -> Unit,
) {
    GhiScreenScaffold(title = countryCode, onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Country detail for $countryCode")
            Button(onClick = { onViewDomains(countryCode) }, modifier = Modifier.fillMaxWidth()) {
                Text("View domains in $countryCode")
            }
        }
    }
}
