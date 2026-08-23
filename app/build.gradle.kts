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

// versionCode override for CI: pass `-PversionBuild=<n>` (CI passes
// `git rev-list --count HEAD`, a value that only ever grows).
// (findProperty returns Any?; `?.toString()` avoids a ClassCastException if a plugin
// ever sets the property as a non-String type.)
val versionBuildOverride = project.findProperty("versionBuild")?.toString()?.toIntOrNull()

// When NOT supplied (an ordinary local `./gradlew assembleDebug`/`bundleRelease`),
// this used to fall back to a file-driven counter (version.properties' `build` + 1)
// that is entirely independent of CI's `git rev-list --count HEAD` - the two
// numbers have no relationship to each other and can drift in either direction
// (e.g. CI build 326 vs a local counter that only reached 92), so the old
// "stays well above existing file-driven builds" comment was simply false: it
// depended on which counter happened to be bigger that day, not on anything the
// arithmetic guaranteed. Computing the SAME git-commit-count locally too gives
// both lineages one real source of truth, so a local build's versionCode is
// never lower than the latest CI build's - actually preventing
// INSTALL_FAILED_VERSION_DOWNGRADE instead of just claiming to. Falls back to
// the old file counter only if this checkout has no usable git history (e.g.
// a source zip with no .git directory).
// providers.exec (not a raw ProcessBuilder) is deliberate: the configuration
// cache tracks external processes started this way as a proper build input,
// so it can safely cache and re-run configuration - a bare ProcessBuilder call
// here made configuration-cache storage fail outright ("Starting an external
// process 'git rev-list --count HEAD' during configuration time is
// unsupported"), breaking every build (CI's dependency-submission and check
// jobs included) the moment configuration caching was enabled.
val gitCommitCount = runCatching {
    providers.exec {
        workingDir = rootProject.projectDir
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull()
val buildNumber = versionBuildOverride
    ?: gitCommitCount
    ?: (versionProps.getProperty("build", "0").toInt() + 1)

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.hereliesaz.ideaz"
    compileSdk = 37

    // Dynamic feature modules delivered with this base app. :webruntime is
    // install-time + fused, so its assets remain present in a plain assembleRelease
    // APK (the GitHub-release distribution channel) and are reachable via the base
    // AssetManager with no SplitInstall call. See docs/build_pipeline.md.
    dynamicFeatures += setOf(":webruntime")

    defaultConfig {
        applicationId = "com.hereliesaz.ideaz"
        minSdk = 30

        targetSdk = 37
        // One packed formula for both local and CI builds. `buildNumber` is the git
        // commit count (either passed in via -PversionBuild by CI, or computed the
        // same way locally above) for both lineages, so the two never diverge - the
        // code is monotonic and never downgrades an installed build or gets rejected
        // by Play. `buildNumber` isn't clamped to patch's 100-wide digit slot, so a
        // large commit count spills into higher digits (e.g. patch=66, build=326 ->
        // ...6926, not ...66326) - harmless for the arithmetic (plain addition, not
        // string concatenation, so the total stays correctly monotonic), just not
        // cleanly human-readable past two build digits.
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

    androidResources {
        // The bundled Android template ships .github/workflows/build-apk.yml
        // (assets/templates/android). aapt's default asset filter contains ".*",
        // which drops every dot-prefixed entry — the workflow would never reach
        // the APK and scaffolded projects would have no CI. This is the default
        // list minus ".*"; .git and friends stay excluded by their own entries.
        ignoreAssetsPatterns.clear()
        ignoreAssetsPatterns.addAll(
            listOf(
                "!.svn", "!.git", "!.gitattributes", "!.gitignore", "!.gitkeep",
                "!.ds_store", "!*.scc", "<dir>_*", "!CVS", "!thumbs.db",
                "!picasa.ini", "!*~",
            )
        )
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
        // Jackson (via google-genai) and bcprov (GithubSecretBox) ship in the APK.
        // The remaining copies are limited to test / lint-tooling classpaths:
        // bcpkix/bcutil via Robolectric or AGP, commons-lang3 via AGP's lint tools,
        // and Netty via grpc-netty on the unit-test classpath. Pin every occurrence;
        // the release-runtime-only dependency submission below reports only what can
        // ship, while local/CI resolution remains hardened on every configuration.
        // The earlier foojay-resolver removal in settings.gradle.kts did not address
        // these dependencies because that plugin was never their source.
        force(
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.85",
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
                // BOM; pin the whole family to a patched 2.18.x - currently 2.18.10, the
                // latest patch in that line, closing 9 Dependabot alerts (CVE-2026-54512/
                // 54514/54515/59889 and siblings; see docs/TODO.md's Production Readiness
                // section for the full list).
                //
                // Two earlier attempts (2026-08-16, 2026-08-17) reverted this same move -
                // a simultaneous Netty bump hanging CI, then an opaque, stack-trace-free
                // `:app:packageDebug` failure with nothing to diagnose from the CI log
                // alone. This attempt (2026-08-21) reproduced neither locally, with
                // `--stacktrace` on: `:app:packageDebug`, `:app:checkDebugDuplicateClasses`,
                // and a full `./gradlew build` all pass clean at 2.18.10, and
                // `:app:dependencies --configuration debugRuntimeClasspath` confirms every
                // Jackson module resolves to 2.18.10 with no conflicts. Whatever caused the
                // prior failure looks CI-runner-specific rather than a real incompatibility
                // - if `packageDebug` fails again in CI at this exact pin, that's the signal
                // it's worth chasing as an environment issue, not a Jackson one.
                requested.group.startsWith("com.fasterxml.jackson") ->
                    useVersion("2.18.10")
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
    // removed with the on-device model / overlay subsystems
    // removed with the on-device model subsystem
    // removed with the on-device model / overlay subsystems
    implementation(libs.aznavrail) {
        exclude(group = "com.github.HereLiesAz.AzNavRail", module = "aznavrail-cmp-wasm-js")
        exclude(group = "com.github.HereLiesAz.AzNavRail", module = "aznavrail-cmp-desktop")
        exclude(group = "com.github.HereLiesAz.AzNavRail", module = "aznavrail-cmp-android")
    }
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
    // removed with the on-device model / overlay subsystems

}

abstract class IncrementBuildNumberTask : DefaultTask() {
    @get:Internal
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val skip: Property<Boolean>

    @TaskAction
    fun increment() {
        // CI supplies the build component via -PversionBuild (commit count); leave
        // version.properties untouched in that case so the checkout stays clean.
        if (skip.get()) return
        val file = versionFile.get().asFile
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        val currentBuild = props.getProperty("build", "0").toInt()
        props.setProperty("build", (currentBuild + 1).toString())
        file.outputStream().use { props.store(it, null) }
    }
}

tasks.register<IncrementBuildNumberTask>("incrementBuildNumber") {
    versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
    skip.set(versionBuildOverride != null)
    outputs.upToDateWhen { false }
}

tasks.configureEach {
    if (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("install")) {
        dependsOn("incrementBuildNumber")
    }
}

// Pin an explicit, modest test-JVM heap instead of leaving it to the JVM's
// own memory-derived default, which crashed the forked test executor under
// build-and-release.yml's heavier combined release+debug build (see PR #850).
tasks.withType<Test>().configureEach {
    maxHeapSize = "1536m"
}
