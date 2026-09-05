package io.ciphertun.ghi.feature.domains

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.model.Domain
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/**
 * Purpose: browse/paginate all discovered domains, optionally pre-filtered
 * by country (when reached from Country Detail). Funnels into Domain Detail.
 */
@Composable
fun DomainsScreen(
    domains: List<Domain>,
    countryFilter: String?,
    onBack: () -> Unit,
    onOpenDomain: (String) -> Unit,
) {
    val title = if (countryFilter != null) "Domains — $countryFilter" else "Domains"
    GhiScreenScaffold(title = title, onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(domains, key = { it.id }) { domain ->
                ListItem(
                    headlineContent = { Text(domain.fqdn) },
                    supportingContent = { Text(domain.registrableDomain) },
                    modifier = Modifier.clickable { onOpenDomain(domain.id) },
                )
            }
        }
    }
}
