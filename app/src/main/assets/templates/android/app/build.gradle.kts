import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // Local preview target. IDEaz compiles commonMain + wasmJsMain to a .wasm
    // binary on-device and hot-reloads it in the preview WebView; this target
    // exists so the same sources also build a browser distribution via Gradle.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "helloworld"
        browser()
        binaries.executable()
    }

    // Final output target. Compiled remotely by .github/workflows/build-apk.yml.
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        // All UI and logic lives here. Both targets consume it unchanged.
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }

        // Platform source sets hold nothing but their entry point.
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
        }
    }
}

android {
    namespace = "com.example.helloworld"
    compileSdk = 37

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.example.helloworld"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
