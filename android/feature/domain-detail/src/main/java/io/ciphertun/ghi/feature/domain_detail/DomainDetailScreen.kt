package io.ciphertun.ghi.feature.domain_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.model.Domain
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun DomainDetailScreen(
    domain: Domain,
    onBack: () -> Unit,
    onOpenIp: (String) -> Unit,
    onOpenCertificate: (String) -> Unit,
    onOpenRelated: (String) -> Unit,
    onOpenGraph: (String) -> Unit,
    onCheckResponse: () -> Unit = {},
) {
    GhiScreenScaffold(title = domain.fqdn, onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(domain.fqdn, style = MaterialTheme.typography.headlineSmall)
            Text("Registrable: ${domain.registrableDomain}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            Text("Network", style = MaterialTheme.typography.titleMedium)
            domain.httpStatus?.let { Text("HTTP: $it") }
            domain.httpsStatus?.let { Text("HTTPS: $it") }
            domain.httpServerHeader?.takeIf { it.isNotBlank() }?.let { Text("Server: $it") }
            domain.tlsVersion?.let { Text("TLS: $it  •  verified: ${domain.tlsValid ?: "unknown"}") }
            domain.cdn?.takeIf { it.isNotBlank() }?.let { Text("CDN: $it") }
            domain.contentType?.takeIf { it.isNotBlank() }?.let { Text("Content type: $it") }
            domain.latencyMs?.let { Text("Latency: ${it} ms") }
            if (domain.addresses.isNotEmpty()) Text("Addresses: ${domain.addresses.joinToString()}")

            HorizontalDivider()
            Text("Country signals", style = MaterialTheme.typography.titleMedium)
            if (domain.countrySignals.isEmpty()) {
                Text("No country signals computed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                domain.countrySignals.forEach { signal ->
                    Text("${signal.signalType}: ${signal.countryCode} (${signal.confidence}%)")
                }
            }

            domain.primaryIpId?.let {
                Button(onClick = { onOpenIp(it) }, modifier = Modifier.fillMaxWidth()) { Text("VIEW RESOLVED IP") }
            }
            domain.currentCertificateId?.let {
                Button(onClick = { onOpenCertificate(it) }, modifier = Modifier.fillMaxWidth()) { Text("VIEW CERTIFICATE") }
            }
            Button(onClick = onCheckResponse, modifier = Modifier.fillMaxWidth()) { Text("OPEN RESPONSE CHECKER") }
            Button(onClick = { onOpenGraph(domain.id) }, modifier = Modifier.fillMaxWidth()) { Text("VIEW RELATIONSHIP GRAPH") }
        }
    }
}
