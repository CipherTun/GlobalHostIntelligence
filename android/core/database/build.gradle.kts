// Local Room cache of server data, so browsing already-fetched domains/
// IPs/ASNs/certificates works offline and lists don't re-fetch on every
// screen visit. This is a cache, not the source of truth — the Go
// backend + Postgres remain authoritative; sync strategy lands in Phase 4.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.ciphertun.ghi.core.database"
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
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}