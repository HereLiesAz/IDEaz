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
// foojay-resolver-convention was removed: its transitive Netty/BouncyCastle/
// Jackson/Commons-Lang/HttpClient surface accounted for all 16 build-time
// dependency CVEs flagged on this project, and upstream is already at the
// latest 1.0.0 release with no fixed-in version to bump to.
//
// Gradle still resolves the jvmToolchain(21) request in app/build.gradle.kts,
// just from locally-installed JDKs (CI installs one via actions/setup-java).
// If a fresh dev machine doesn't have JDK 21, the build now fails fast
// instead of auto-downloading via Foojay — install Temurin 21 once.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "IDEaz"
include(":app")
