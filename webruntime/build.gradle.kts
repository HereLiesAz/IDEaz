plugins {
    alias(libs.plugins.android.dynamic.feature)
}

// Assets-only dynamic feature module: it carries the bundled in-browser web
// runtime (assets/ideaz-runtime/*) and nothing else — no Kotlin, no resources.
// minSdk / versionCode / signing are all inherited from the base :app module.
android {
    namespace = "com.hereliesaz.ideaz.webruntime"
    compileSdk = 37
}

dependencies {
    implementation(project(":app"))
}
