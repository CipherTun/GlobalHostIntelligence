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
    // Process-wide singleton: discovery survives rotation and theme changes.
    val session = remember(context) { GhiSession.get(context.applicationContext) }
    val liveResults by session.liveResults.collectAsState()
    val discoveredResults by session.discoveredResults.collectAsState()
    val status by session.status.collectAsState()
    val error by session.error.collectAsState()
    val elapsed by session.elapsedMs.collectAsState()
    val progress by session.progress.collectAsState()
    val engine by session.engine.collectAsState()
    val style by session.style.collectAsState()

    NavHost(navController, startDestination = GhiRoute.DISCOVER) {
        composable(GhiRoute.DISCOVER) {
            DiscoverScreen(
                discoveredResults = discoveredResults,
                liveResults = liveResults,
                running = status == "RUNNING",
                status = status,
                error = error,
                elapsedMs = elapsed,
                enabledSources = engine.enabledSources,
                onStart = { q, m -> session.startDiscovery(q, m) },
                onStop = session::stopDiscovery,
                onAnalyze = session::analyze,
                progress = progress,
                acceptAnyResponse = engine.acceptAnyResponse
            )
        }
        composable(GhiRoute.SUBDOMAINS) { SubdomainsScreen(session::discoverSubdomains) }
        composable(GhiRoute.IP_TOOLS) { IpToolsScreen(session::resolve, session::resolveIp) }
        composable(GhiRoute.RESPONSE) { ResponseScreen(session::analyze, session::checkResponse) }
        composable(GhiRoute.TLS) { TlsAnalyzerScreen(session::analyzeTls) }
        composable(GhiRoute.DNS) { DnsInspectorScreen(session::inspectDns) }
        composable(GhiRoute.CERTIFICATES) { CertificateSearchScreen(session::searchCertificates) }
        composable(GhiRoute.RDAP) { RdapScreen(session::rdap) }
        composable(GhiRoute.PAYLOADS) { PayloadGeneratorScreen() }
        composable(GhiRoute.EXPORT) { ExportScreen(liveResults, session::exportResults) }
        composable(GhiRoute.INVESTIGATION) { GhiInvestigationScreen(session::investigate) }
        composable(GhiRoute.WEB_SURFACE) { GhiWebSurfaceScreen(session::inspectWebSurface) }
        composable(GhiRoute.AGENT) { GhiAgentScreen(session::ghiAgent) }
        composable(GhiRoute.SOURCES) { DiscoverySourcesScreen(session.enabledSources(), session::saveSources) }
        composable(GhiRoute.SETTINGS) {
            SettingsScreen(
                engine = engine,
                style = style,
                onEngineSave = session::saveEngine,
                onStyleChange = session::setStyle,
                onResetEngine = session::resetSettings,
                onPrivacyOptions = { if (activity != null) GhiAdManager.showPrivacyOptions(activity) }
            )
        }
    }
}
