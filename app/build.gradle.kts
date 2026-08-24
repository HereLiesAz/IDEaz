// Compose Multiplatform 1.11 marks the `compose.runtime` / `compose.material3`
// accessors deprecated at ERROR level, telling you to name the artifacts
// directly. That advice does not hold on the stable line: `org.jetbrains.compose
// .material3:material3` has no 1.11.1 - it is only published as 1.11.0-alphaNN -
// so spelling the coordinates out means either pinning an alpha or mixing
// version lines. The plugin's own accessors resolve the combination JetBrains
// actually ships and tests together, so keep using them and revisit when the
// direct coordinates line up with a stable release.
@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")

import java.util.Properties
import java.io.FileInputStream
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// IDEaz is a Kotlin/Compose Multiplatform app with two targets:
//
//   android  - the phone IDE, the product's original identity.
//   desktop  - the same UI on the JVM. This is not a second product; it is how
//              the app becomes runnable and testable without a handset. The
//              deepest finding of the architecture audit was that nothing here
//              had ever executed on a device, and that a released build shipped
//              with every credential save broken because no one had launched it.
//              A desktop target makes "run the app and click the loop" a thing a
//              developer (or CI) can actually do.
//
// Both targets are JVM, which is what makes this tractable: JGit, OkHttp and
// Retrofit all run unchanged on both.
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }

    jvmToolchain(21)

    // Both targets are JVM, but commonMain still compiles to platform-agnostic
    // metadata and so cannot touch java.* - which rules out almost everything
    // real here (java.io.File, JGit, OkHttp). jvmSharedMain sits between them:
    // shared by android and desktop, and free to use the JVM stdlib. This is
    // where the bulk of IDEaz's logic lives.
    //
    //   commonMain            - platform-agnostic (Compose UI, pure Kotlin)
    //     └── jvmSharedMain   - + the JVM stdlib, JGit, OkHttp, Retrofit
    //           ├── androidMain
    //           └── desktopMain
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmShared = create("jvmSharedMain") { dependsOn(getByName("commonMain")) }
        getByName("androidMain").dependsOn(jvmShared)
        getByName("desktopMain").dependsOn(jvmShared)

        jvmShared.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging.interceptor)
            implementation(libs.retrofit)
            implementation(libs.retrofit2.kotlinx.serialization.converter)
            implementation(libs.org.eclipse.jgit)
            implementation(libs.slf4j.api)
            implementation(libs.bouncycastle.bcprov)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.aznavrail.cmp)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.cmp.lifecycle.runtime)
            implementation(libs.cmp.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.webkit)
            implementation(libs.androidx.preference.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.sora.editor)
            implementation(libs.sora.language.textmate)
            implementation(libs.slf4j.android)
            // The exclude has to be applied on the configuration rather than
            // inline here: the KMP sourceSet DSL takes a Provider, not a
            // configurable dependency notation.
            implementation(libs.google.genai)
            implementation(libs.haze)
        }

        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Robolectric and Mockito need the Android classpath, so these stay on
        // the Android unit-test source set rather than commonTest.
        // Existing tests live under src/androidUnitTest/java; KMP only looks in
        // .../kotlin by default, so the task ran with no sources and reported
        // success while executing nothing.
        getByName("androidUnitTest").kotlin.srcDir("src/androidUnitTest/java")

        getByName("androidUnitTest").dependencies {
            implementation(libs.junit)
            implementation(libs.org.json)
            implementation(libs.mockwebserver)
            implementation(libs.robolectric)
            implementation(libs.mockito.kotlin)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
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
        // compose is supplied by the Compose Multiplatform plugin.
        buildConfig = true
    }
    // KMP puts Android sources under androidMain; point AGP's `main` source set
    // at the Android resources, assets and manifest that moved with them.
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.directories.add("src/androidMain/res")
        assets.directories.add("src/androidMain/assets")
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
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/LICENSE-notice.md")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.md")
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
// google-genai drags in full protobuf-java, and Android cannot host both it and
// protobuf-javalite. This was a dependency-level exclude on google-genai itself
// before the KMP move; the sourceSet DSL has no inline exclude form, so it is
// applied to the source set's own declarable configuration instead.
//
// NOT configurations.all. That also strips protobuf from lint's tool classpath,
// where GooglePlaySdkIndex needs it to parse the Play SDK index - lintAnalyzeDebug
// then dies with NoClassDefFoundError in its static initializer, from a stack
// that names GradleDetector and looks nothing like a dependency problem.
configurations.named("androidMainImplementation") {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

// Security pins live in the ROOT build file's `subprojects` block, not here.
// They were `configurations.all { ... }` in this file, which only ever reached
// :app's own configurations. :webruntime resolves its own graph — it depends on
// :app, so it inherits the *dependencies* but not the resolution rules — and the
// dependency-submission workflow submits BOTH modules' releaseRuntimeClasspath.
// So the SBOM carried :webruntime's unpinned Jackson 2.17.2 alongside :app's
// pinned 2.18.10, and Dependabot kept reporting the 2.17.2 CVEs against a repo
// whose app module had already been patched for them. See build.gradle.kts.

// Constraints only. Every actual dependency lives in the kotlin { sourceSets }
// block above, per target. These force secure versions on transitive deps that
// Dependabot flagged; a constraint sets the version without adding the artifact,
// so Gradle applies it only when resolution actually pulls it in.
dependencies {
    constraints {
        commonMainImplementation(libs.bouncycastle.bcprov) {
            because("CVE timing channel + LDAP injection (Dependabot #29, #27)")
        }
        commonMainImplementation(libs.bouncycastle.bcpkix) {
            because("Broken/risky cryptographic algorithm (Dependabot #26)")
        }
        commonMainImplementation(libs.commons.lang3) {
            because("Uncontrolled recursion CVE (Dependabot #31)")
        }
        commonMainImplementation(libs.apache.httpclient) {
            because("XSS in Apache HttpClient (Dependabot #30)")
        }
    }


    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

abstract class IncrementBuildNumberTask : DefaultTask() {
    @get:Internal
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val skip: Property<Boolean>

    @TaskAction
    fun increment() {
        // Skipped whenever `buildNumber` (see above) already has a real source
        // for the build component - CI's -PversionBuild, or a local git commit
        // count - which is every normal case. The file counter this writes is
        // consulted only as buildNumber's last-resort fallback for a checkout
        // with no usable git history at all, so bumping it on every single
        // local assemble/bundle/install regardless dirtied version.properties
        // (and busted the configuration cache) for a value nothing was reading.
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
    // Only the git-less fallback case needs this file counter to advance at
    // all - see buildNumber's derivation and IncrementBuildNumberTask.increment().
    skip.set(versionBuildOverride != null || gitCommitCount != null)
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

    // Every CI hang in this project's history was fixed by patching the one bad
    // test - a leaked CoroutineScope, a Dispatchers.Main post under Robolectric's
    // PAUSED looper - while the property that let a single bad test take down the
    // entire build was never touched: one forked JVM for the whole module, no
    // per-test timeout. So the next leak cost another 20-minute red build and
    // another round of thread-dump forensics.
    //
    // forkEvery bounds the blast radius to one worker. The timeout turns a hang
    // into a named failing test instead of a silent wall-clock timeout with no
    // output. Together they are what should make the permanently-armed jcmd
    // watchdog in the workflows deletable - which is the real measure of whether
    // this suite is healthy.
    forkEvery = 40
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    timeout.set(Duration.ofMinutes(10))
}

// Desktop packaging. `./gradlew :app:run` launches the IDE on the JVM - the
// fastest way to exercise the tap -> prompt -> edit -> reload loop without a
// handset, which is the whole reason this target exists.
compose.desktop {
    application {
        mainClass = "com.hereliesaz.ideaz.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "IDEaz"
            packageVersion = "$major.$minor.$patch"
        }
    }
}
