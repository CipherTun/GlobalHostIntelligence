package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Job(
    val id: String,
    val scopeType: String, // GLOBAL | COUNTRY
    val scopeValue: String? = null,
    val status: String,
    val domainsFound: Int = 0,
)
