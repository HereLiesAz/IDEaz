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

val major = versionProps.getProperty("major", "1").toInt()
val minor = versionProps.getProperty("minor", "0").toInt()
val patch = versionProps.getProperty("patch", "0").toInt()
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
            // R8 strips unused library code (notably most of bcprov, of which only
            // the X25519/Salsa20/Poly1305/Blake2b primitives are reached). Keep
            // rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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

        // Security: pin transitive libraries flagged by Dependabot to patched versions.
        // Only Jackson actually ships in the APK (pulled by google-genai into
        // releaseRuntimeClasspath); the rest live solely in test / lint-tooling
        // classpaths — BouncyCastle via Robolectric, commons-lang3 (and BouncyCastle)
        // via AGP's androidLintTool, Netty via grpc-netty on the unit-test classpath.
        // They never reach a device, but they stay in the dependency graph, so pin
        // them everywhere to clear the alerts. (The earlier foojay-resolver removal in
        // settings.gradle.kts did NOT address these — that plugin was misattributed as
        // the source; these come from real app dependencies.)
        force(
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcutil-jdk18on:1.84",
            "org.apache.commons:commons-lang3:3.20.0",
            "com.google.protobuf:protobuf-java:4.35.1",
            "com.google.protobuf:protobuf-kotlin:4.35.1",
        )
        dependencySubstitution {
            substitute(module("com.google.protobuf:protobuf-javalite"))
                .using(module("com.google.protobuf:protobuf-java:4.35.1"))
                .because("Android cannot have both javalite and full protobuf-java on the same classpath")
        }
        eachDependency {
            when {
                // Jackson arrives as core/databind/annotations + datatype modules and a
                // BOM; pin the whole family to a patched 2.18.x. Use 2.18.7 — the java8
                // datatype modules (jackson-datatype-jdk8/jsr310) stop there, while
                // core/databind go to 2.18.8, so 2.18.7 is the latest version every
                // module publishes (and still > the 2.18.6 fix).
                requested.group.startsWith("com.fasterxml.jackson") ->
                    useVersion("2.18.7")
                // Netty arrives as ~11 modules via grpc-netty (unit-test only). Pin the
                // io.netty group to the latest patched 4.1.x — staying off 4.2.x, which
                // grpc-netty does not support. (netty-tcnative tracks a separate scheme.)
                requested.group == "io.netty" &&
                    requested.name.startsWith("netty-") &&
                    !requested.name.startsWith("netty-tcnative") ->
                    useVersion("4.1.134.Final")
            }
        }
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
    // Used for libsodium-compatible crypto_box_seal (GithubSecretBox) to encrypt
    // GitHub Actions secrets. R8 strips the unused remainder of bcprov in release.
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.google.genai) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation(libs.google.ai.edge.aicore)
    implementation(libs.mediapipe.tasks.genai) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
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
