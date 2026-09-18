# Technology Versions

Verified 2026-09-03 against current stable upstream releases. Preview/alpha/beta releases are not used.

## Android toolchain

| Component | Version |
|---|---|
| Kotlin | 2.4.10 |
| Android Gradle Plugin | 9.3.2 |
| Gradle | 9.7.1 |
| Jetpack Compose BOM | 2026.08.00 |
| Compose | 1.12.0 via BOM |
| Material 3 | 1.4.0 via BOM |
| Core KTX | 1.19.0 |
| Activity Compose | 1.13.0 |
| Lifecycle / ViewModel | 2.11.0 |
| Navigation Compose | 2.10.0 |
| Room | 2.8.4 |
| DataStore | 1.2.1 |
| WorkManager | 2.11.2 |
| Paging | 3.5.1 |
| Kotlin Serialization | 1.10.0 |
| Kotlin Coroutines | 1.11.0 |
| Hilt Gradle/runtime | 2.60.1 |
| AndroidX Hilt | 1.4.0 |
| KSP | 2.3.11 |
| Android Test JUnit Extension | 1.3.0 |
| Espresso | 3.7.0 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| JDK | 17 |
| Android NDK | 29.0.14206865 (r29) |

## Native library pipeline

The Go mobile package is bound with the current `golang.org/x/mobile` toolchain. The CI explicitly builds:

- `android/arm` → `armeabi-v7a` (ARM32)
- `android/arm64` → `arm64-v8a` (ARM64)

The AAR workflow uploads `ghi-core-aar`. The Android workflow runs only after that workflow succeeds, downloads that exact workflow artifact, copies it into `android/core/crawlercore/libs/ghi.aar`, builds the APK, and verifies both native ABIs are inside the APK.

## Backend toolchain

| Component | Version |
|---|---|
| Go | 1.27.1 |
| chi | 5.3.2 |
| pgx | 5.10.0 |
| go-redis | 9.22.0 |
| PostgreSQL CI service | 18.6 |
| Redis CI service | 8.10 |

## Compatibility rules

- Keep Kotlin 2.4.10 with KSP 2.3.11.
- Keep Compose BOM 2026.08.00 with compileSdk 37.
- Keep JDK 17 for the Android build.
- Keep NDK r29 for the gomobile AAR job.
- Do not use preview AndroidX or Compose artifacts merely to obtain a higher version number.
