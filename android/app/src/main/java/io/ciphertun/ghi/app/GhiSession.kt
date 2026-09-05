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
import java.util.UUID
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
        val id = UUID.randomUUID().toString()
        _status.value = "RUNNING"
        _error.value = null
        _elapsedMs.value = 0
        _liveResults.value = emptyList()
        discoveryJob?.cancel()

        discoveryJob = scope.launch {
            val started = System.currentTimeMillis()
            val seen = ConcurrentHashMap.newKeySet<String>()
            val validationGate = Semaphore(validationThreads())
            val sourceGate = Semaphore(sourceParallelism())
            val sources = if (scopeMode.equals("domain", true)) {
                enabledSources().filterNot { it == "country" }
                    .map { it to normalized }
            } else {
                buildList {
                    if (enabledSources().contains("country")) add("country" to normalized)
                    if (enabledSources().contains("urlscan")) add("urlscan" to normalized)
                }
            }.distinct()

            if (sources.isEmpty()) {
                _error.value = "Enable at least one discovery source in Settings."
                _status.value = "FAILED"
                return@launch
            }

            coroutineScope {
                sources.map { (source, sourceQuery) ->
                    launch(Dispatchers.IO) {
                        sourceGate.withPermit {
                            val raw = GhiMobileBridge.discoverCandidates(sourceQuery, source, (maxResults * 2).coerceAtMost(2000))
                            runCatching {
                                val obj = JSONObject(raw)
                                val arr = obj.optJSONArray("domains") ?: obj.optJSONArray("results") ?: JSONArray()
                                val candidates = buildList {
                                    for (i in 0 until arr.length()) {
                                        val item = arr.opt(i)
                                        val domain = if (item is JSONObject) item.optString("domain") else item.toString()
                                        if (domain.isNotBlank()) add(domain)
                                    }
                                }
                                coroutineScope {
                                    candidates.map { candidate ->
                                        async(Dispatchers.IO) {
                                            val domain = candidate.trim().lowercase()
                                            if (domain.isBlank() || !seen.add(domain)) return@async
                                            validationGate.withPermit {
                                                val analyzed = runCatching {
                                                    JSONObject(GhiMobileBridge.analyzeHostWithOptions(domain, validationTimeout(), userAgent()))
                                                }.getOrNull() ?: return@withPermit
                                                val https = analyzed.optInt("https_status", -1)
                                                val http = analyzed.optInt("http_status", -1)
                                                val status = when { https in 200..399 -> https; http in 200..399 -> http; else -> -1 }
                                                if (status < 0) return@withPermit
                                                val result = DomainPing(domain, analyzed.optLong("elapsed_ms", 0L), status)
                                                withContext(Dispatchers.Main.immediate) {
                                                    if (_liveResults.value.size < maxResults && _liveResults.value.none { it.domain == domain }) {
                                                        _liveResults.value = _liveResults.value + result
                                                    }
                                                }
                                            }
                                        }
                                    }.awaitAll()
                                }
                                obj.optString("error").takeIf { it.isNotBlank() }?.let { msg ->
                                    withContext(Dispatchers.Main.immediate) { _error.value = "$source: $msg" }
                                }
                            }.onFailure { e ->
                                withContext(Dispatchers.Main.immediate) { _error.value = "$source: ${e.message ?: "source failed"}" }
                            }
                        }
                    }
                }.joinAll()
            }
            _elapsedMs.value = System.currentTimeMillis() - started
            if (isActive) _status.value = if (_liveResults.value.isNotEmpty()) "COMPLETED" else "FAILED"
        }
        return id
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        if (_status.value == "RUNNING") _status.value = "STOPPED"
    }

    fun analyze(host: String): String = GhiMobileBridge.analyzeHostWithOptions(host, validationTimeout(), userAgent())
    fun checkResponse(mode: String, targets: String, proxy: String, method: String, path: String, headers: String, body: String, followRedirects: Boolean, allowInsecure: Boolean, timeoutSeconds: Int, payloadMode: Boolean, dnsTransport: String, resolver: String, authoritative: String): String =
        GhiMobileBridge.checkResponse(mode, targets, proxy, method, path, headers, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode, dnsTransport, resolver, authoritative)

    fun resolve(host: String): String = GhiMobileBridge.resolveDomain(host)
    fun resolveIp(value: String): String = GhiMobileBridge.resolveIp(value)

    fun saveSources(sources: Set<String>) {
        prefs.edit().putStringSet("enabled_sources", sources).apply()
    }

    fun exportResults(format: String): String {
        val current = _liveResults.value
        return when (format.lowercase()) {
            "csv" -> buildString {
                appendLine("domain,status,latency_ms")
                current.forEach { appendLine("${it.domain},${it.status},${it.latencyMs}") }
            }
            "json" -> JSONArray(current.map { JSONObject().apply {
                put("domain", it.domain); put("status", it.status); put("latency_ms", it.latencyMs)
            } }).toString(2)
            else -> current.joinToString("\n") { it.domain }
        }
    }

    fun discoveryLimit() = prefs.getInt("discovery_limit", 500).coerceIn(10, 5000)
    fun validationThreads() = prefs.getInt("validation_threads", 32).coerceIn(1, 256)
    fun sourceParallelism() = prefs.getInt("source_parallelism", 8).coerceIn(1, 32)
    fun validationTimeout() = prefs.getInt("validation_timeout", 10).coerceIn(2, 60)
    fun userAgent() = prefs.getString("user_agent", "GlobalHostIntelligence/2.3") ?: "GlobalHostIntelligence/2.3"
    fun animationsEnabled() = prefs.getBoolean("animations", true)
    fun compactResults() = prefs.getBoolean("compact_results", false)

    fun enabledSources(): Set<String> = prefs.getStringSet("enabled_sources", DEFAULT_SOURCES)?.toSet() ?: DEFAULT_SOURCES

    fun saveSettings(limit: Int, threads: Int, parallel: Int, timeout: Int, agent: String, sources: Set<String>, animations: Boolean, compact: Boolean) {
        prefs.edit()
            .putInt("discovery_limit", limit.coerceIn(10, 5000))
            .putInt("validation_threads", threads.coerceIn(1, 256))
            .putInt("source_parallelism", parallel.coerceIn(1, 32))
            .putInt("validation_timeout", timeout.coerceIn(2, 60))
            .putString("user_agent", agent.trim().ifBlank { "GlobalHostIntelligence/2.3" })
            .putStringSet("enabled_sources", sources)
            .putBoolean("animations", animations)
            .putBoolean("compact_results", compact)
            .apply()
    }

    fun resetSettings() { prefs.edit().clear().apply() }


    companion object {
        val DEFAULT_SOURCES = linkedSetOf(
            "urlscan", "crt.sh", "crt.name", "ctlogs.dev", "certspotter", "rapiddns",
            "anubis", "subdomain.center", "hackertarget", "wayback", "threatminer",
            "commoncrawl", "otx", "subdomain.app", "sonar", "riddler", "jldc", "sublist3r", "country"
        )
    }
}
