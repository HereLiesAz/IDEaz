plugins {
    // Declared here with `apply false` so every plugin's version is resolved once,
    // at the root, and subprojects can apply them by alias without a version. The
    // Kotlin Multiplatform plugin in particular must be declared this way: AGP
    // puts Kotlin on the build classpath itself, so a versioned request from a
    // subproject fails with "already on the classpath with an unknown version".
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
