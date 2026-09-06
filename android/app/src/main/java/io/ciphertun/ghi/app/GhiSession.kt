package io.ciphertun.ghi.app

import android.content.Context
import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge
import io.ciphertun.ghi.feature.discover.DomainPing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class GhiSession(context: Context) {
    private val prefs = context.getSharedPreferences("ghi_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _liveResults = MutableStateFlow<List<DomainPing>>(emptyList())
    val liveResults: StateFlow<List<DomainPing>> = _liveResults.asStateFlow()
    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private var discoveryJob: Job? = null

    fun startDiscovery(query: String, scopeMode: String = "country", maxResults: Int = discoveryLimit()): String {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return ""
        val limit = maxResults.coerceIn(10, 500)
        discoveryJob?.cancel()
        _status.value = "RUNNING"
        _error.value = null
        _elapsedMs.value = 0L
        _liveResults.value = emptyList()

        discoveryJob = scope.launch {
            val started = System.currentTimeMillis()
            val seen = ConcurrentHashMap.newKeySet<String>()
            val failures = ConcurrentHashMap.newKeySet<String>()
            val sources = if (scopeMode.equals("domain", true)) {
                enabledSources().filterNot { it == "country" }.toList()
            } else {
                buildList {
                    if (enabledSources().contains("country")) add("country-world")
                    else if (enabledSources().contains("urlscan")) add("urlscan")
                }
            }.distinct()

            if (sources.isEmpty()) {
                _error.value = "Enable at least one discovery source in Sources."
                _status.value = "FAILED"
                return@launch
            }

            if (sources.contains("country-world")) {
                val raw = withContext(Dispatchers.IO) {
                    GhiMobileBridge.discoverCountryWorld(normalized, limit, providerConfigJson())
                }
                val obj = runCatching { JSONObject(raw) }.getOrElse { throw IllegalStateException("Invalid country discovery response") }
                val arr = obj.optJSONArray("domains") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
                    if (value.contains('.') && !value.contains(':') && !value.contains(' ')) seen.add(value)
                }
                obj.optJSONObject("errors")?.let { errors ->
                    errors.keys().forEach { failures.add(it) }
                }
            }

            if (!sources.contains("country-world")) coroutineScope {
                val sourceGate = kotlinx.coroutines.sync.Semaphore(sourceParallelism())
                sources.map { source ->
                    async(Dispatchers.IO) {
                        sourceGate.withPermit {
                            runCatching {
                                val raw = GhiMobileBridge.discoverRawSource(normalized, source, (limit * 2).coerceAtMost(1000))
                                val obj = JSONObject(raw)
                                val arr = obj.optJSONArray("domains") ?: obj.optJSONArray("results") ?: JSONArray()
                                for (i in 0 until arr.length()) {
                                    val item = arr.opt(i)
                                    val candidate = if (item is JSONObject) item.optString("domain") else item.toString()
                                    candidate.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
                                        .takeIf { it.isNotBlank() && it.contains('.') && !it.contains(':') && !it.contains(' ') }
                                        ?.let(seen::add)
                                }
                                obj.optString("error").takeIf { it.isNotBlank() }?.let { failures.add(source) }
                            }.onFailure { failures.add(source) }
                        }
                    }
                }.awaitAll()

                // Bounded validation: never create one coroutine/native call per candidate.
                val candidates = seen.toList().take(limit * 4)
                val workerCount = minOf(validationThreads(), candidates.size).coerceAtLeast(1)
                val queue = kotlinx.coroutines.channels.Channel<String>(workerCount)
                val accepted = ConcurrentHashMap<String, DomainPing>()
                val workers = List(workerCount) {
                    launch(Dispatchers.IO) {
                        for (candidate in queue) {
                            if (!isActive || accepted.size >= limit) continue
                            val analyzed = runCatching {
                                JSONObject(GhiMobileBridge.analyzeHostWithOptions(candidate, validationTimeout(), userAgent()))
                            }.getOrNull() ?: continue
                            val https = analyzed.optInt("https_status", -1)
                            val http = analyzed.optInt("http_status", -1)
                            val code = when { https in 200..399 -> https; http in 200..399 -> http; else -> -1 }
                            if (code in 200..399) accepted.putIfAbsent(candidate, DomainPing(candidate, analyzed.optLong("elapsed_ms", 0L), code))
                        }
                    }
                }
                val publisher = launch(Dispatchers.Main.immediate) {
                    while (isActive && workers.any { it.isActive }) {
                        _liveResults.value = accepted.values.sortedBy { it.domain }.take(limit)
                        delay(120)
                    }
                }
                for (candidate in candidates) {
                    if (!isActive || accepted.size >= limit) break
                    queue.send(candidate)
                }
                queue.close()
                workers.joinAll()
                publisher.cancelAndJoin()
                _liveResults.value = accepted.values.sortedBy { it.domain }.take(limit)
            }
            _elapsedMs.value = System.currentTimeMillis() - started
            if (isActive) {
                _status.value = when {
                    _liveResults.value.size >= limit -> "COMPLETED"
                    _liveResults.value.isNotEmpty() && failures.isNotEmpty() -> "PARTIAL"
                    _liveResults.value.isNotEmpty() -> "COMPLETED"
                    failures.isNotEmpty() -> "FAILED"
                    else -> "COMPLETED"
                }
            }
        }
        return normalized
    }

    fun stopDiscovery() {
        discoveryJob?.cancel(); discoveryJob = null
        if (_status.value == "RUNNING") _status.value = "STOPPED"
    }

    fun analyze(host: String): String = GhiMobileBridge.analyzeHostWithOptions(host, validationTimeout(), userAgent())
    fun checkResponse(mode: String, targets: String, proxy: String, method: String, path: String, headers: String, body: String, followRedirects: Boolean, allowInsecure: Boolean, timeoutSeconds: Int, payloadMode: Boolean, dnsTransport: String, resolver: String, authoritative: String): String =
        GhiMobileBridge.checkResponse(mode, targets, proxy, method, path, headers, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode, dnsTransport, resolver, authoritative)
    fun resolve(host: String): String = GhiMobileBridge.resolveDomain(host)
    fun resolveIp(value: String): String = GhiMobileBridge.resolveIp(value)
    fun discoverSubdomains(domain: String, maxResults: Int = 500): String = GhiMobileBridge.discoverSubdomains(domain, maxResults)

    fun saveSources(sources: Set<String>) = prefs.edit().putStringSet("enabled_sources", sources).apply()

    fun exportResults(format: String): String {
        val current = _liveResults.value
        return when (format.lowercase()) {
            "csv" -> buildString { appendLine("domain,status,latency_ms"); current.forEach { appendLine("${it.domain},${it.status},${it.latencyMs}") } }
            "json" -> JSONArray(current.map { JSONObject().apply { put("domain", it.domain); put("status", it.status); put("latency_ms", it.latencyMs) } }).toString(2)
            else -> current.joinToString("\n") { it.domain }
        }
    }

    fun discoveryLimit() = prefs.getInt("discovery_limit", 500).coerceIn(10, 500)
    fun validationThreads() = prefs.getInt("validation_threads", 32).coerceIn(1, 128)
    fun sourceParallelism() = prefs.getInt("source_parallelism", 8).coerceIn(1, 24)
    fun validationTimeout() = prefs.getInt("validation_timeout", 8).coerceIn(2, 30)
    fun userAgent() = prefs.getString("user_agent", "GlobalHostIntelligence/3.0") ?: "GlobalHostIntelligence/3.0"
    fun animationsEnabled() = prefs.getBoolean("animations", true)
    fun compactResults() = prefs.getBoolean("compact_results", false)
    fun enabledSources(): Set<String> = prefs.getStringSet("enabled_sources", DEFAULT_SOURCES)?.toSet() ?: DEFAULT_SOURCES

    fun providerConfigJson(): String = JSONObject().apply {
        put("Censys", prefs.getString("provider_censys", "") ?: "")
        put("Netlas", prefs.getString("provider_netlas", "") ?: "")
        put("Shodan", prefs.getString("provider_shodan", "") ?: "")
    }.toString()

    fun saveSettings(limit: Int, threads: Int, parallel: Int, timeout: Int, agent: String, sources: Set<String>, animations: Boolean, compact: Boolean) {
        prefs.edit().putInt("discovery_limit", limit.coerceIn(10, 500)).putInt("validation_threads", threads.coerceIn(1, 128))
            .putInt("source_parallelism", parallel.coerceIn(1, 24)).putInt("validation_timeout", timeout.coerceIn(2, 30))
            .putString("user_agent", agent.trim().ifBlank { "GlobalHostIntelligence/3.0" }).putStringSet("enabled_sources", sources)
            .putBoolean("animations", animations).putBoolean("compact_results", compact).apply()
    }

    fun resetSettings() = prefs.edit().clear().apply()

    companion object {
        val DEFAULT_SOURCES = linkedSetOf("urlscan","crt.sh","crt.name","ctlogs.dev","certspotter","rapiddns","anubis","subdomain.center","hackertarget","wayback","threatminer","commoncrawl","otx","subdomain.app","sonar","riddler","jldc","sublist3r","country")
    }
}
