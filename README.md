# Global Host Intelligence

Android client (Kotlin/Compose) with an **embedded Go crawler engine**
(compiled in via `gomobile bind` — no server, no VPS, no Termux needed
to run it) for certificate-transparency-driven domain/host discovery,
IP/ASN/geo enrichment, and multi-signal country classification. An
optional hosted Go backend + PostgreSQL + Redis stack is also included
for anyone who later wants a multi-device/multi-user deployment. See
`docs/ARCHITECTURE.md` for both modes and `docs/TECHNOLOGY_VERSIONS.md`
for pinned tool versions.

**Status: Phase 1 (Foundation) plus the embedded-core bootstrap —
repo/module scaffolding, Postgres schema, Docker Compose, full Android
navigation skeleton across 20 screens, and a gomobile-bindable Go
package (`backend/mobile`) with a working DNS-lookup call, wired end to
end into the Home screen to prove the JNI bridge. Business logic beyond
that (real crawler stages, real REST handlers) is not yet implemented —
see the phase list in `docs/ARCHITECTURE.md`.**

## Repository layout

```
android/    Kotlin/Compose app, including core/crawlercore (wraps ghi.aar)
backend/    Go embedded engine (backend/mobile) + optional hosted API server/worker
docs/       Architecture, technology versions, and (per-phase) API/security/testing docs
.github/    CI: android-build.yml, backend-build.yml, mobile-lib-build.yml
```

## Running it — embedded mode (default, install only the app)

1. Push this repo to GitHub. `.github/workflows/mobile-lib-build.yml`
   compiles `backend/mobile` into `ghi.aar` via `gomobile bind` and
   commits it to `android/core/crawlercore/libs/ghi.aar`.
2. That commit triggers `.github/workflows/android-build.yml`, which
   builds the APK against the newly-committed AAR — the AAR is baked
   into that APK, so installing just the APK is enough.
3. Download the debug APK from that workflow run's artifacts, install
   it. Home screen shows a "ghi-mobile-core alive" card and a button
   that runs a real DNS lookup through the embedded library — no server,
   no Termux, no backend to keep running anywhere.

The `mobile-lib-build.yml` workflow needed the Android NDK + gomobile,
neither of which were available to verify this in the environment it
was written in — expect to debug NDK/API-level mismatches on the first
real run, the same way the AGP 9 / Hilt compatibility issues came up.

The very first push triggers both `mobile-lib-build.yml` and
`android-build.yml` at once — the first `android-build.yml` run will
fail (no AAR committed yet). That's expected; it self-heals once
`mobile-lib-build.yml` finishes and pushes the AAR, which triggers
`android-build.yml` again.

## Running the optional hosted backend locally

Requires Docker.

```
docker compose up --build
curl http://localhost:8080/v1/health
```

This starts Postgres 18.6, Redis 8.10, runs migrations, then starts the
API server and crawler worker.

## Building the Android app

CI (`.github/workflows/android-build.yml`) installs Gradle 9.7.1 directly
via `gradle/actions/setup-gradle` and runs `gradle assembleDebug` — it
does not depend on a committed wrapper, so pushing this repo as-is is
enough for CI to build it.

This repo does not yet include `gradle/wrapper/gradle-wrapper.jar` (a
binary file that could not be generated without network/Gradle access in
the environment this scaffold was built in). That only matters if you
want a portable `./gradlew` for local builds — generate it once from any
machine with Gradle installed, from the `android/` directory:

```
gradle wrapper --gradle-version 9.7.1
git add gradle/wrapper
git commit -m "Add Gradle wrapper"
```

Given the established build pattern for this org (Termux + MT Manager +
GitHub Actions as the sole build environment — see the CipherTun VPN
project), the intended flow is: edit in Termux, push to `dev`, let GitHub
Actions produce the APK — the same pattern already proven there.

## Continuing the build

Each phase in `docs/ARCHITECTURE.md` §10 is a self-contained unit of
work: implement the REST handlers (Phase 3), wire ViewModels/repositories
to real data (Phase 4), flesh out detail screens (Phase 5), build the
crawler pipeline stages (Phase 6), add the WebSocket live feed (Phase 7),
then advanced UX, hardening, and release build (Phases 8–10).
