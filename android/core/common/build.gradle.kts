plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.ciphertun.ghi.core.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}