# GHI 4.1 merge manifest

The supplied `GHI-4.1-intelligence-upgrade-bundle.zip` was merged into the supplied full repository before the additional 4.1 work was applied.

| Supplied bundle item | Integrated result |
|---|---|
| `GhiComponents.kt` | Replaced with the spectrum spark implementation; additionally changed to show the contextual message before the spark appears. |
| `GhiIntelligenceScreens.kt` | Integrated; investigation and agent calls now run on `Dispatchers.IO` so the UI does not freeze. |
| `PayloadGeneratorScreen.kt` | Integrated and expanded into a request lab with validation, raw HTTP, cURL and Fetch output. |
| `SecurityToolsScreens.kt` | Integrated with background network work and spectrum loading state. |
| `SubdomainsScreen.kt` | Integrated with spectrum loading state and 5000-result UI limit. |
| `carrier.go` | Integrated with current Chrome reduced UA, real CRLF framing, UTF-8 body length and WebSocket handshake headers. |
| `GhiSession.user-agent.patch` | Applied manually to the exact repository version so old GHI UAs migrate to the current Chrome UA while custom UAs remain supported. |
| `NetworkToolsScreens.loading.patch` | Applied manually and extended so the response checker shows a global loading state even when previous results remain visible. |
| `DiscoveryAndIp.loading.patch` | Applied manually to Discovery and IP/Domain tools. |

Additional 4.1 work:

- New Web Surface Inspector screen and Go engine.
- Web Surface routing in GHI Agent and Investigation.
- Current browser UA centralized in `backend/mobile/user_agent.go`.
- UA and request-generation regression tests.
- Web Surface regression tests.
- Discovery/subdomain limits increased to 5000 while preserving bounded concurrency.
- Version bumped to 4.1.0.
- Release notes and research documentation updated.
