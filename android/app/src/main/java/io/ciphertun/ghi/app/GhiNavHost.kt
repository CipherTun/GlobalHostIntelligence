package io.ciphertun.ghi.app

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import io.ciphertun.ghi.feature.discover.*
import io.ciphertun.ghi.feature.settings.SettingsScreen
import io.ciphertun.ghi.core.ui.navigation.GhiRoute

@Composable
fun GhiNavHost(navController: NavHostController = rememberNavController()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val session = remember(context) { GhiSession(context.applicationContext) }
    val liveResults by session.liveResults.collectAsState()
    val discoveredResults by session.discoveredResults.collectAsState()
    val status by session.status.collectAsState()
    val error by session.error.collectAsState()
    val elapsed by session.elapsedMs.collectAsState()

    NavHost(navController, startDestination = GhiRoute.DISCOVER) {
        composable(GhiRoute.DISCOVER) {
            DiscoverScreen(
                discoveredResults = discoveredResults,
                liveResults = liveResults,
                running = status == "RUNNING",
                status = status,
                error = error,
                elapsedMs = elapsed,
                enabledSources = session.enabledSources(),
                onStart = { q, m -> session.startDiscovery(q, m) },
                onStop = session::stopDiscovery,
                onAnalyze = session::analyze
            )
        }
        composable(GhiRoute.SUBDOMAINS) { SubdomainsScreen(session::discoverSubdomains) }
        composable(GhiRoute.IP_TOOLS) { IpToolsScreen(session::resolve, session::resolveIp) }
        composable(GhiRoute.RESPONSE) { ResponseScreen(session::analyze, session::checkResponse) }
        composable(GhiRoute.TLS) { TlsAnalyzerScreen(session::analyzeTls) }
        composable(GhiRoute.DNS) { DnsInspectorScreen(session::inspectDns) }
        composable(GhiRoute.CERTIFICATES) { CertificateSearchScreen(session::searchCertificates) }
        composable(GhiRoute.PAYLOADS) { PayloadGeneratorScreen() }
        composable(GhiRoute.EXPORT) { ExportScreen(liveResults, session::exportResults) }
        composable(GhiRoute.SOURCES) { DiscoverySourcesScreen(session.enabledSources(), session::saveSources) }
        composable(GhiRoute.SETTINGS) {
            SettingsScreen(
                session.discoveryLimit(), session.validationThreads(), session.sourceParallelism(),
                session.validationTimeout(), session.userAgent(), session.enabledSources(),
                session.animationsEnabled(), session.compactResults(),
                { limit, threads, parallel, timeout, agent, sources, animations, compact ->
                    session.saveSettings(limit, threads, parallel, timeout, agent, sources, animations, compact)
                }, session::resetSettings,
                onPrivacyOptions = { if (activity != null) GhiAdManager.showPrivacyOptions(activity) }
            )
        }
    }
}
