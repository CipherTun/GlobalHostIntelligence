package io.ciphertun.ghi.feature.certificates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ciphertun.ghi.core.model.Certificate
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

/** Purpose: browse discovered TLS certificates, funnel into Certificate Detail. */
@Composable
fun CertificatesScreen(certificates: List<Certificate>, onBack: () -> Unit, onOpenCertificate: (String) -> Unit) {
    GhiScreenScaffold(title = "Certificates", onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(certificates, key = { it.id }) { cert ->
                ListItem(
                    headlineContent = { Text(cert.subjectCn ?: cert.sha256Fingerprint.take(16)) },
                    supportingContent = { Text(cert.issuer ?: "Unknown issuer") },
                    modifier = Modifier.clickable { onOpenCertificate(cert.id) },
                )
            }
        }
    }
}
