package io.ciphertun.ghi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ciphertun.ghi.core.crawlercore.GhiMobileBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _corePingResult = MutableStateFlow<String?>(null)
    val corePingResult: StateFlow<String?> = _corePingResult.asStateFlow()

    private val _dnsTestResult = MutableStateFlow<String?>(null)
    val dnsTestResult: StateFlow<String?> = _dnsTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            _corePingResult.value = withContext(Dispatchers.Default) {
                runCatching { GhiMobileBridge.ping() }
                    .getOrElse { "core library unavailable: ${it.message ?: "unknown error"}" }
            }
        }
    }

    fun runDnsTest(fqdn: String) {
        viewModelScope.launch {
            if (fqdn.isBlank()) { _dnsTestResult.value = "Enter a hostname"; return@launch }
            _dnsTestResult.value = "Resolving $fqdn…"
            _dnsTestResult.value = withContext(Dispatchers.IO) {
                runCatching { GhiMobileBridge.resolveDomain(fqdn) }
                    .getOrElse { "error: ${it.message ?: "lookup failed"}" }
            }
        }
    }
}
