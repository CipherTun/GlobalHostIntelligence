# GlobalHostIntelligence 2.3.0 — focused full-app build notes

## Intentional app surface
Exactly five top-level areas are exposed:
- Discover
- IP / Domain
- Response
- Payloads
- Settings

No History, Search, Graph, Bookmarks, Exports, Home, standalone Hosts, Domains, IP Addresses, Countries, ASNs or Certificates menus are included in the active Android app.

## Discovery
- Every ISO country code exposed by Android is available in the country selector.
- Country discovery uses RIPEstat country/ASN routing data plus URLScan and the passive datasets available to the mobile source adapters.
- Domain discovery fans out across the enabled public/free source adapters concurrently.
- Candidate hostnames are normalized and de-duplicated.
- Validation is concurrent and only HTTP/HTTPS 2xx–3xx hosts are promoted to live results.
- Source parallelism, validation concurrency, timeout and User-Agent are configurable in Settings.

## Network tools
- IP / Domain performs Domain → IP(s) and IP → Domain(s), with multiple input lines processed concurrently.
- Response Checker retains the supplied HTTP Custom-style response result behavior, including progressive cards, status colors, CDN detection, copy controls and DNS diagnostics.
- Payload Generator automatically creates standard HTTP/HTTPS request variants from a domain and provides per-payload Copy plus Copy All.

## Verification
- Go mobile package: `go test ./mobile` passes when run against a temporary Go 1.23 test copy because the workspace environment cannot download its declared Go 1.27 toolchain.
- Android Gradle compilation could not be executed in this workspace because the Android/Gradle toolchain is not installed. No claim of an Android build pass is made.
