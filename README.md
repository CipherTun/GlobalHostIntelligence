# Global Host Intelligence

Global Host Intelligence (GHI) is an Android network-intelligence toolkit with an embedded Go engine. The APK works without a separate server or Termux installation.

## Features

- **Discovery** — parallel passive discovery using multiple public sources, live validation, source/error reporting, and hostname analysis.
- **Subdomains** — discover and validate subdomains for a target domain.
- **Response Checker** — HTTP/HTTPS request testing with methods, headers, body, redirects, TLS handling, timeout control, DNS/resolver options, and response details.
- **IP / Domain tools** — resolve domains to IP addresses and IP addresses back to observed domains.
- **Payload Generator** — generate network request/payload formats for supported methods and targets.
- **TLS Analyzer** — inspect TLS version, cipher, ALPN, certificates, fingerprints, and peer-chain information.
- **DNS Inspector** — inspect common DNS record types and reverse lookups where available.
- **Certificate Search** — search public Certificate Transparency data.
- **Export** — export discovered live hosts as text, CSV, or JSON.
- **Configurable engine** — discovery limits, validation workers, source parallelism, timeout, user-agent, sources, animations, and compact results.
- **Production AdMob** — consent-aware banner advertising and rate-limited rewarded-interstitial breaks. Debug builds use Google's test ad units; release builds use the configured production units.
- **Privacy options** — access the Google ad privacy controls from Settings when available.
- **ARM support** — the release pipeline packages the embedded Go engine for `armeabi-v7a` and `arm64-v8a`.

## How to use

1. Open GHI and wait for the three-second startup screen.
2. Open **Discovery** from the bottom navigation or the menu.
3. Enter a country code, ASN, or domain scope supported by the discovery screen and start a scan.
4. Watch the live validated results appear while discovery sources run in parallel.
5. Tap a hostname to expand its details. Tap it again to collapse it.
6. Use **Subdomains**, **Response Checker**, **IP / Domain**, **Payload Generator**, **TLS**, **DNS**, and **Certificates** from the menu when you need a specific investigation tool.
7. Use **Export** when you want the current live-host result set as text, CSV, or JSON.
8. Open **Settings** to tune the discovery engine and interface. Ad banners and automatic ad breaks are not shown on Settings.

## Build

GitHub Actions builds the embedded Go AAR first, then injects that exact AAR into the Android build. The release workflow verifies the source revision and native ARM libraries before publishing the signed APK artifact.
