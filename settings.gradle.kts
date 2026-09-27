pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ciyato"
include(":app")

// Performance regression gates for a launcher, which is the one app on a phone
// that gets opened and returned to constantly (F-166). This module is
// `com.android.test`: it builds an APK that drives the app on a real device, so
// it configures and compiles here and RUNS only on hardware - see ci/README.md.
include(":macrobenchmark")
