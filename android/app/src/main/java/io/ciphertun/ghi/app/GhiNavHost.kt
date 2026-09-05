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
    val session = remember(context) { GhiSession(context.applicationContext) }
    val results by session.liveResults.collectAsState()
    val status by session.status.collectAsState()
    val error by session.error.collectAsState()
    val elapsed by session.elapsedMs.collectAsState()

    NavHost(navController, startDestination = GhiRoute.DISCOVER) {
        composable(GhiRoute.DISCOVER) {
            DiscoverScreen(
                results = results,
                running = status == "RUNNING",
                status = status,
                error = error,
                elapsedMs = elapsed,
                enabledSources = session.enabledSources(),
                onStart = { query, scopeMode -> session.startDiscovery(query, scopeMode) },
                onStop = session::stopDiscovery
            )
        }
        composable(GhiRoute.IP_TOOLS) {
            IpToolsScreen(
                onResolveDomain = session::resolve,
                onResolveIp = session::resolveIp
            )
        }
        composable(GhiRoute.RESPONSE) {
            ResponseScreen(
                onAnalyze = session::analyze,
                onCheck = session::checkResponse
            )
        }
        composable(GhiRoute.PAYLOADS) {
            PayloadGeneratorScreen()
        }
        composable(GhiRoute.EXPORT) {
            ExportScreen(results = results, exportText = session::exportResults)
        }
        composable(GhiRoute.SOURCES) {
            DiscoverySourcesScreen(
                enabledSources = session.enabledSources(),
                onSave = session::saveSources
            )
        }
        composable(GhiRoute.SETTINGS) {
            SettingsScreen(
                discoveryLimit = session.discoveryLimit(),
                validationThreads = session.validationThreads(),
                sourceParallelism = session.sourceParallelism(),
                validationTimeout = session.validationTimeout(),
                userAgent = session.userAgent(),
                enabledSources = session.enabledSources(),
                animationsEnabled = session.animationsEnabled(),
                compactResults = session.compactResults(),
                onSave = { limit, threads, parallel, timeout, agent, sources, animations, compact ->
                    session.saveSettings(limit, threads, parallel, timeout, agent, sources, animations, compact)
                },
                onReset = session::resetSettings
            )
        }
    }
}
