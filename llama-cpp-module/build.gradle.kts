// Android library module wrapping llama.cpp via JNI. NOT included in
// settings.gradle.kts by default — see README.md to activate.
plugins {
    id("com.android.library")
}

android {
    namespace = "android.llama.cpp"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        ndk {
            // Add "x86_64" for the emulator. Each ABI multiplies size + build time.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DGGML_OPENMP=OFF", "-DLLAMA_CURL=OFF")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
