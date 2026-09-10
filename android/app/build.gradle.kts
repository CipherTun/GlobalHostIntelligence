plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.ciphertun.ghi"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.ciphertun.ghi"
        minSdk = 26
        targetSdk = 37
        versionCode = 241
        versionName = "2.4.1"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("GHI_RELEASE_STORE_FILE").orNull
                ?: System.getenv("GHI_RELEASE_STORE_FILE")
            val storePasswordValue = providers.gradleProperty("GHI_RELEASE_STORE_PASSWORD").orNull
                ?: System.getenv("GHI_RELEASE_STORE_PASSWORD")
            val keyAliasValue = providers.gradleProperty("GHI_RELEASE_KEY_ALIAS").orNull
                ?: System.getenv("GHI_RELEASE_KEY_ALIAS")
            val keyPasswordValue = providers.gradleProperty("GHI_RELEASE_KEY_PASSWORD").orNull
                ?: System.getenv("GHI_RELEASE_KEY_PASSWORD")

            if (storeFilePath != null && storePasswordValue != null &&
                keyAliasValue != null && keyPasswordValue != null
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:crawlercore"))

    implementation(project(":feature:discover"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

    implementation("com.google.android.gms:play-services-ads:25.4.0")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
