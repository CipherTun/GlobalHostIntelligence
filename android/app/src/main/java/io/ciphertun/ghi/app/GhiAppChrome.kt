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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.ciphertun.ghi.R
import io.ciphertun.ghi.core.designsystem.LocalGhiStyle
import io.ciphertun.ghi.core.ui.components.GhiBackdrop
import io.ciphertun.ghi.core.ui.components.LocalGhiOpenDrawer
import io.ciphertun.ghi.core.ui.navigation.GhiRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private data class NavGroup(val title: String, val items: List<NavItem>)

/**
 * The drawer used to omit TLS, DNS, Certificates, Export and Sources even
 * though their routes existed, which left those tools unreachable.
 */
private val navGroups = listOf(
    NavGroup(
        "DISCOVER",
        listOf(
            NavItem(GhiRoute.DISCOVER, "Discovery", Icons.Filled.Explore),
            NavItem(GhiRoute.SUBDOMAINS, "Subdomains", Icons.Filled.Dns),
            NavItem(GhiRoute.SOURCES, "Sources", Icons.Filled.Tune),
        )
    ),
    NavGroup(
        "PROBE",
        listOf(
            NavItem(GhiRoute.RESPONSE, "Response Checker", Icons.Filled.Language),
            NavItem(GhiRoute.PAYLOADS, "Payload Generator", Icons.Filled.Bolt),
        )
    ),
    NavGroup(
        "ANALYZE",
        listOf(
            NavItem(GhiRoute.IP_TOOLS, "IP / Domain", Icons.Filled.Public),
            NavItem(GhiRoute.RDAP, "Registration (RDAP)", Icons.Filled.Badge),
            NavItem(GhiRoute.TLS, "TLS Analyzer", Icons.Filled.Lock),
            NavItem(GhiRoute.DNS, "DNS Inspector", Icons.Filled.Storage),
            NavItem(GhiRoute.CERTIFICATES, "Certificates", Icons.Filled.VerifiedUser),
            NavItem(GhiRoute.WEB_SURFACE, "Web Surface", Icons.Filled.Web),
            NavItem(GhiRoute.INVESTIGATION, "Investigation", Icons.Filled.Security),
        )
    ),
    NavGroup(
        "ASSIST",
        listOf(NavItem(GhiRoute.AGENT, "GHI Agent", Icons.Filled.AutoAwesome))
    ),
    NavGroup(
        "DATA",
        listOf(
            NavItem(GhiRoute.EXPORT, "Export", Icons.Filled.FileDownload),
            NavItem(GhiRoute.SETTINGS, "Settings", Icons.Filled.Settings),
        )
    ),
)

private val bottomLevel = listOf(
    NavItem(GhiRoute.DISCOVER, "Discovery", Icons.Filled.Explore),
    NavItem(GhiRoute.SUBDOMAINS, "Subdomains", Icons.Filled.Dns),
    NavItem(GhiRoute.RESPONSE, "Response Checker", Icons.Filled.Language),
    NavItem(GhiRoute.IP_TOOLS, "IP / Domain", Icons.Filled.Public),
)

