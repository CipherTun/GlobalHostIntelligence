package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

/**
 * One piece of evidence toward a domain's country classification.
 * A domain typically has several of these (tld, ip_geo, asn,
 * organization, rdap, certificate_org, nameserver) with independent
 * confidence — deliberately not collapsed into one "country" field.
 */
@Serializable
data class CountrySignal(
    val signalType: String,
    val countryCode: String,
    val confidence: Double,
    val evidence: String? = null,
)
