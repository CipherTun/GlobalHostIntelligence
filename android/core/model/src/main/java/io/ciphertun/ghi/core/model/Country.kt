package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Country(
    val code: String,
    val name: String,
    val flagEmoji: String? = null,
)
