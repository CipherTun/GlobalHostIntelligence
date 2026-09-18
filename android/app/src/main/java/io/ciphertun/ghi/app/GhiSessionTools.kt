package io.ciphertun.ghi.app

import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge

fun GhiSession.analyzeTls(host: String): String = GhiMobileBridge.analyzeTls(host)
fun GhiSession.inspectDns(host: String): String = GhiMobileBridge.inspectDns(host)
fun GhiSession.searchCertificates(domain: String): String = GhiMobileBridge.searchCertificates(domain)

fun GhiSession.investigate(target: String): String =
    GhiMobileBridge.investigate(target, validationTimeout())

fun GhiSession.securityHeaders(target: String): String =
    GhiMobileBridge.securityHeaders(target, validationTimeout())

fun GhiSession.redirectMap(target: String): String =
    GhiMobileBridge.redirectMap(target, validationTimeout())

fun GhiSession.networkTiming(target: String): String =
    GhiMobileBridge.networkTiming(target, validationTimeout())

fun GhiSession.technologyFingerprint(target: String): String =
    GhiMobileBridge.technologyFingerprint(target, validationTimeout())

fun GhiSession.internetSearch(query: String): String =
    GhiMobileBridge.internetSearch(query, 10)

fun GhiSession.ghiAgent(query: String): String =
    GhiMobileBridge.ghiAgent(query)

fun GhiSession.discoverProjectDiscovery(domain: String, apiKey: String, maxResults: Int = 500): String = GhiMobileBridge.discoverProjectDiscovery(domain, apiKey, maxResults)
