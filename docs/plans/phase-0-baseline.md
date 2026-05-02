# Phase 0 — Build Baseline

**HEAD:** `420646349881b3215d8d020db8e0a33d550d490f`
**Branch:** `claude/priceless-tesla-d309a4`
**Captured:** 2026-05-01
**Host:** Windows 11, git-bash, Gradle 9.4.1, Java 21.0.9 (system), Android SDK present

This document captures the actual current state of the build at HEAD `4206463`, before
any Phase 0 triage changes. Subsequent tasks should diff their output against this to
distinguish pre-existing failures from regressions they introduce.

---

## Summary

| Task | Result |
| --- | --- |
| `./gradlew :app:assembleDebug` | FAIL (configuration-phase error) |
| `./gradlew :app:testDebugUnitTest` | FAIL (same configuration-phase error) |
| Deprecation warnings (`is deprecated`) | 0 (build dies before compilation) |

Both tasks fail at Gradle's **configuration phase** with an identical error:
the version catalog (`gradle/libs.versions.toml`) references a `hermes` version
that is not declared. Because this kills configuration, no Kotlin/Java compilation
runs, which is why no deprecation warnings are emitted in either log.

This means the "real" compile errors and deprecation warnings the rest of Phase 0
intends to fix are currently **masked** by the catalog error. Task 2 onward will
need to clear this configuration error before deeper failures become visible.

---

## Step 1.1 — `./gradlew :app:assembleDebug`

**Command:** `./gradlew :app:assembleDebug 2>&1 | tee /tmp/baseline-assembleDebug.log`
**Result:** **BUILD FAILED in 33s** (exit code 1)
**Log:** captured to `/tmp/baseline-assembleDebug.log` (37 lines, transcribed below)

### Full output

```
Starting a Gradle Daemon, 4 stopped Daemons could not be reused, use --status for details
Calculating task graph as no cached configuration is available for tasks: :app:assembleDebug

[Incubating] Problems report is available at: file:///C:/Users/azrie/OneDrive/Documents/GitHub/IDEaz/.claude/worktrees/priceless-tesla-d309a4/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog libs, version reference 'hermes' doesn't exist.

    Reason: Dependency 'com.facebook.react:hermes-android' references version 'hermes' which doesn't exist.

    Possible solutions:
      1. Declare 'hermes' in the catalog.
      2. Use one of the following existing versions: 'activityCompose', 'aetherTransportFile', 'agp', 'appcompat', 'aznavrail', 'chaquopy', 'composeBom', 'composeUnstyledCore', 'coreKtx', 'documentfile', 'espressoCore', 'generativeai', 'glassfishEl', 'googleGenai', 'guava', 'haze', 'hiddenapibypass', 'javaxAnnotation', 'jaxbApi', 'jna', 'json', 'junit', 'junitVersion', 'kotlin', 'kotlinCompilerEmbeddable', 'kotlinxCoroutinesCore', 'kotlinxSerializationJson', 'kxml2', 'lifecycleRuntimeKtx', 'localbroadcastmanager', 'loggingInterceptor', 'materialIconsExtended', 'mavenCore', 'mavenResolver', 'mavenResolverHttp', 'mockwebserver', 'navigationCompose', 'nbJavac', 'okhttp', 'orgEclipseJgit', 'preferenceKtx', 'r8', 'reactNative', 'retrofit', 'retrofit2KotlinxSerializationConverter', 'robolectric', 'roomCompiler', 'runtimeLivedata', 'scalaCompiler', 'slf4jAndroid', 'slf4jApi', 'slf4jSimple', 'smali', 'sodium', 'soloader', 'soraEditor', 'validationApi', 'wagonHttp', or 'zipline'.

    For more information, please refer to https://docs.gradle.org/9.4.1/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog libs, version reference 'hermes' doesn't exist.

      Reason: Dependency 'com.facebook.react:hermes-android' references version 'hermes' which doesn't exist.

      Possible solutions:
        1. Declare 'hermes' in the catalog.
        2. Use one of the following existing versions: 'activityCompose', 'aetherTransportFile', 'agp', 'appcompat', 'aznavrail', 'chaquopy', 'composeBom', 'composeUnstyledCore', 'coreKtx', 'documentfile', 'espressoCore', 'generativeai', 'glassfishEl', 'googleGenai', 'guava', 'haze', 'hiddenapibypass', 'javaxAnnotation', 'jaxbApi', 'jna', 'json', 'junit', 'junitVersion', 'kotlin', 'kotlinCompilerEmbeddable', 'kotlinxCoroutinesCore', 'kotlinxSerializationJson', 'kxml2', 'lifecycleRuntimeKtx', 'localbroadcastmanager', 'loggingInterceptor', 'materialIconsExtended', 'mavenCore', 'mavenResolver', 'mavenResolverHttp', 'mockwebserver', 'navigationCompose', 'nbJavac', 'okhttp', 'orgEclipseJgit', 'preferenceKtx', 'r8', 'reactNative', 'retrofit', 'retrofit2KotlinxSerializationConverter', 'robolectric', 'roomCompiler', 'runtimeLivedata', 'scalaCompiler', 'slf4jAndroid', 'slf4jApi', 'slf4jSimple', 'smali', 'sodium', 'soloader', 'soraEditor', 'validationApi', 'wagonHttp', or 'zipline'.

      For more information, please refer to https://docs.gradle.org/9.4.1/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

BUILD FAILED in 33s
Configuration cache entry stored.
```

