package io.ciphertun.ghi.app

import android.content.Context
import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge
import io.ciphertun.ghi.core.designsystem.GhiAccent
import io.ciphertun.ghi.core.designsystem.GhiCorners
import io.ciphertun.ghi.core.designsystem.GhiDensity
import io.ciphertun.ghi.core.designsystem.GhiStyle
import io.ciphertun.ghi.core.designsystem.GhiThemeMode
import io.ciphertun.ghi.feature.discover.DiscoveryProgress
import io.ciphertun.ghi.feature.discover.DomainPing
import io.ciphertun.ghi.feature.settings.GhiEngineSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide session. It used to be created inside the NavHost with
 * `remember`, so a rotation or theme change orphaned the running discovery job
 * and threw its results away. It is now a singleton: use [GhiSession.get].
 */
class GhiSession(context: Context) {
    private val prefs = context.getSharedPreferences("ghi_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _liveResults = MutableStateFlow<List<DomainPing>>(emptyList())
    val liveResults: StateFlow<List<DomainPing>> = _liveResults.asStateFlow()
    private val _discoveredResults = MutableStateFlow<List<DomainPing>>(emptyList())
    val discoveredResults: StateFlow<List<DomainPing>> = _discoveredResults.asStateFlow()
    private val _status = MutableStateFlow("READY")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private val _progress = MutableStateFlow(DiscoveryProgress())
    val progress: StateFlow<DiscoveryProgress> = _progress.asStateFlow()
    private var discoveryJob: Job? = null

    private val _style = MutableStateFlow(loadStyle())
    val style: StateFlow<GhiStyle> = _style.asStateFlow()
    private val _engine = MutableStateFlow(engineSettings())
    val engine: StateFlow<GhiEngineSettings> = _engine.asStateFlow()

    init {
        migrateSecrets()
    }

    // ------------------------------------------------------------------ discovery

    fun startDiscovery(query: String, scopeMode: String = "country", maxResults: Int = discoveryLimit()): String {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return ""
        val limit = maxResults.coerceIn(10, 5000)
        val cfg = engineSettings()
        discoveryJob?.cancel()
        _status.value = "RUNNING"
        _error.value = null
        _elapsedMs.value = 0L
        _liveResults.value = emptyList()
        _discoveredResults.value = emptyList()
        _progress.value = DiscoveryProgress(phase = "sources")

        discoveryJob = scope.launch {
            val started = System.currentTimeMillis()
            try {
                runDiscovery(normalized, scopeMode, limit, cfg)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                _error.value = t.message ?: "Discovery failed"
                _status.value = "FAILED"
            } finally {
                _elapsedMs.value = System.currentTimeMillis() - started
            }
        }
        return normalized
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runDiscovery(normalized: String, scopeMode: String, limit: Int, cfg: GhiEngineSettings) = coroutineScope {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val failures = ConcurrentHashMap.newKeySet<String>()
        val sourcesDone = AtomicInteger(0)
        val enabled = cfg.enabledSources

        fun publishDiscovered() {
            _discoveredResults.value = seen.toList().sorted().take(limit * 4).map { DomainPing(it, 0L, -1, false) }
        }

        val sources = if (scopeMode.equals("domain", true)) {
            enabled.filterNot { it == "country" || it == "urlscan-country" }
        } else {
            buildList {
                if (enabled.contains("country")) add("country-world")
                if (enabled.contains("urlscan-country")) add("urlscan-country")
                if (enabled.contains("urlscan") && !contains("urlscan-country")) add("urlscan")
            }.distinct()
        }
        if (sources.isEmpty()) {
            _error.value = "Enable at least one discovery source in Settings."
            _status.value = "FAILED"
            _progress.value = DiscoveryProgress(phase = "done")
            return@coroutineScope
        }
        _progress.update { it.copy(sourcesTotal = sources.size) }

        // ---- phase 1: passive sources, bounded by the source-parallelism setting
        val sourceGate = Semaphore(cfg.sourceParallelism.coerceIn(1, 32))
        sources.map { source ->
            async(Dispatchers.IO) {
                sourceGate.acquire()
                try {
                    runCatching {
                        val raw = when (source) {
                            "country-world" -> GhiMobileBridge.discoverCountryWorld(normalized, limit, providerConfigJson())
                            "urlscan-country" -> GhiMobileBridge.discoverRawSource(normalized, source, limit)
                            "projectdiscovery" -> GhiMobileBridge.discoverProjectDiscovery(normalized, cfg.projectDiscoveryApiKey, (limit * 2).coerceAtMost(1000))
                            else -> GhiMobileBridge.discoverRawSource(normalized, source, (limit * 2).coerceAtMost(1000))
                        }
                        val obj = JSONObject(raw)
                        var added = false
                        for (domain in extractDomains(obj)) if (seen.add(domain)) added = true
                        obj.optJSONObject("errors")?.let { errors -> errors.keys().forEach { failures.add(it) } }
                        if (obj.optString("error").isNotBlank()) failures.add(source)
                        if (added) withContext(Dispatchers.Main.immediate) { publishDiscovered() }
                    }.onFailure {
                        if (it is CancellationException) throw it
                        failures.add(source)
                    }
                } finally {
                    sourceGate.release()
                    val done = sourcesDone.incrementAndGet()
                    _progress.update { p -> p.copy(sourcesDone = done, candidates = seen.size, failedSources = failures.toList().sorted()) }
                }
            }
        }.awaitAll()

        // ---- phase 2: validate candidates with a bounded, adaptive worker pool
        val candidates = seen.toList().take(limit * 4)
        _progress.update { it.copy(phase = "validating", candidates = candidates.size, failedSources = failures.toList().sorted()) }
        val accepted = ConcurrentHashMap<String, DomainPing>()
        if (candidates.isNotEmpty()) {
            val workerCount = minOf(cfg.validationThreads, candidates.size).coerceAtLeast(1)
            val gate = AdaptiveGate(workerCount, cfg.adaptiveConcurrency)
            // Plain Dispatchers.IO caps at 64 threads and would starve the source fetches.
            val dispatcher = Dispatchers.IO.limitedParallelism(workerCount)
            val queue = Channel<String>(workerCount)
            val checked = AtomicInteger(0)
            val workers = List(workerCount) {
                launch(dispatcher) {
                    for (candidate in queue) {
                        if (!isActive || accepted.size >= limit) continue
                        gate.acquire()
                        try {
                            validate(candidate, cfg, gate)?.let { accepted.putIfAbsent(candidate, it) }
                        } finally {
                            gate.release()
                            checked.incrementAndGet()
                        }
                    }
                }
            }
            val publisher = launch(Dispatchers.Main.immediate) {
                var lastLive = -1
                while (isActive && workers.any { it.isActive }) {
                    if (accepted.size != lastLive) {
                        lastLive = accepted.size
                        _liveResults.value = accepted.values.sortedBy { it.domain }.take(limit)
                    }
                    _progress.update { it.copy(validated = checked.get(), live = accepted.size, concurrency = gate.current) }
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
            _progress.update { it.copy(validated = checked.get(), live = accepted.size, concurrency = gate.current) }
        }

        if (isActive) {
            val live = _liveResults.value.size
            _status.value = when {
                live >= limit -> "COMPLETED"
                failures.isNotEmpty() -> "PARTIAL"
                else -> "COMPLETED"
            }
            _progress.update { it.copy(phase = "done") }
        }
    }

    /** Blocking probe of one host with optional retries for timeouts. */
    private fun validate(host: String, cfg: GhiEngineSettings, gate: AdaptiveGate): DomainPing? {
        var attempt = 0
        while (true) {
            val analyzed = runCatching {
                JSONObject(GhiMobileBridge.analyzeHostWithOptions(host, cfg.validationTimeout, cfg.userAgent))
            }.getOrNull()
            val elapsed = analyzed?.optLong("elapsed_ms", 0L) ?: 0L
            val timedOut = analyzed == null || elapsed >= cfg.validationTimeout * 900L
            val code = acceptedCode(
                analyzed?.optInt("https_status", -1) ?: -1,
                analyzed?.optInt("http_status", -1) ?: -1,
                cfg.acceptAnyResponse
            )
            if (code > 0) {
                gate.record(false)
                return DomainPing(host, elapsed, code, true)
            }
            if (timedOut && attempt < cfg.validationRetries) {
                attempt++
                continue
            }
            gate.record(timedOut)
            return null
        }
    }

    private fun acceptedCode(https: Int, http: Int, any: Boolean): Int = when {
        https in 200..399 -> https
        http in 200..399 -> http
        any && https > 0 -> https
        any && http > 0 -> http
        else -> -1
    }

    private fun extractDomains(obj: JSONObject): List<String> {
        val arr = obj.optJSONArray("domains") ?: obj.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val raw = if (item is JSONObject) item.optString("domain") else item?.toString().orEmpty()
            normalizeHost(raw)?.let { out.add(it) }
        }
        return out
    }

    /** Strips scheme/path/trailing dot and the `*.` of certificate wildcard names. */
    private fun normalizeHost(raw: String): String? =
        raw.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .substringBefore('/').trimEnd('.').removePrefix("*.")
            .takeIf { it.isNotBlank() && it.contains('.') && !it.contains(':') && !it.contains(' ') && !it.contains('*') }

    fun stopDiscovery() {
        discoveryJob?.cancel(); discoveryJob = null
        if (_status.value == "RUNNING") _status.value = "STOPPED"
        _progress.update { it.copy(phase = "stopped") }
    }

    // ------------------------------------------------------------------ tools

    fun analyze(host: String): String = GhiMobileBridge.analyzeHostWithOptions(host, validationTimeout(), userAgent())
    fun checkResponse(mode: String, targets: String, proxy: String, method: String, path: String, headers: String, body: String, followRedirects: Boolean, allowInsecure: Boolean, timeoutSeconds: Int, payloadMode: Boolean, dnsTransport: String, resolver: String, authoritative: String): String =
        GhiMobileBridge.checkResponse(mode, targets, proxy, method, path, headers, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode, dnsTransport, resolver, authoritative)
    fun resolve(host: String): String = GhiMobileBridge.resolveDomain(host)
    fun resolveIp(value: String): String = GhiMobileBridge.resolveIp(value)
    fun rdap(target: String): String = GhiMobileBridge.rdapLookup(target)
    fun discoverSubdomains(domain: String, maxResults: Int = 500): String = GhiMobileBridge.discoverSubdomains(domain, maxResults)

    fun saveSources(sources: Set<String>) {
        prefs.edit().putStringSet("enabled_sources", sources).apply()
        _engine.value = engineSettings()
    }

    fun exportResults(format: String): String {
        val current = _liveResults.value
        return when (format.lowercase()) {
            "csv" -> buildString { appendLine("domain,status,latency_ms"); current.forEach { appendLine("${it.domain},${it.status},${it.latencyMs}") } }
            "json" -> JSONArray(current.map { JSONObject().apply { put("domain", it.domain); put("status", it.status); put("latency_ms", it.latencyMs); put("live", it.live) } }).toString(2)
            else -> current.joinToString("\n") { it.domain }
        }
    }

    // ------------------------------------------------------------------ settings

    fun discoveryLimit() = prefs.getInt("discovery_limit", 500).coerceIn(10, 5000)
    fun validationThreads() = prefs.getInt("validation_threads", 32).coerceIn(1, 256)
    fun sourceParallelism() = prefs.getInt("source_parallelism", 8).coerceIn(1, 32)
    fun validationTimeout() = prefs.getInt("validation_timeout", 8).coerceIn(2, 60)
    fun validationRetries() = prefs.getInt("validation_retries", 0).coerceIn(0, 2)
    fun acceptAnyResponse() = prefs.getBoolean("accept_any_response", false)
    fun adaptiveConcurrency() = prefs.getBoolean("adaptive_concurrency", true)
    fun userAgent(): String {
        val saved = prefs.getString("user_agent", "")?.trim().orEmpty()
        return if (saved.isBlank() || saved.startsWith("GlobalHostIntelligence/")) {
            DEFAULT_BROWSER_USER_AGENT
        } else saved
    }
    fun projectDiscoveryApiKey() = readSecret("project_discovery_api_key")
    fun animationsEnabled() = prefs.getBoolean("animations", true)
    fun compactResults() = prefs.getBoolean("compact_results", false)
    fun enabledSources(): Set<String> = prefs.getStringSet("enabled_sources", DEFAULT_SOURCES)?.toSet() ?: DEFAULT_SOURCES

    fun engineSettings() = GhiEngineSettings(
        discoveryLimit = discoveryLimit(),
        validationThreads = validationThreads(),
        sourceParallelism = sourceParallelism(),
        validationTimeout = validationTimeout(),
        validationRetries = validationRetries(),
        acceptAnyResponse = acceptAnyResponse(),
        adaptiveConcurrency = adaptiveConcurrency(),
        userAgent = userAgent(),
        projectDiscoveryApiKey = projectDiscoveryApiKey(),
        enabledSources = enabledSources(),
    )

    fun providerConfigJson(): String = JSONObject().apply {
        put("Censys", readSecret("provider_censys"))
        put("Netlas", readSecret("provider_netlas"))
        put("Shodan", readSecret("provider_shodan"))
        put("ProjectDiscovery", projectDiscoveryApiKey())
    }.toString()

    fun saveEngine(s: GhiEngineSettings) {
        prefs.edit()
            .putInt("discovery_limit", s.discoveryLimit.coerceIn(10, 5000))
            .putInt("validation_threads", s.validationThreads.coerceIn(1, 256))
            .putInt("source_parallelism", s.sourceParallelism.coerceIn(1, 32))
            .putInt("validation_timeout", s.validationTimeout.coerceIn(2, 60))
            .putInt("validation_retries", s.validationRetries.coerceIn(0, 2))
            .putBoolean("accept_any_response", s.acceptAnyResponse)
            .putBoolean("adaptive_concurrency", s.adaptiveConcurrency)
            .putString("user_agent", s.userAgent.trim().let {
                if (it.isBlank() || it.startsWith("GlobalHostIntelligence/")) DEFAULT_BROWSER_USER_AGENT else it
            })
            .putStringSet("enabled_sources", s.enabledSources)
            .putSecret("project_discovery_api_key", s.projectDiscoveryApiKey.trim())
            .apply()
        _engine.value = engineSettings()
    }

    /** Legacy entry point kept for source compatibility. */
    fun saveSettings(limit: Int, threads: Int, parallel: Int, timeout: Int, agent: String, sources: Set<String>, animations: Boolean, compact: Boolean, projectDiscoveryKey: String = "") {
        saveEngine(
            _engine.value.copy(
                discoveryLimit = limit, validationThreads = threads, sourceParallelism = parallel,
                validationTimeout = timeout, userAgent = agent, enabledSources = sources,
                projectDiscoveryApiKey = projectDiscoveryKey,
            )
        )
        setStyle(_style.value.copy(animations = animations, compactResults = compact))
    }

    fun resetSettings() {
        // Reset only engine settings. Provider credentials, appearance and other data stay.
        prefs.edit()
            .remove("discovery_limit").remove("validation_threads").remove("source_parallelism")
            .remove("validation_timeout").remove("validation_retries").remove("accept_any_response")
            .remove("adaptive_concurrency").remove("user_agent").remove("enabled_sources")
            .remove("project_discovery_api_key")
            .apply()
        _engine.value = engineSettings()
    }

    // ------------------------------------------------------------------ appearance

    fun setStyle(style: GhiStyle) {
        prefs.edit().apply {
            putString("style_mode", style.mode.name)
            putString("style_accent", style.accent.name)
            if (style.customAccentArgb != null) putInt("style_custom_accent", style.customAccentArgb!!) else remove("style_custom_accent")
            putFloat("style_glass", style.glass.coerceIn(.35f, .95f))
            putString("style_corners", style.corners.name)
            putString("style_density", style.density.name)
            putBoolean("animations", style.animations)
            putBoolean("style_backdrop", style.backdrop)
            putBoolean("compact_results", style.compactResults)
        }.apply()
        _style.value = style
    }

    private fun loadStyle(): GhiStyle = GhiStyle(
        mode = enumPref("style_mode", GhiThemeMode.DARK),
        accent = enumPref("style_accent", GhiAccent.OCEAN),
        customAccentArgb = if (prefs.contains("style_custom_accent")) prefs.getInt("style_custom_accent", 0) else null,
        glass = prefs.getFloat("style_glass", .72f).coerceIn(.35f, .95f),
        corners = enumPref("style_corners", GhiCorners.SOFT),
        density = enumPref("style_density", GhiDensity.COMFORT),
        animations = prefs.getBoolean("animations", true),
        backdrop = prefs.getBoolean("style_backdrop", true),
        compactResults = prefs.getBoolean("compact_results", false),
    )

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E {
        val name = prefs.getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    // ------------------------------------------------------------------ secrets

    private fun readSecret(key: String): String = SecretStore.decrypt(prefs.getString(key, "") ?: "")

    private fun android.content.SharedPreferences.Editor.putSecret(key: String, value: String) =
        putString(key, SecretStore.encrypt(value))

    /** Re-writes API keys stored as plain text by older versions in encrypted form. */
    private fun migrateSecrets() {
        val editor = prefs.edit()
        var dirty = false
        for (key in SECRET_KEYS) {
            val raw = prefs.getString(key, null)
            if (!raw.isNullOrEmpty() && !SecretStore.isEncrypted(raw)) {
                val enc = SecretStore.encrypt(raw)
                if (enc != raw) {
                    editor.putString(key, enc)
                    dirty = true
                }
            }
        }
        if (dirty) editor.apply()
    }

    /**
     * Limits concurrent probes and backs off when many of them time out
     * (congested or throttled network), then recovers as timeouts fade.
     */
    private class AdaptiveGate(private val max: Int, private val adaptive: Boolean) {
        private val permits = Semaphore(max)
        private val lock = Any()
        private var held = 0
        private var window = 0
        private var bad = 0

        val current: Int get() = synchronized(lock) { max - held }

        suspend fun acquire() = permits.acquire()
        fun release() = permits.release()

        fun record(timedOut: Boolean) {
            if (!adaptive) return
            synchronized(lock) {
                window++
                if (timedOut) bad++
                if (window < 32) return
                val ratio = bad.toDouble() / window
                if (ratio > .6) {
                    val target = (max - held) / 2
                    var removed = 0
                    while (removed < target && max - held > 4 && permits.tryAcquire()) {
                        held++
                        removed++
                    }
                } else if (ratio < .25) {
                    var restored = 0
                    while (restored < 4 && held > 0) {
                        permits.release()
                        held--
                        restored++
                    }
                }
                window = 0
                bad = 0
            }
        }
    }

    companion object {
        const val DEFAULT_BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36"
        val DEFAULT_SOURCES = linkedSetOf("urlscan","crt.sh","crt.name","ctlogs.dev","certspotter","rapiddns","anubis","subdomain.center","hackertarget","wayback","threatminer","commoncrawl","otx","subdomain.app","sonar","riddler","jldc","sublist3r","country","urlscan-country")
        private val SECRET_KEYS = listOf("project_discovery_api_key", "provider_censys", "provider_netlas", "provider_shodan")

        @Volatile private var instance: GhiSession? = null

        fun get(context: Context): GhiSession =
            instance ?: synchronized(this) {
                instance ?: GhiSession(context.applicationContext).also { instance = it }
            }
    }
}
