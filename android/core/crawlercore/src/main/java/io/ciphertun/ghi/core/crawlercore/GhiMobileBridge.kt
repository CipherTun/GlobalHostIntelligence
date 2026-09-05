package io.ciphertun.ghi.core.crawlercore

import org.json.JSONObject

/** Reflection boundary around the generated gomobile Mobile class. */
object GhiMobileBridge {
    private const val CLASS_NAME = "io.ciphertun.ghi.core.crawlercore.generated.mobile.Mobile"
    private val mobileClass: Class<*>? get() = runCatching { Class.forName(CLASS_NAME) }.getOrNull()

    private fun invoke(name: String, vararg args: Any): String {
        val cls = mobileClass ?: return JSONObject().put("ok", false).put("error", "Embedded GHI mobile library is not loaded").toString()
        return runCatching {
            val method = cls.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
                ?: error("Mobile.$name is not available in the embedded library")
            val adapted = method.parameterTypes.mapIndexed { index, type ->
                val value = args[index]
                if (value is Number) when (type) {
                    java.lang.Integer.TYPE -> value.toInt()
                    java.lang.Long.TYPE -> value.toLong()
                    java.lang.Short.TYPE -> value.toShort()
                    java.lang.Byte.TYPE -> value.toByte()
                    java.lang.Double.TYPE -> value.toDouble()
                    java.lang.Float.TYPE -> value.toFloat()
                    else -> value
                } else value
            }.toTypedArray()
            method.invoke(null, *adapted)?.toString().orEmpty()
        }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Library call failed").toString() }
    }

    fun ping(): String = invoke("ping")
    fun resolveDomain(fqdn: String): String = invoke("resolveDomain", fqdn)
    fun discover(query: String, maxResults: Int = 100): String = invoke("discover", query, maxResults)
    fun discoverSource(query: String, source: String, maxResults: Int = 100): String = invoke("discoverSource", query, source, maxResults)
    fun discoverCandidates(query: String, source: String, maxResults: Int = 100): String = invoke("discoverCandidates", query, source, maxResults)
    fun discoverCarrier(query: String, maxResults: Int = 100): String = invoke("discoverCarrier", query, maxResults)
    fun analyzeHost(host: String): String = invoke("analyzeHost", host)
    fun analyzeHostWithTimeout(host: String, timeoutSeconds: Int): String = invoke("analyzeHostWithTimeout", host, timeoutSeconds)
    fun analyzeHostWithOptions(host: String, timeoutSeconds: Int, userAgent: String): String = invoke("analyzeHostWithOptions", host, timeoutSeconds, userAgent)
    fun checkHost(host: String, method: String = "GET", allowInsecure: Boolean = false, followRedirects: Boolean = true): String = invoke("checkHost", host, method, allowInsecure, followRedirects)
    fun checkResponse(mode: String, targets: String, proxy: String, method: String, path: String, headers: String, body: String, followRedirects: Boolean, allowInsecure: Boolean, timeoutSeconds: Int, payloadMode: Boolean, dnsTransport: String, resolver: String, authoritative: String): String = invoke("checkResponse", mode, targets, proxy, method, path, headers, body, followRedirects, allowInsecure, timeoutSeconds, payloadMode, dnsTransport, resolver, authoritative)
    fun resolveIp(value: String): String = invoke("resolveIP", value)
    fun generateRequest(method: String, host: String, path: String, body: String): String = invoke("generateRequest", method, host, path, body)
    fun generateNetworkRequest(network: String, method: String, host: String, path: String, body: String): String = invoke("generateNetworkRequest", network, method, host, path, body)
}
