package io.ciphertun.ghi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.*

@Composable
fun GhiInvestigationScreen(
    investigate: (String) -> String
) {
    var target by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    GhiScreenScaffold("Investigation") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "ATTACK-SURFACE INVESTIGATION",
                    "Runs GHI's real discovery, DNS, HTTP, TLS, certificate and security-analysis engines together."
                )
            }
            item {
                GhiCard {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Domain or host") },
                            placeholder = { Text("example.com") }
                        )
                        Button(
                            onClick = {
                                running = true
                                result = investigate(target.trim())
                                running = false
                            },
                            enabled = target.isNotBlank() && !running,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Security, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "ANALYZING…" else "START INVESTIGATION")
                        }
                        GhiBusyIndicator(running)
                    }
                }
            }
            if (result.isNotBlank()) {
                item { GhiLiveJsonCard("LIVE INVESTIGATION RESULT", result) }
            }
        }
    }
}

@Composable
fun GhiAgentScreen(
    ask: (String) -> String
) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    GhiScreenScaffold("GHI Agent") { modifier ->
        LazyColumn(
            modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                GhiHero(
                    "GHI AGENT",
                    "A local, deterministic agent with no API key. It executes real GHI tools and can perform live public web searches."
                )
            }
            item {
                GhiCard {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("Ask GHI") },
                            placeholder = { Text("Investigate example.com") }
                        )
                        Text(
                            "Examples: investigate example.com • DNS example.com • TLS example.com • headers example.com • search ProjectDiscovery httpx",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhiSlate300
                        )
                        Button(
                            onClick = {
                                running = true
                                result = ask(query.trim())
                                running = false
                            },
                            enabled = query.isNotBlank() && !running,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (running) "WORKING…" else "RUN")
                        }
                        GhiBusyIndicator(running)
                    }
                }
            }
            if (result.isNotBlank()) {
                item { GhiLiveJsonCard("LIVE AGENT RESULT", result) }
            }
        }
    }
}

@Composable
private fun GhiLiveJsonCard(title: String, value: String) {
    GhiCard {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Language, null, tint = GhiAccentCyan)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = GhiSlate300
            )
        }
    }
}
