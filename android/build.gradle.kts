// Top-level build file: declares plugin versions for the whole project
// without applying them (apply false), matching the version catalog in
// gradle/libs.versions.toml. See docs/TECHNOLOGY_VERSIONS.md for the
// verified version numbers.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
