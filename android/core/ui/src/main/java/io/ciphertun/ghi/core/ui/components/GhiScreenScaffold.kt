package io.ciphertun.ghi.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.GhiInk900

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GhiInk900, titleContentColor = MaterialTheme.colorScheme.onSurface),
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
