package io.ciphertun.ghi.feature.settings

/**
 * Everything the discovery / validation engine needs, as one immutable value so
 * the Settings screen can diff a draft against the saved state.
 */
data class GhiEngineSettings(
    val discoveryLimit: Int = 500,
    val validationThreads: Int = 32,
    val sourceParallelism: Int = 8,
    val validationTimeout: Int = 8,
    /** Extra attempts for hosts that time out (0..2). */
    val validationRetries: Int = 0,
    /** Count any HTTP answer (401, 403, 404, 5xx...) as reachable, not only 2xx-3xx. */
    val acceptAnyResponse: Boolean = false,
    /** Back off validation workers automatically when many probes time out. */
    val adaptiveConcurrency: Boolean = true,
    val userAgent: String = "",
    val projectDiscoveryApiKey: String = "",
    val enabledSources: Set<String> = emptySet(),
)
