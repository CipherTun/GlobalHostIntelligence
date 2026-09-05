package io.ciphertun.ghi.app

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.ciphertun.ghi.R
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.LocalGhiOpenDrawer
import io.ciphertun.ghi.core.ui.navigation.GhiRoute
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val topLevel = listOf(
    NavItem(GhiRoute.DISCOVER, "Discover", Icons.Filled.Explore),
    NavItem(GhiRoute.IP_TOOLS, "IP / Domain", Icons.Filled.Dns),
    NavItem(GhiRoute.RESPONSE, "Response", Icons.Filled.Public),
    NavItem(GhiRoute.PAYLOADS, "Payloads", Icons.Filled.Bolt),
    NavItem(GhiRoute.EXPORT, "Export", Icons.Filled.FileDownload),
    NavItem(GhiRoute.SOURCES, "Sources", Icons.Filled.Source),
    NavItem(GhiRoute.SETTINGS, "Settings", Icons.Filled.Settings),
)
private val bottomLevel = topLevel.take(4)

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

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = GhiInk950,
                modifier = Modifier.fillMaxWidth(.86f)
            ) {
                Column(Modifier.fillMaxHeight()) {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GhiNavy700, GhiInk900))).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ghi_globe), "GHI globe", Modifier.size(52.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("GLOBAL HOST", fontWeight = FontWeight.ExtraBold)
                                Text("INTELLIGENCE", color = GhiAccentBlue, fontWeight = FontWeight.ExtraBold)
                                Text("TOOLS", color = GhiAccentCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).padding(10.dp)) {
                        item { Text("TOOLS", modifier = Modifier.padding(12.dp), color = GhiSlate500, style = MaterialTheme.typography.labelSmall) }
                        topLevel.forEach { item { DrawerItem(route, it, ::go) } }
                    }
                }
            }
        }
    ) {
        CompositionLocalProvider(LocalGhiOpenDrawer provides { scope.launch { drawer.open() } }) {
            Scaffold(
                containerColor = GhiInk950,
                bottomBar = {
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
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.radialGradient(listOf(GhiNavy700.copy(alpha = .28f), GhiInk950))))
                    GhiNavHost(navController)
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(route: String?, item: NavItem, go: (String) -> Unit) {
    NavigationDrawerItem(
        label = { Text(item.label) },
        selected = route == item.route,
        onClick = { go(item.route) },
        icon = { Icon(item.icon, null) },
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
