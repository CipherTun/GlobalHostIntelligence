package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class IpAddress(
    val id: String,
    val address: String,
    val asnId: String? = null,
    val geoCountryCode: String? = null,
    val geoCity: String? = null,
    val isCdnEdge: Boolean = false,
    val cdnId: String? = null,
)
