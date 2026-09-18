# Global Host Intelligence — Architecture

Original implementation. Not derived from or copying the source, branding,
or interface of Howdy Host Finder, KOFnet, SNIHost, or any other existing
tool — those are referenced only for the general workflow category
(certificate-transparency-driven domain/host discovery).

## 1. System architecture

**Primary mode — embedded (no server, install-only-the-app):**

```
Android app (Kotlin/Compose)
  --JNI (gomobile bind, in-process, no network)-->
    ghi.aar  (compiled from backend/mobile — DNS, HTTP, TLS, CT-log
              queries, classification — using the phone's own network)
  --stores results-->
    Room (on-device SQLite)
```

No Postgres, no Redis, no Docker, no VPS, no Termux needed at runtime.
The crawler logic is compiled directly into the app via `gomobile bind`
(see `.github/workflows/mobile-lib-build.yml`) and called as ordinary
Kotlin functions — the JNI boundary carries simple types and JSON
strings only (gomobile's type bridge doesn't support arbitrary structs).
This is the same pattern CipherTun VPN uses for its sing-box core. The
built AAR is committed into `android/core/crawlercore/libs/ghi.aar` so a
single APK build contains everything — nothing to install separately.

**Secondary mode — hosted server (optional, unchanged from below):**

```
Android (Kotlin/Compose)  --HTTPS/WSS-->  Go API Server  --SQL-->  PostgreSQL
                                              |  |
                                              |  +--cache/queue/rate-limit--> Redis

                                              |
                                              +--> Crawler Worker Pool
                                                     Discovery -> DNS -> IP/Geo -> ASN
                                                     -> Country Classifier -> HTTP -> TLS
                                                     -> Certificates -> CDN -> Relationships
                                                     -> Postgres write -> WS event
```

Still available for anyone who later wants a hosted, multi-device, or
multi-user deployment — nothing above was removed, `core:network`'s
Retrofit client and the Settings screen's backend URL field still work
against it. The embedded mode is what the app uses by default; pointing
at a hosted server is opt-in. Android never crawls in the hosted-server
case — it requests jobs, browses stored results, and receives live
progress over WebSocket, while discovery/enrichment logic lives in Go on
the server. In embedded mode, Android always crawls directly — that's
the whole point of compiling the engine in.

## 2. Repository tree

```
global-host-intelligence/
  docs/
    ARCHITECTURE.md
    TECHNOLOGY_VERSIONS.md
    README.md, ANDROID.md, BACKEND.md, DATABASE.md, CRAWLER.md,
    API.md, DEPLOYMENT.md, SECURITY.md, TESTING.md   (added as each phase lands)
  android/
    settings.gradle.kts
    build.gradle.kts
    gradle/libs.versions.toml
    app/
    core/{common,model,network,database,designsystem,ui}/
    feature/{home,discover,crawler,countries,country-detail,domains,
              domain-detail,hosts,ips,ip-detail,asns,asn-detail,
              certificates,certificate-detail,search,graph,history,
              bookmarks,exports,settings}/
  backend/
    cmd/{server,worker}/
    internal/{api,auth,crawler,discovery,sources,dns,httpx,tls,geo,asn,
               certificates,cdn,classifier,normalization,deduplication,
               database,queue,cache,websocket,scheduler,exports,
               logging,metrics,config}/
    pkg/{models,validation}/
    migrations/
    configs/
    tests/
  .github/workflows/
    android-build.yml
    backend-build.yml
  docker-compose.yml
```

## 3. Android module tree (Gradle module = folder above)

`app` depends on every `feature/*` module and `core/ui` +
`core/designsystem` for shell/navigation wiring only — no business logic in
`app`. Each `feature/*` module depends only on `core/*` modules, never on a
sibling feature module directly (cross-feature navigation goes through the
shared navigation contracts in `core/ui`, so features stay independently
buildable). `core/model` has zero Android dependencies (pure Kotlin) so it
can be unit-tested on the JVM without an emulator.

```
app            -> all feature/* , core/ui, core/designsystem
feature/*      -> core/common, core/model, core/network, core/database, core/ui
core/network   -> core/model, core/common
core/database  -> core/model, core/common
core/ui        -> core/designsystem, core/model
core/designsystem -> core/common
core/common    -> (leaf)
core/model     -> (leaf, pure Kotlin)
```

## 4. Go module tree

```
cmd/server    -> internal/api, internal/config, internal/database,
                 internal/cache, internal/websocket, internal/scheduler
cmd/worker    -> internal/crawler, internal/queue, internal/database,
                 internal/cache
internal/crawler -> internal/discovery, internal/sources, internal/dns,
                     internal/httpx, internal/tls, internal/geo,
                     internal/asn, internal/certificates, internal/cdn,
                     internal/classifier, internal/normalization,
                     internal/deduplication, internal/queue,
                     internal/database, internal/websocket
internal/*    -> pkg/models, pkg/validation
```

`server` and `worker` are separate binaries sharing internal packages, so
the crawler workers can scale independently of the API process (matches
"each stage should be independently scalable").

## 5. Data model (core tables)

`countries`, `organizations`, `asns`, `ips`, `domains`, `dns_records`,
`certificates`, `cdns`, `sources`, `jobs`, `observations`, `relationships`.

Country classification is deliberately non-singular per domain — see
`domain_country_signals` below — instead of one `country` column, matching
the spec's rule against pretending every domain has one unquestionable
country.

