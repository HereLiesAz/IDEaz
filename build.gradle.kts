plugins {
    // Declared here with `apply false` so every plugin's version is resolved once,
    // at the root, and subprojects can apply them by alias without a version. The
    // Kotlin Multiplatform plugin in particular must be declared this way: AGP
    // puts Kotlin on the build classpath itself, so a versioned request from a
    // subproject fails with "already on the classpath with an unknown version".
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Dependency resolution rules for EVERY module, not just :app.
//
// These lived in app/build.gradle.kts as `configurations.all { ... }`. That form
// is per-project: it covered :app's configurations and nothing else. :webruntime
// depends on :app, so it inherits the dependencies but resolves them under its
// own rules — which were none. Its releaseRuntimeClasspath therefore kept Jackson
// at the version google-genai requests (2.17.2) while :app's was forced to
// 2.18.10, and dependency-submission.yml submits both modules, so the SBOM
// reported a vulnerable Jackson that :app had already been patched against.
// Seven Dependabot alerts (#56, #57, #58, #59, #81, #83, #95) tracked exactly
// that copy.
//
// `subprojects` puts the rules where a new module inherits them by default,
// rather than by someone remembering to copy this block into it.
subprojects {
    configurations.all {
        resolutionStrategy {
            force("androidx.concurrent:concurrent-futures:1.3.0")
            force("androidx.concurrent:concurrent-futures-ktx:1.3.0")

            // Security: pin transitive libraries flagged by Dependabot to patched versions.
            // Jackson (via google-genai) and bcprov (GithubSecretBox) ship in the APK.
            // The remaining copies are limited to test / lint-tooling classpaths:
            // bcpkix/bcutil via Robolectric or AGP, commons-lang3 via AGP's lint tools,
            // and Netty via grpc-netty on the unit-test classpath. Pin every occurrence;
            // .github/workflows/dependency-submission.yml submits only the release
            // runtime graph (what can actually ship), while local/CI resolution stays
            // hardened on every configuration, in every module (see the `subprojects`
            // comment above this block).
            // The earlier foojay-resolver removal in settings.gradle.kts did not address
            // these dependencies because that plugin was never their source.
            force(
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.apache.commons:commons-lang3:3.20.0",
                "com.google.protobuf:protobuf-java:4.36.0",
                "com.google.protobuf:protobuf-kotlin:4.36.0",
            )
            dependencySubstitution {
                substitute(module("com.google.protobuf:protobuf-javalite"))
                    .using(module("com.google.protobuf:protobuf-java:4.36.0"))
                    .because("Android cannot have both javalite and full protobuf-java on the same classpath")
            }
            eachDependency {
                when {
                    // Jackson arrives as core/databind/annotations + datatype modules and a
                    // BOM; pin the whole family to a patched 2.18.x line - currently 2.18.10,
                    // the latest patch. See docs/TODO.md's Production Readiness section for
                    // the alert history.
                    //
                    // Two early attempts (2026-08-16, 2026-08-17) reverted this same version
                    // bump - a simultaneous Netty bump hanging CI, then an opaque, stack-
                    // trace-free `:app:packageDebug` failure. Neither reproduced once this was
                    // pinned to 2.18.10 in isolation (2026-08-21): `packageDebug`,
                    // `checkDebugDuplicateClasses`, and a full `./gradlew build` all passed,
                    // confirmed by `:app:dependencies` that every Jackson module resolved to
                    // 2.18.10 with no conflicts.
                    //
                    // "Every Jackson module" turned out to mean :app's, only. This was
                    // `configurations.all` in app/build.gradle.kts, a per-project block, so it
                    // never reached :webruntime - which pulled Jackson 2.17.2 straight from
                    // google-genai's own requested version. dependency-submission.yml submits
                    // both modules' releaseRuntimeClasspath, so the SBOM carried both versions,
                    // and Dependabot kept flagging seven CVEs (#56, #57, #58, #59, #81, #83,
                    // #95) against a Jackson this repo had already patched in :app (2026-08-23).
                    // Moving this whole `resolutionStrategy` to `subprojects` (see file-level
                    // comment above) is what actually closes them.
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
}
