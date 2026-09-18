pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "global-host-intelligence"

include(":app")

// core
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:designsystem")
include(":core:ui")
include(":core:crawlercore")

// feature
include(":feature:discover")
include(":feature:settings")
