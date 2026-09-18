package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Certificate(
    val id: String,
    val sha256Fingerprint: String,
    val subjectCn: String? = null,
    val subjectOrg: String? = null,
    val subjectOrgCountry: String? = null,
    val issuer: String? = null,
    val notBefore: String? = null,
    val notAfter: String? = null,
    val sanDomains: List<String> = emptyList(),
)
