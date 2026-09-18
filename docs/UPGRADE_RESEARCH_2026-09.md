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


## 4.1 full-upgrade additions

### Browser identity / User-Agent

StatCounter's August 2026 worldwide data shows Chrome as the largest browser family overall (69.39%), with Chrome for Android at 60.29% of mobile browser-version share. Google's Chrome Releases page lists Chrome 153.0.8010.52 as the stable Android release published 17 September 2026. Chrome's reduced Android User-Agent format intentionally exposes `Android 10; K` and a major-only browser version with `0.0.0` minor/patch values, so GHI uses:

`Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36`

This is used as the default browser-style UA across the mobile Go engine and payload builder. A user-supplied custom UA is still preserved.

### Thinking/loading state

Google describes Gemini Spark as an agentic experience with live task progress, while Android's Compose documentation supports continuous `InfiniteTransition` animations and animated colors. GHI's implementation is local and deterministic: a thin contextual message appears first, then a four-point sparkle sweeps the full visible HSV hue range continuously at 400 ms per hue cycle. The message rotates by operation context; it is not presented as an LLM-generated thought process and it never fabricates backend progress.

### HTTP request builder

The payload generator is now a protocol-valid HTTP/1.1 request lab rather than a decorative animation. It constructs the request line, Host, User-Agent, Accept, optional custom headers, UTF-8 Content-Length, and body framing with CRLF separators. It also validates the generated request and exports equivalent cURL and browser Fetch snippets. The WebSocket entry is explicitly a handshake template, not a full WebSocket frame client.

### Web surface inspector

A new bounded Web Surface Inspector checks the landing page plus a fixed standards-oriented set: `robots.txt`, `sitemap.xml`, `/.well-known/security.txt`, `/.well-known/assetlinks.json`, `/.well-known/apple-app-site-association`, and `/manifest.json`. It extracts the page title, canonical URL, same-origin links, response metadata, SHA-256 body fingerprint, sitemap declarations, and bounded previews. It does not recursively crawl or guess arbitrary paths.

### Broader intelligence integration

Investigation now includes the web-surface inspection, and the local GHI Agent can route requests for surface/robots/sitemap/manifest inspection. The existing discovery, DNS, TLS, security-header, redirect, timing, technology-fingerprint, certificate, web-search and optional ProjectDiscovery integrations remain intact.

## 4.1 source references

- Chrome Releases — Chrome for Android 153.0.8010.52, 17 Sep 2026: https://chromereleases.googleblog.com/2026/09/chrome-for-android-update_01970946217.html
- MDN — User-Agent header and Chrome Android UA reduction: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/User-Agent
- MDN — User-Agent reduction: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/User-agent_reduction
- StatCounter — worldwide browser share, Aug 2026: https://gs.statcounter.com/browser-market-share/all-worldwide
- StatCounter — mobile browser-version share, Aug 2026: https://gs.statcounter.com/browser-version-market-share/mobile/worldwide
- Google — Gemini Spark / agentic Gemini app, May 2026: https://blog.google/innovation-and-ai/products/gemini-app/next-evolution-gemini-app/
- Google — Gemini Spark updates, Jun/Jul 2026: https://blog.google/innovation-and-ai/products/gemini-app/gemini-spark-updates-june-2026/
- Android Developers — InfiniteTransition: https://developer.android.com/reference/kotlin/androidx/compose/animation/core/InfiniteTransition
- Android Developers — animateColor: https://developer.android.com/reference/kotlin/androidx/compose/animation/animateColor.composable
- OWASP MASTG — Mobile App Network Communication: https://mas.owasp.org/MASTG/0x04f-Testing-Network-Communication/
- OWASP MASVS — Network Communication: https://mas.owasp.org/MASVS/08-MASVS-NETWORK/
- OWASP Amass — attack-surface management and asset discovery: https://owasp.org/projects/amass
- ProjectDiscovery Nuclei — HTTP request/input formats: https://docs.projectdiscovery.io/tools/nuclei/input-formats
- SNI Host — public payload-generator feature description: https://snihost.com/payload-generator/about
