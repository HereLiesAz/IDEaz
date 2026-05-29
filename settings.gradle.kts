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
// foojay-resolver-convention was removed. (An earlier changelog claimed this cleared
// the Netty/BouncyCastle/Jackson/Commons-Lang/HttpClient dependency CVEs — that was a
// misdiagnosis. Those libraries are NOT transitive deps of this settings-classpath
// plugin; they come from real app dependencies — google-genai (Jackson at runtime;
// Netty via grpc-netty on the unit-test classpath), Robolectric (BouncyCastle), and
// AGP's androidLintTool (commons-lang3 + BouncyCastle) — and are now pinned to patched
// versions via resolutionStrategy in app/build.gradle.kts.)
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