### Notes

- A first run of `assembleDebug` failed earlier with a transient toolchain-download
  error (Gradle attempting to extract a JetBrains JDK from `~/.gradle/jdks/` while a
  stale `.lock` file was held). Removing the stale `.lock` files in `~/.gradle/jdks/`
  resolved that and produced the catalog error above, which is the real baseline.
  This environment quirk is not part of the source-code baseline.
- The catalog error fingerprint is unambiguous: `com.facebook.react:hermes-android`
  is declared in the catalog (or `app/build.gradle.kts` / a convention plugin)
  pointing to a `hermes` version key that has not been added to
  `gradle/libs.versions.toml`'s `[versions]` block.

---

## Step 1.2 — `./gradlew :app:testDebugUnitTest`

**Command:** `./gradlew :app:testDebugUnitTest 2>&1 | tee /tmp/baseline-testDebugUnitTest.log`
**Result:** **BUILD FAILED in 957ms** (exit code 1)
**Log:** captured to `/tmp/baseline-testDebugUnitTest.log` (36 lines, transcribed below)

The failure is the same configuration-phase catalog error as `assembleDebug` —
configuration runs before any test compilation, so the test task never starts.

### Full output

```
Calculating task graph as no cached configuration is available for tasks: :app:testDebugUnitTest

[Incubating] Problems report is available at: file:///C:/Users/azrie/OneDrive/Documents/GitHub/IDEaz/.claude/worktrees/priceless-tesla-d309a4/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
org.gradle.api.InvalidUserDataException: Invalid catalog definition:
  - Problem: In version catalog libs, version reference 'hermes' doesn't exist.

    Reason: Dependency 'com.facebook.react:hermes-android' references version 'hermes' which doesn't exist.

    Possible solutions:
      1. Declare 'hermes' in the catalog.
      2. Use one of the following existing versions: 'activityCompose', 'aetherTransportFile', 'agp', 'appcompat', 'aznavrail', 'chaquopy', 'composeBom', 'composeUnstyledCore', 'coreKtx', 'documentfile', 'espressoCore', 'generativeai', 'glassfishEl', 'googleGenai', 'guava', 'haze', 'hiddenapibypass', 'javaxAnnotation', 'jaxbApi', 'jna', 'json', 'junit', 'junitVersion', 'kotlin', 'kotlinCompilerEmbeddable', 'kotlinxCoroutinesCore', 'kotlinxSerializationJson', 'kxml2', 'lifecycleRuntimeKtx', 'localbroadcastmanager', 'loggingInterceptor', 'materialIconsExtended', 'mavenCore', 'mavenResolver', 'mavenResolverHttp', 'mockwebserver', 'navigationCompose', 'nbJavac', 'okhttp', 'orgEclipseJgit', 'preferenceKtx', 'r8', 'reactNative', 'retrofit', 'retrofit2KotlinxSerializationConverter', 'robolectric', 'roomCompiler', 'runtimeLivedata', 'scalaCompiler', 'slf4jAndroid', 'slf4jApi', 'slf4jSimple', 'smali', 'sodium', 'soloader', 'soraEditor', 'validationApi', 'wagonHttp', or 'zipline'.

    For more information, please refer to https://docs.gradle.org/9.4.1/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.
> Invalid catalog definition:
    - Problem: In version catalog libs, version reference 'hermes' doesn't exist.

      Reason: Dependency 'com.facebook.react:hermes-android' references version 'hermes' which doesn't exist.

      Possible solutions:
        1. Declare 'hermes' in the catalog.
        2. Use one of the following existing versions: 'activityCompose', 'aetherTransportFile', 'agp', 'appcompat', 'aznavrail', 'chaquopy', 'composeBom', 'composeUnstyledCore', 'coreKtx', 'documentfile', 'espressoCore', 'generativeai', 'glassfishEl', 'googleGenai', 'guava', 'haze', 'hiddenapibypass', 'javaxAnnotation', 'jaxbApi', 'jna', 'json', 'junit', 'junitVersion', 'kotlin', 'kotlinCompilerEmbeddable', 'kotlinxCoroutinesCore', 'kotlinxSerializationJson', 'kxml2', 'lifecycleRuntimeKtx', 'localbroadcastmanager', 'loggingInterceptor', 'materialIconsExtended', 'mavenCore', 'mavenResolver', 'mavenResolverHttp', 'mockwebserver', 'navigationCompose', 'nbJavac', 'okhttp', 'orgEclipseJgit', 'preferenceKtx', 'r8', 'reactNative', 'retrofit', 'retrofit2KotlinxSerializationConverter', 'robolectric', 'roomCompiler', 'runtimeLivedata', 'scalaCompiler', 'slf4jAndroid', 'slf4jApi', 'slf4jSimple', 'smali', 'sodium', 'soloader', 'soraEditor', 'validationApi', 'wagonHttp', or 'zipline'.

      For more information, please refer to https://docs.gradle.org/9.4.1/userguide/version_catalog_problems.html#undefined_version_reference in the Gradle documentation.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

BUILD FAILED in 957ms
Configuration cache entry stored.
```

