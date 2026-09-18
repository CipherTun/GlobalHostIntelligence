package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Relationship(
    val relatedDomainId: String,
    val relatedFqdn: String,
    val relationshipType: String,
    val evidence: String? = null,
)
