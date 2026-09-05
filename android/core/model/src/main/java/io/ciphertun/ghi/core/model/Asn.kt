package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Asn(
    val id: String,
    val asnNumber: Int,
    val name: String? = null,
    val organizationId: String? = null,
    val countryCode: String? = null,
)
