plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
        }
    }
    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/NOTICE")
    }
    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/sisu/javax.inject.Named")
    }
}

dependencies {
    implementation(platform("org.eclipse.aether:aether-bom:1.1.0"))
    implementation("org.eclipse.aether:aether-api")
    implementation("org.eclipse.aether:aether-spi")
    implementation("org.eclipse.aether:aether-util")
    implementation("org.eclipse.aether:aether-impl")
    implementation("org.eclipse.aether:aether-connector-basic")
    implementation("org.eclipse.aether:aether-transport-file")
    implementation("org.eclipse.aether:aether-transport-http")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")

    implementation("org.slf4j:slf4j-simple:1.7.36")

    constraints {
        implementation("com.google.guava:guava:32.1.3-android") {
            because("Guava Android is smaller and avoids conflicts")
        }
    }

    // JGit for Git operations
    implementation(libs.org.eclipse.jgit)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    // Networking for Jules API
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Maven Dependency Resolution
    implementation(libs.maven.resolver.provider)
    implementation(libs.maven.resolver.api)
    implementation(libs.maven.resolver.spi)
    implementation(libs.maven.resolver.util)
    implementation(libs.maven.resolver.impl)
    implementation(libs.maven.resolver.connector.basic)
    implementation(libs.maven.resolver.transport.file)
    implementation(libs.maven.resolver.transport.http)
    implementation(libs.slf4j.simple)
    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.aznavrail)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
