package io.ciphertun.ghi.feature.certificate_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import io.ciphertun.ghi.core.model.Certificate
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: full certificate details — subject, issuer, validity window, SAN domains (each a relationship signal). */
@Composable
fun CertificateDetailScreen(certificate: Certificate, onBack: () -> Unit) {
    GhiScreenScaffold(title = certificate.subjectCn ?: "Certificate", onBack = onBack) { modifier ->
        Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Issuer: ${certificate.issuer ?: "Unknown"}")
            Text("Valid: ${certificate.notBefore ?: "?"} to ${certificate.notAfter ?: "?"}")
            Text("SAN domains (${certificate.sanDomains.size}):")
            certificate.sanDomains.forEach { Text("• $it") }
        }
    }
}
