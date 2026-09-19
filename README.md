# GlobalHostIntelligence (GHI)

<p align="center">
  <strong>Android Network Intelligence & Host Discovery Toolkit</strong><br>
  Built for practical DNS, HTTP, TLS, certificate, subdomain and host investigation.
</p>

<p align="center">
  <a href="https://github.com/CipherTun/GlobalHostIntelligence/releases">Releases</a> ·
  <a href="https://github.com/CipherTun/GlobalHostIntelligence/issues">Issues</a> ·
  <a href="https://github.com/CipherTun/GlobalHostIntelligence/security">Security</a>
</p>

## Overview

**GlobalHostIntelligence (GHI)** is an Android network-intelligence toolkit with an embedded Go engine. The application is designed to provide useful host and network investigation capabilities directly on Android without requiring a separate server or Termux installation.

GHI combines discovery, live validation and focused network-analysis tools in one Android application.

> **Project status:** Active development. Features and interfaces may continue to evolve between releases.

## What GHI can do

### Host & subdomain discovery

- Parallel passive discovery using multiple public/free sources.
- Candidate hostname normalization and de-duplication.
- Concurrent HTTP/HTTPS validation.
- Live-host identification and source/error reporting.
- Hostname details that can be expanded directly from results.
- Configurable discovery limits, validation workers, source parallelism and timeouts.

### HTTP / HTTPS response testing

The Response Checker provides an HTTP Custom-style testing workflow with support for:

- GET
- HEAD
- POST
- PUT
- PATCH
- OPTIONS
- Custom headers
- Request bodies
- Redirect handling
- TLS handling
- Timeout control
- DNS/resolver diagnostics
- Status and response details
- Response preview and copy controls

### IP and domain intelligence

- Domain → IP resolution.
- IP → observed domain lookup.
- Multiple inputs can be processed concurrently.
- Results are normalized for investigation workflows.

### Payload generation

Generate protocol-valid HTTP/1.1 request variants from a target, including:

- Standard HTTP/HTTPS requests.
- Raw and absolute-form requests.
- Keep-alive variants.
- WebSocket handshake variants.
- Custom headers and body content.
- cURL export.
- Fetch export.
- Per-payload and Copy All controls.

### TLS / SSL analysis

Inspect available TLS connection information including:

- TLS version.
- Cipher.
- ALPN.
- Certificate metadata.
- SHA-256 certificate fingerprint.
- Leaf certificate PEM.
- Peer certificate chain information.

### DNS inspection

Inspect common DNS records where available:

`A` · `AAAA` · `CNAME` · `MX` · `NS` · `TXT` · `SRV` · `PTR`

### Certificate Transparency search

Search public Certificate Transparency data for domain and certificate information.

### Investigation workflow

GHI can combine host, DNS, TLS, security-header, redirect, timing, technology, web-surface and certificate information into a broader investigation workflow.

### Web Surface Inspector

Inspect the target's public web surface without recursive crawling, including:

- Landing page metadata.
- `robots.txt`.
- `sitemap.xml`.
- `security.txt`.
- Android association metadata.
- iOS association metadata.
- Web manifest metadata.

### GHI Agent

The GHI Agent routes plain-language requests to real local engines or supported public web search functionality. It is designed to expose actual tool results rather than fabricate remote-LLM activity.

### Export

Export discovered live hosts as:

- TXT
- CSV
- JSON

## Android support

| Target | Support |
|---|---|
| Android | Android 8.0+ (`minSdk 26`) |
| ARM 32-bit | `armeabi-v7a` |
| ARM 64-bit | `arm64-v8a` |
| Application ID | `io.ciphertun.ghi` |
| Current version | `4.1.0` |
| Compile SDK | 37 |
| Target SDK | 37 |
| Java | 17 |

The release pipeline packages the embedded native Go engine for the Android ARM ABIs used by the project.

## Download

Official builds are published through the repository's **Releases** page when a release is created:

**[Download GHI releases](https://github.com/CipherTun/GlobalHostIntelligence/releases)**

GitHub Actions also produces release APK artifacts from the project's release workflow.

> The repository currently has no GitHub Release published. The first formal release can be created after the release APK workflow completes successfully.

## Screenshots

Screenshots should show the actual current application UI rather than mocked or unrelated images.

Recommended public screenshots:

1. Discovery screen.
2. Live discovery results with hostname details expanded.
3. Response Checker.
4. Payload Generator.
5. TLS/DNS analysis.
6. Settings.
7. Main navigation/menu.

Place verified screenshots under `docs/screenshots/` and add them to this section using relative Markdown image paths.

## Build architecture

GHI uses a two-stage Android build pipeline:

1. The mobile core is built as an AAR.
2. The release workflow downloads the matching AAR from the successful Actions run.
3. The AAR is injected into the Android project.
4. Android is built with Gradle.
5. The signed release APK is verified and uploaded as an Actions artifact.

The release configuration currently disables R8/minification and resource shrinking to keep the release build predictable.

## Development

The project is primarily Kotlin/Jetpack Compose on Android with an embedded Go-based engine.

Current Android build configuration includes:

- Android Gradle Plugin / Gradle-based Android build.
- Kotlin + Jetpack Compose.
- Hilt dependency injection.
- AndroidX lifecycle/navigation components.
- Google Mobile Ads SDK.
- User Messaging Platform for ad consent.

## Advertising

The application contains consent-aware advertising and rate-limited ad experiences.

The repository's production AdMob configuration is intentionally kept in the application source and release verification workflow. **Do not replace, remove or publish private advertising/account credentials.**

## Privacy & responsible use

GHI is a network-intelligence and diagnostic tool. Only investigate systems, domains and networks that you own or have explicit permission to test.

Some discovery and Certificate Transparency features use public internet data. Availability, accuracy and freshness of third-party data sources can change independently of GHI.

## Security

Please do **not** report sensitive vulnerabilities in a public issue.

See [`SECURITY.md`](SECURITY.md) for the responsible disclosure process.

## Contributing

Contributions, bug reports, documentation improvements and feature proposals are welcome.

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

## License

GlobalHostIntelligence is released under the **MIT License**.

See [`LICENSE`](LICENSE) for the full license text.

## Project links

- Repository: https://github.com/CipherTun/GlobalHostIntelligence
- Releases: https://github.com/CipherTun/GlobalHostIntelligence/releases
- Issues: https://github.com/CipherTun/GlobalHostIntelligence/issues
- Security: https://github.com/CipherTun/GlobalHostIntelligence/security


**Included example:** `docs/screenshots/08-discovery-with-ad.png` — Discovery screen with an advertisement placement.
