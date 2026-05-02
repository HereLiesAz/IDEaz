
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val major = versionProps.getProperty("major").toInt()
val minor = versionProps.getProperty("minor").toInt()
val patch = versionProps.getProperty("patch").toInt()
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.hereliesaz.ideaz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 30

        targetSdk = 36
        versionCode = major * 1000000 + minor * 10000 + patch * 100 + buildNumber
        versionName = "$major.$minor.$patch.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"
            )
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/INDEX.LIST")
            pickFirsts.add("**/*.kotlin_builtins")
            pickFirsts.add("**/*.kotlin_module")
        }
    }
}

androidComponents.onVariants { variant ->
    variant.outputs.forEach { output ->
        val version = output.versionName.get()
        // Workaround: VariantOutput interface does not expose outputFileName in this AGP version.
        // We cast to the internal implementation to maintain the renaming feature.
        if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
            output.outputFileName.set("IDEaz-$version-${variant.name}.apk")
        }
    }
}

// AGP 9 turns on consistent resolution by default, which forces androidTest classpaths
// to align with debug runtime. The Google generativeai SDK pulls in
// concurrent-futures(-ktx) 1.2.0-alpha03 while AndroidX test deps pull in 1.2.0,
// producing a strict-version conflict during lint's androidTest model generation.
// Pin both modules to the alpha version that matches runtime to break the deadlock.
configurations.all {
    resolutionStrategy {
        force("androidx.concurrent:concurrent-futures:1.2.0-alpha03")
        force("androidx.concurrent:concurrent-futures-ktx:1.2.0-alpha03")
    }
}

dependencies {
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    implementation(libs.org.eclipse.jgit)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.generativeai)
    implementation(libs.google.genai)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.compose.unstyled.core)
    implementation(libs.aznavrail)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.haze)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
}