---

## Step 1.3 — Deprecation warnings (`is deprecated`)

`grep -c "is deprecated"` against both baseline logs:

```
/tmp/baseline-assembleDebug.log:0
/tmp/baseline-testDebugUnitTest.log:0
```

**Zero deprecation warnings recorded** — but only because both builds die in the
configuration phase. Compilation never runs, so kotlinc / javac / AGP never get a
chance to emit any. This number will balloon once the catalog error is fixed in
Task 2; that delta should be expected, not treated as a regression.

---

## Implications for the rest of Phase 0

1. **Task 2 must fix the version catalog first.** Until `hermes` is either added
   to `[versions]` in `gradle/libs.versions.toml` or the offending
   `com.facebook.react:hermes-android` dependency declaration is removed/updated,
   no other Phase 0 task can produce a meaningful build/test signal.
2. **Real compile errors and deprecation warnings are still hidden.** Whatever
   the older repo-root logs (`compile_log.txt`, `verify_build.log`,
   `test_log.txt`) reported was captured against a different state and is not
   reproducible from HEAD `4206463` until the catalog is fixed. The first task
   that gets past configuration should re-establish a fresh post-fix baseline
   (compile errors + first round of deprecation warnings) before further
   triage.
3. **Environment caveat.** Running `./gradlew` on a fresh worktree may need a
   one-time cleanup: stop daemons (`./gradlew --stop`) and remove stale locks
   under `~/.gradle/jdks/*.lock` if Gradle aborts trying to extract the
   JetBrains JDK toolchain. This is not a source-code issue.
