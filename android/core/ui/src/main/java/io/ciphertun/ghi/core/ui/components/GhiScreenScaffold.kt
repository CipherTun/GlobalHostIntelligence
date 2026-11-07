package io.ciphertun.ghi.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Screen scaffold. Both the container and the app bar are transparent so the
 * app-level [GhiBackdrop] shows through every screen (previously each screen
 * painted an opaque background and hid it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhiScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    val openDrawer = LocalGhiOpenDrawer.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    when {
                        onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        openDrawer != null -> IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Open navigation") }
                    }
                },
                actions = actions,
            )
        },
    ) { innerPadding -> content(Modifier.padding(innerPadding)) }
}
