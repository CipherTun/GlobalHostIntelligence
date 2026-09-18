# The Go Mobile API is reached through reflection from GhiMobileBridge.
# Keep the generated class and methods in minified release builds.
-keep class io.ciphertun.ghi.core.crawlercore.generated.mobile.Mobile { *; }
-keep class io.ciphertun.ghi.core.crawlercore.generated.mobile.** { *; }

# Keep the bridge itself stable for the app module.
-keep class io.ciphertun.ghi.core.crawlercore.GhiMobileBridge { *; }
