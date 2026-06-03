import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}

val major = versionProps.getProperty("major").toInt()
val minor = versionProps.getProperty("minor").toInt()
val patch = versionProps.getProperty("patch").toInt()
val buildNumber = versionProps.getProperty("build", "0").toInt() + 1

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.hereliesaz.ideaz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 30

        targetSdk = 37
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
            // CI sets KEYSTORE_FILE (and matching password/alias env vars) to sign with
            // the real release keystore. Local builds without those env vars fall back
            // to the debug keystore so `./gradlew build` and `assembleRelease` work
            // out of the box. The release signingConfig is only used when fully
            // populated; otherwise the debug config takes over.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        force("androidx.concurrent:concurrent-futures:1.3.0")
        force("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    }
}

dependencies {
    // Force secure versions on JGit's vulnerable transitive deps (Dependabot
    // alerts #26/#27/#29 Bouncy Castle, #30 Apache HttpClient, #31 commons-lang3).
    // Constraints set the version without adding the dep; Gradle applies them
    // only when the transitive resolution actually pulls the artifact.
    constraints {
        implementation(libs.bouncycastle.bcprov) {
            because("CVE timing channel + LDAP injection (Dependabot #29, #27)")
        }
        implementation(libs.bouncycastle.bcpkix) {
            because("Broken/risky cryptographic algorithm (Dependabot #26)")
        }
        implementation(libs.commons.lang3) {
            because("Uncontrolled recursion CVE (Dependabot #31)")
        }
        implementation(libs.apache.httpclient) {
            because("XSS in Apache HttpClient (Dependabot #30)")
        }
    }

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
    implementation(libs.google.genai)
    implementation(libs.google.ai.edge.aicore)
    implementation(libs.androidx.localbroadcastmanager)
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

tasks.register("incrementBuildNumber") {
    val versionFile = rootProject.file("version.properties")
    outputs.upToDateWhen { false }
    doFirst {
        val props = Properties()
        if (versionFile.exists()) {
            versionFile.inputStream().use { props.load(it) }
        }
        val currentBuild = props.getProperty("build", "0").toInt()
        props.setProperty("build", (currentBuild + 1).toString())
        versionFile.outputStream().use { props.store(it, null) }
    }
}

tasks.configureEach {
    if (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("install")) {
        dependsOn("incrementBuildNumber")
    }
}
