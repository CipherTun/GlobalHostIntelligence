package io.ciphertun.ghi.app

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.ciphertun.ghi.R
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.LocalGhiOpenDrawer
import io.ciphertun.ghi.core.ui.navigation.GhiRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val toolLevel = listOf(
    NavItem(GhiRoute.DISCOVER, "Discovery", Icons.Filled.Explore),
    NavItem(GhiRoute.SUBDOMAINS, "Subdomains", Icons.Filled.Dns),
    NavItem(GhiRoute.RESPONSE, "Response Checker", Icons.Filled.Language),
    NavItem(GhiRoute.IP_TOOLS, "IP / Domain", Icons.Filled.Public),
    NavItem(GhiRoute.PAYLOADS, "Payload Generator", Icons.Filled.Bolt),
    NavItem(GhiRoute.SETTINGS, "Settings", Icons.Filled.Settings)
)

private val bottomLevel = listOf(
    GhiRoute.DISCOVER,
    GhiRoute.SUBDOMAINS,
    GhiRoute.RESPONSE,
    GhiRoute.IP_TOOLS
).mapNotNull { route -> toolLevel.firstOrNull { it.route == route } }

@Composable
private fun GhiAutomaticAdHost(route: String?) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showPrompt by remember { mutableStateOf(false) }
    val currentRoute by rememberUpdatedState(route)

    LaunchedEffect(Unit) {
        GhiAdManager.preload(context)
        while (true) {
            delay(1_000L)
            if (currentRoute != GhiRoute.SETTINGS && activity != null && !showPrompt && GhiAdManager.shouldOffer()) {
                showPrompt = true
            }
        }
    }

    if (showPrompt && activity != null) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("Sponsored message") },
            text = {
                Text(
                    "Watch a short sponsored video to receive 60 seconds without another automatic ad. " +
                        "You can choose Not now."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrompt = false
                    GhiAdManager.show(activity)
                }) { Text("Watch") }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) { Text("Not now") }
            }
        )
    }
}

@Composable
fun GhiAppChrome(navController: NavHostController = rememberNavController()) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route

    fun go(target: String) {
        scope.launch { drawer.close() }
        navController.navigate(target) {
            launchSingleTop = true
            restoreState = true
            popUpTo(GhiRoute.DISCOVER) { saveState = true }
        }
    }

    GhiAutomaticAdHost(route)

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = GhiInk950,
                modifier = Modifier.fillMaxWidth(.88f)
            ) {
                Column(Modifier.fillMaxHeight()) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(GhiNavy700, GhiInk900)))
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ghi_globe), "Global Host Intelligence", Modifier.size(52.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("GLOBAL HOST", fontWeight = FontWeight.ExtraBold)
                                Text("INTELLIGENCE", color = GhiAccentBlue, fontWeight = FontWeight.ExtraBold)
                                Text("NETWORK INTELLIGENCE", color = GhiAccentCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).padding(10.dp)) {
                        item {
                            Text("TOOLS", Modifier.padding(12.dp), color = GhiSlate500, style = MaterialTheme.typography.labelSmall)
                        }
                        toolLevel.forEach { navItem ->
                            item {
                                NavigationDrawerItem(
                                    label = { Text(navItem.label) },
                                    selected = route == navItem.route,
                                    onClick = { go(navItem.route) },
                                    icon = { Icon(navItem.icon, null) },
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        CompositionLocalProvider(LocalGhiOpenDrawer provides { scope.launch { drawer.open() } }) {
            Scaffold(
                containerColor = GhiInk950,
                bottomBar = {
                    Column {
                        if (route != GhiRoute.SETTINGS) {
                            GhiAdBanner()
                        }
                        NavigationBar(containerColor = GhiInk900, tonalElevation = 0.dp) {
                            bottomLevel.forEach { item ->
                                NavigationBarItem(
                                    selected = route == item.route,
                                    onClick = { go(item.route) },
                                    icon = { Icon(item.icon, item.label) },
                                    label = { Text(item.label, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp)
                            .background(Brush.radialGradient(listOf(GhiNavy700.copy(alpha = .30f), GhiInk950)))
                    )
                    GhiNavHost(navController)
                }
            }
        }
    }
}