Key relationships:
- `domains.primary_ip_id -> ips.id` (current resolved IP; history lives in `dns_records`)
- `ips.asn_id -> asns.id`, `asns.organization_id -> organizations.id`
- `domains.certificate_id -> certificates.id` (current cert; history in `observations`)
- `domain_country_signals(domain_id, signal_type, country_code, confidence, evidence)` — one row per signal (`tld`, `ip_geo`, `asn`, `organization`, `rdap`, `certificate_org`, `nameserver`), so the UI can show "South Africa — 94%, United Kingdom — 4%, United States — 2%" instead of a single fixed field
- `relationships(domain_id, related_domain_id, relationship_type, evidence)` — shared IP, shared ASN, shared certificate SAN, shared nameserver, subdomain-of
- `observations(entity_type, entity_id, source_id, observed_at)` — append-only, drives "date discovered" / "last seen"
- `jobs(id, scope_type, scope_value, status, started_at, ...)` — one row per discovery run (GLOBAL or a specific country)

Full DDL lands in `backend/migrations/0001_init.up.sql` (Phase 1, included
below) and is extended per-entity in Phase 2.

## 6. Navigation map (Android)

```
Home
 |- Discover
 |   |- Country Discovery -> Country Detail -> Crawler (scoped to that job)
 |   `- Global Discovery -> Crawler (scoped to that job)
 |- Countries (browse) -> Country Detail -> Domains (filtered)
 |- Domains (browse/search) -> Domain Detail
 |                              |- IP Detail
 |                              |- ASN Detail
 |                              |- Certificate Detail
 |                              `- Related Domains -> Domain Detail (recurses)
 |- IPs (browse) -> IP Detail
 |- ASNs (browse) -> ASN Detail
 |- Certificates (browse) -> Certificate Detail
 |- Search (global, cross-entity) -> any Detail screen
 |- Relationship Graph (per-domain or global) -> Domain/IP/ASN Detail
 |- History -> any Detail screen
 |- Bookmarks -> any Detail screen
 |- Export Center
 `- Settings
```

Every screen: top app bar with a title stating what you're looking at, a
back affordance, and contextual actions (bookmark, export, open related).
Detail screens are the hub — every list screen funnels into one.

## 7. API map (REST + WS, `/v1` prefix)

```
GET    /v1/health
GET    /v1/version
POST   /v1/auth/token

GET    /v1/countries                 GET /v1/countries/{code}
GET    /v1/domains                   GET /v1/domains/{id}
GET    /v1/domains/{id}/relationships
GET    /v1/ips                       GET /v1/ips/{id}
GET    /v1/asns                      GET /v1/asns/{id}
GET    /v1/certificates              GET /v1/certificates/{id}
GET    /v1/search?q=&type=

POST   /v1/jobs                      (start discovery: {scope: GLOBAL|country_code})
GET    /v1/jobs/{id}                 GET /v1/jobs/{id}/events (SSE fallback)
POST   /v1/jobs/{id}/pause|resume|stop
GET    /v1/sources                   GET /v1/sources/{name}/health

GET    /v1/bookmarks   POST /v1/bookmarks   DELETE /v1/bookmarks/{id}
GET    /v1/history
POST   /v1/exports     GET /v1/exports/{id}

WS     /v1/ws/jobs/{id}              live crawler events for one job
```

All list endpoints: `?page=&page_size=&sort=&filter[...]=`. All writes
validated and parameterized server-side (see SECURITY.md, added Phase 9).

## 8. Crawler pipeline

```
SOURCE ADAPTER (Discover) -> EXTRACTION -> NORMALIZATION -> DEDUPLICATION
 -> QUEUE -> DNS -> IP ENRICHMENT -> ASN ENRICHMENT -> COUNTRY CLASSIFIER
 -> HTTP ANALYSIS -> TLS ANALYSIS -> CERTIFICATE PARSE -> CDN DETECTION
 -> RELATIONSHIP BUILDER -> DATABASE WRITE -> WEBSOCKET EVENT
```

Source adapters implement one interface (`internal/discovery.Source`):
`Discover(ctx, scope) (<-chan RawRecord, error)`, `HealthCheck(ctx) error`,
`Parse`, `Normalize`, `RateLimit() time.Duration`, `Close() error`. A
failing source is marked `DEGRADED`/`OFFLINE` in the `sources` table and
the pipeline continues with whatever sources are healthy — no single point
of failure. First adapters to implement: Certificate Transparency (crt.sh
JSON endpoint) and a user-provided-domain-list adapter, since both are
public and dependency-free to start with.

## 9. Technology / version matrix

See `docs/TECHNOLOGY_VERSIONS.md` — verified against official sources on
2026-08-29.

## 10. Implementation phases

Phases 1–10 as specified in the build prompt (Foundation, Data Model, API,
Android core screens, Detail screens, Crawler, Live system, Advanced UX,
Hardening, Release build). Tracked in `[[global-host-intelligence]]`
(memory) across sessions since this is a multi-week build, not a
single-turn one.

## A note on this environment

This assistant is running in a sandboxed container with **no network
access** and **no Go toolchain installed**, and no Android SDK/emulator.
That means I can write correct, real source code here, but I cannot
literally compile it, run `go build`, or run Android instrumentation tests
inside this chat — the spec's "compile it, run tests, verify integration"
loop has to happen in your actual build environment, not here. Given your
established pattern on CipherTun VPN — Termux + MT Manager + GitHub
Actions as the sole build environment — this repo ships with GitHub
Actions workflows for both the Android build and the Go backend build/test
from Phase 1 onward, so `git push` gets you a real compiled artifact and
real test results without needing a local SDK. I will never claim
something built or passed tests without that CI (or you) actually having
run it.