@Composable
fun GhiAppChrome(
    navController: NavHostController =
        rememberNavController()
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val style = LocalGhiStyle.current

    val current by
        navController.currentBackStackEntryAsState()

    val route = current?.destination?.route

    val context = LocalContext.current
    val activity = context as? Activity

    val adsReady by
        GhiAdManager.ready.collectAsState()

    var previousRoute by
        remember { mutableStateOf<String?>(null) }

    /*
     * Native Advanced is a layout ad, so it is not mixed into the
     * fullscreen transition pool. Instead, eligible screens randomly
     * receive it while the existing banner remains independent.
     */
    val showNativeAd = remember(route, adsReady) {
        route != null &&
            route != GhiRoute.SETTINGS &&
            adsReady &&
            Random.nextInt(100) < 35
    }

    LaunchedEffect(
        route,
        adsReady,
        activity
    ) {
        val oldRoute = previousRoute
        previousRoute = route

        if (oldRoute == null) {
            return@LaunchedEffect
        }

        if (route == null) {
            return@LaunchedEffect
        }

        if (oldRoute == route) {
            return@LaunchedEffect
        }

        if (route == GhiRoute.SETTINGS) {
            return@LaunchedEffect
        }

        if (!adsReady) {
            return@LaunchedEffect
        }

        val currentActivity = activity
            ?: return@LaunchedEffect

        delay(700L)

        GhiAdManager.showIfReady(
            currentActivity
        )
    }

    fun go(target: String) {
        scope.launch {
            drawer.close()
        }

        navController.navigate(target) {
            launchSingleTop = true
            restoreState = true

            popUpTo(
                GhiRoute.DISCOVER
            ) {
                saveState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = scheme.background,
                modifier = Modifier.fillMaxWidth(.88f)
            ) {
                Column(
                    Modifier.fillMaxHeight()
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        scheme.primary.copy(alpha = .30f),
                                        scheme.surface
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Image(
                                painter =
                                    painterResource(
                                        R.drawable.ghi_globe
                                    ),
                                contentDescription =
                                    "Global Host Intelligence",
                                modifier =
                                    Modifier.size(52.dp)
                            )

                            Spacer(
                                Modifier.width(12.dp)
                            )

                            Column {
                                Text(
                                    "GLOBAL HOST",
                                    fontWeight =
                                        FontWeight.ExtraBold
                                )

                                Text(
                                    "INTELLIGENCE",
                                    color = scheme.primary,
                                    fontWeight =
                                        FontWeight.ExtraBold
                                )

                                Text(
                                    "NETWORK INTELLIGENCE",
                                    color = scheme.secondary,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall
                                )
                            }
                        }
                    }

                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        navGroups.forEach { group ->
                            item {
                                Text(
                                    group.title,
                                    Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
                                    color = scheme.outline,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall
                                )
                            }
                            group.items.forEach { navItem ->
                                item {
                                    NavigationDrawerItem(
                                        label = {
                                            Text(navItem.label)
                                        },
                                        selected =
                                            route ==
                                                navItem.route,
                                        onClick = {
                                            go(navItem.route)
                                        },
                                        icon = {
                                            Icon(
                                                navItem.icon,
                                                null
                                            )
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = scheme.primary.copy(alpha = .18f),
                                            selectedIconColor = scheme.primary,
                                            selectedTextColor = scheme.primary,
                                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                        ),
                                        modifier =
                                            Modifier.padding(
                                                vertical = 1.dp
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        CompositionLocalProvider(
            LocalGhiOpenDrawer provides {
                scope.launch {
                    drawer.open()
                }
            }
        ) {
            Scaffold(
                containerColor = scheme.background,

                bottomBar = {
                    Column {
                        if (
                            showNativeAd &&
                            route != GhiRoute.SETTINGS
                        ) {
                            GhiNativeAd(
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                )
                            )
                        }

                        if (
                            route != GhiRoute.SETTINGS &&
                            adsReady
                        ) {
                            GhiAdBanner()
                        }

                        NavigationBar(
                            containerColor = scheme.surface.copy(alpha = (style.glass + .2f).coerceAtMost(.97f)),
                            tonalElevation = 0.dp
                        ) {
                            bottomLevel.forEach { item ->
                                NavigationBarItem(
                                    selected =
                                        route ==
                                            item.route,
                                    onClick = {
                                        go(item.route)
                                    },
                                    icon = {
                                        Icon(
                                            item.icon,
                                            item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = scheme.primary,
                                        selectedTextColor = scheme.primary,
                                        indicatorColor = scheme.primary.copy(alpha = .22f)
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                // Every screen scaffold is transparent, so this backdrop is what you see.
                GhiBackdrop(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    GhiNavHost(navController)
                }
            }
        }
    }
}
