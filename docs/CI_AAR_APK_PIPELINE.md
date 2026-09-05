# CI AAR → APK Pipeline

The Android application does not build against a placeholder native library.
The native Go core is built first by `Mobile Core Library (AAR)`.

1. `mobile-lib-build.yml` checks out the same source revision.
2. It installs Go 1.27.1, JDK 17 and Android NDK r29 (`29.0.14206865`).
3. `gomobile bind` builds one AAR containing exactly ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`).
4. The workflow verifies both `.so` entries exist in the AAR.
5. The workflow uploads the AAR as the `ghi-core-aar` artifact.
6. `android-build.yml` starts only after that AAR workflow succeeds.
7. It downloads the artifact from that exact workflow run using `gh run download`.
8. It copies the downloaded AAR to `android/core/crawlercore/libs/ghi.aar`.
9. Gradle 9.7.1 runs the unit tests and debug APK build.
10. The workflow verifies that the resulting APK contains both ARM32 and ARM64 Go libraries.
11. The debug APK is uploaded as the `ghi-debug-apk` artifact.

The AAR is intentionally not committed to the repository. This prevents a stale
binary from being used when the Go mobile source changes and prevents an AAR
commit from recursively triggering the build workflows.
