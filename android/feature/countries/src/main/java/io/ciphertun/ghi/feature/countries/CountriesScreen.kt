package io.ciphertun.ghi.feature.countries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.model.Country
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: browse discovered countries, funnel into Country Detail. */
@Composable
fun CountriesScreen(
    countries: List<Country>,
    onBack: () -> Unit,
    onOpenCountry: (String) -> Unit,
) {
    GhiScreenScaffold(title = "Countries", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(countries, key = { it.code }) { country ->
                ListItem(
                    headlineContent = { Text("${country.flagEmoji ?: ""} ${country.name}".trim()) },
                    supportingContent = { Text(country.code) },
                    modifier = Modifier.clickable { onOpenCountry(country.code) },
                )
            }
        }
    }
}
