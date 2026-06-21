plugins {
    alias(libs.plugins.android.dynamic.feature)
}

// Assets-only dynamic feature module: it carries the bundled in-browser web
// runtime (assets/ideaz-runtime/*) and nothing else — no Kotlin, no resources.
// versionCode / signing are inherited from the base :app module. minSdk must be
// declared explicitly and match the base (30): bundletool rejects the bundle if a
// feature module's minSdkVersion is lower than the base module's.
android {
    namespace = "com.hereliesaz.ideaz.webruntime"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }
}

dependencies {
    implementation(project(":app"))
}
