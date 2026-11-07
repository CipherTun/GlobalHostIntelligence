package io.ciphertun.ghi.feature.discover

/** Live counters for one discovery run, shown on the Discovery screen. */
data class DiscoveryProgress(
    /** idle | sources | validating | done | stopped */
    val phase: String = "idle",
    val sourcesTotal: Int = 0,
    val sourcesDone: Int = 0,
    val candidates: Int = 0,
    val validated: Int = 0,
    val live: Int = 0,
    val failedSources: List<String> = emptyList(),
    /** Validation workers currently allowed to run (adaptive). */
    val concurrency: Int = 0,
)
