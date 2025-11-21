plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hereliesaz.ideaz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 26

        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true

        aidl = true
    }

    // --- FIX: This block is required ---
    // It tells Gradle where to find your Kotlin code (fixing the "Default Activity" error)
    // and also where to find your in-app tool assets and AIDL files.
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            aidl.srcDirs("src/main/aidl")
            assets.srcDirs("src/main/assets")
            // We DO include jniLibs here for the NATIVE tools (java, aapt2, jules)
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    // --- END FIX ---

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("mime.types")
            excludes.add("META-INF/THIRD-PARTY.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/plexus/components.xml")
            excludes.add("plugin.properties")
            pickFirsts += "META-INF/sisu/javax.inject.Named"
        }
    }

    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.14206865"
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "commons-logging" && requested.name == "commons-logging") {
                useTarget("org.slf4j:jcl-over-slf4j:1.7.30")
                because("Avoids duplicate classes with jcl-over-slf4j")
            }

        }
    }
}

dependencies {
    implementation(libs.maven.resolver.api)
    implementation(libs.maven.resolver.spi)
    implementation(libs.maven.resolver.util)
    implementation(libs.maven.resolver.impl)
    implementation(libs.maven.resolver.connector.basic)
    implementation(libs.maven.resolver.transport.http)

    implementation(libs.maven.core)
    implementation(libs.maven.settings.builder)

    implementation(libs.jaxb.api)
    implementation(libs.javax.annotation.api)
    implementation(libs.validation.api)
    implementation(libs.glassfish.el)
    implementation(libs.slf4j.simple)
    constraints {
        implementation("com.google.guava:guava:32.1.3-android") {
            because("Guava Android is smaller and avoids conflicts")
        }
    }
    implementation(libs.org.eclipse.jgit)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.okhttp)
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.haze)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}