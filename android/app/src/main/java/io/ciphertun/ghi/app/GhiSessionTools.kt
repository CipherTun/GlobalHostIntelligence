package io.ciphertun.ghi.app

import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge

fun GhiSession.analyzeTls(host: String): String = GhiMobileBridge.analyzeTls(host)
fun GhiSession.inspectDns(host: String): String = GhiMobileBridge.inspectDns(host)
fun GhiSession.searchCertificates(domain: String): String = GhiMobileBridge.searchCertificates(domain)
