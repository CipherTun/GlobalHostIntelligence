# Global Host Intelligence — 2026-09 Upgrade Research

This upgrade keeps the Run #53 architecture and adds capabilities based on current public documentation and current releases, rather than replacing the app with a terminal workflow.

## UI / Android baseline

- Kotlin 2.4.10 is the current stable Kotlin release as of this upgrade.
- Android Gradle Plugin 9.3.2 is the current stable AGP API release.
- Compose BOM 2026.08.00 is the current stable Compose BOM; Compose 1.12 and Material 3 1.4 are the stable August 2026 line.
- AndroidX current stable releases are used where they are compatible with this project.

## Discovery research

The discovery architecture follows the modular-source approach demonstrated by ProjectDiscovery Subfinder: individual passive providers, source selection, rate limiting, source attribution and graceful handling of unavailable providers.

Free/public sources retained or added in this upgrade include:

- Certificate Transparency / crt.sh
- CertSpotter
- crt.name
- CTLogs
- Anubis
- Subdomain Center
- HackerTarget
- Wayback
- ThreatMiner
- URLScan public search
- RapidDNS
- RIPEstat country/ASN data already present in the project
- **AlienVault OTX passive DNS** (anonymous endpoint; optional API key can increase limits)
- **Common Crawl CDX index** (public, no paid subscription; rate-limited public infrastructure)

Dead/unreliable free sources are deliberately not added just to increase the source count. ProjectDiscovery's 2026 source discussions specifically identify BufferOver and RedHuntLabs as unreliable/defunct for free use.

## HTTP response checker research

HTTP Custom currently advertises a Response Checker, live connection log, IP Finder/Auto Ping and reusable custom HTTP request payload controls. GHI does not copy HTTP Custom code or proprietary UI. Instead, the response-checking concepts are implemented natively in the existing GHI engine:

- HTTP/HTTPS response status
- configurable HTTP method
- configurable path
- custom request headers
- optional request body
- redirect control
- TLS verification control
- bounded timeout
- response headers
- bounded response-body preview
- final URL after redirects
- latency
- content type/length
- TLS handshake/version/verification state
- CDN detection
- resolved IP addresses
- copyable structured result

The result is presented as compact cards with expandable headers/body sections rather than a terminal dump.

## Safety / stability principles

- No destructive network operations were added.
- Passive discovery remains passive; HTTP validation is limited to ordinary requests to user-selected hosts.
- Response bodies are capped at 64 KiB in the mobile inspector to avoid large-memory UI failures.
- Existing ARM32 and ARM64 native packaging remains part of the build contract.
- Existing Run #53 routes and tools remain present; richer screens are introduced incrementally.

## Primary research references

- AndroidX releases: https://developer.android.com/jetpack/androidx/versions
- Compose August 2026 release: https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
- Android Gradle Plugin 9.3: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- Go release history: https://go.dev/doc/devel/release
- ProjectDiscovery Subfinder: https://github.com/projectdiscovery/subfinder
- Common Crawl Index Server: https://index.commoncrawl.org/
- Common Crawl access: https://commoncrawl.org/get-started
- AlienVault OTX: https://otx.alienvault.com/
- HTTP Custom public listing: https://play.google.com/store/apps/details?id=xyz.easypro.httpcustom
