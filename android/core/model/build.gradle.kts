// Pure Kotlin module — deliberately has zero Android dependency so these
// data classes are unit-testable on the plain JVM without an emulator,
// and so every other module (including backend-adjacent tooling) can
// depend on them cheaply.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}
