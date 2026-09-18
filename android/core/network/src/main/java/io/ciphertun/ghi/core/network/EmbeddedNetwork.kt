package io.ciphertun.ghi.core.network

/**
 * Marker for the embedded-only networking architecture.
 * Discovery, DNS, HTTP and TLS operations are executed by the Go mobile
 * library in :core:crawlercore; this module intentionally has no VPS/API URL.
 */
object EmbeddedNetwork
