package io.ciphertun.ghi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SourceHealth(
    val name: String,
    val status: String, // ONLINE | DEGRADED | OFFLINE
    val requestCount: Long = 0,
    val errorCount: Long = 0,
    val latencyMillis: Long? = null,
)
