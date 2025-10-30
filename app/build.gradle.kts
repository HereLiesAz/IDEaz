plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hereliesaz.ideaz"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 24
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
    buildFeatures {
        compose = true
        aidl = true
    }
    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/sisu/javax.inject.Named"
        }
    }
}

dependencies {
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
    implementation("org.apache.maven:maven-resolver-provider:3.8.1")
    implementation("org.apache.maven.resolver:maven-resolver-api:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-spi:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-util:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-impl:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-transport-file:1.6.3")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.6.3")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))
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