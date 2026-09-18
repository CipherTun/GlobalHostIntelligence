plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.ciphertun.ghi.core.crawlercore"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The gomobile AAR is unpacked by CI into this Android library module.
// We intentionally consume its classes.jar + native libraries separately
// instead of declaring a local .aar dependency. AGP does not allow an AAR
// to contain another AAR, while the app can safely package these extracted
// classes/native libraries into the final APK.
dependencies {
    api(files("libs/ghicore-classes.jar"))
}
