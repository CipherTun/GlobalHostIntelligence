package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Domain(
    val id: String,
    val fqdn: String,
    val tld: String,
    val registrableDomain: String,
    val primaryIpId: String? = null,
    val currentCertificateId: String? = null,
    val httpStatus: Int? = null,
    val httpsStatus: Int? = null,
    val httpServerHeader: String? = null,
    val tlsVersion: String? = null,
    val tlsValid: Boolean? = null,
    val latencyMs: Long? = null,
    val contentType: String? = null,
    val cdn: String? = null,
    val addresses: List<String> = emptyList(),
    val discoveredVia: String? = null,
    val discoveredAt: String,
    val lastSeenAt: String,
    val countrySignals: List<CountrySignal> = emptyList(),
)
