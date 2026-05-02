# Phase 0: Triage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bring the IDEaz codebase to a clean-compiling, deprecation-warning-free state by deleting all code paths that have been removed from scope per `docs/plans/2026-05-01-ideaz-revival-design.md`.

**Architecture:** Pure deletion + targeted fixes. No new features. Each task is a self-contained branch-or-commit unit that ends with a green build. The order goes from most-isolated deletions (smallest blast radius) to most-entangled (BuildService + BuildDelegate, which touch a lot).

**Tech Stack:** Kotlin, Gradle 9, AGP 9, Android Studio toolchain. The plan uses `./gradlew` for builds.

---

## Pre-flight context for the executor

You are working on the abandoned-and-now-revived **IDEaz** Android project. Read `docs/plans/2026-05-01-ideaz-revival-design.md` *before* starting. The design doc decides what stays and what goes; this plan implements those decisions.

The codebase has been touched by automated agents over the past year. Logs in the repo root (`compile_log.txt`, `verify_build.log`, `test_log.txt`) are **stale** — they were captured at different points and may not reflect current state. **Task 1 establishes ground truth** before any changes are made.

**Conventions:**
- Branch: work happens on the current branch (`claude/priceless-tesla-d309a4`). Do *not* create new branches per task.
- Commit messages: imperative, scoped (`refactor(buildlogic): drop on-device toolchain`).
- Each task ends with a passing `./gradlew :app:assembleDebug` AND a commit. No exceptions — never commit a broken build.
- If a task's deletion creates compile errors elsewhere (it will), fix them in the same task. Don't punt to a later task.
- If you discover something that should be deleted but isn't on this plan, add a note to the task and ask before deleting.
- The Kotlin source root is `app/src/main/kotlin/com/hereliesaz/ideaz/`. Tests are in `app/src/test/java/com/hereliesaz/ideaz/`.
- Use `Grep` to find all references to a class before deleting it. Don't trust this plan to enumerate every reference.

**Skills to invoke before specific actions:**
- Before any code change: `superpowers:test-driven-development` (where unit tests are involved) and `superpowers:verification-before-completion` (before marking a task done).
- Before commits: `superpowers:verification-before-completion` — never claim done without a green build.
- If the build breaks in unexpected ways: `superpowers:systematic-debugging`.

---

## Task 1: Establish baseline ground truth

**Goal:** Capture the *actual current state* of the build before changing anything. Stale logs in the repo root are not trustworthy.

**Files:**
- Create: `docs/plans/phase-0-baseline.md`

**Step 1.1: Run baseline build**

Run: `./gradlew :app:assembleDebug 2>&1 | tee /tmp/baseline-assembleDebug.log`
Expected: Either green or red. Either is fine — we just need to know.

**Step 1.2: Run baseline tests**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tee /tmp/baseline-testDebugUnitTest.log`
Expected: Either green or red.

**Step 1.3: Document baseline**

Write `docs/plans/phase-0-baseline.md` containing:
- Output of `git rev-parse HEAD` at the top.
- Whether `assembleDebug` passes / fails. If fails, paste the *full* error output (compile errors, exception stack).
- Whether `testDebugUnitTest` passes / fails. Same treatment.
- A list of any "deprecated" warnings emitted (search the log for `is deprecated`).

This file is the executor's reference for "what was already broken before I started."

**Step 1.4: Commit baseline**

```bash
git add docs/plans/phase-0-baseline.md
git commit -m "docs(phase-0): record build baseline before triage"
```

**Acceptance:** `phase-0-baseline.md` exists with full output. Committed.

---

## Task 2: Delete React Native

**Why:** Per design, RN is permanently out of scope. RN brings 60+ `.so` libs that block stripping and dominate APK size.

**Files to delete:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/react/ReactNativeActivity.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/react/IdeazNativeModule.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/react/IdeazReactPackage.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ReactNativeBuildStep.kt`
- `app/src/test/java/com/hereliesaz/ideaz/buildlogic/ReactNativeBuildStepTest.kt`
- Asset templates: `app/src/main/assets/templates/react_native/` (whole directory)

**Files to modify:**
- `app/build.gradle.kts` — remove `react.android`, `hermes.android`, `soloader` dependencies (around lines 231–233)
- `gradle/libs.versions.toml` — remove `reactNative`, `hermes` (broken — undefined version), `soloader` library entries and version refs
- `app/src/main/AndroidManifest.xml` — remove the `<activity android:name=".react.ReactNativeActivity" .../>` block (lines 50–52)
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt` — remove RN detection branch (`Grep` for "react" or "reactNative")
- `app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt` — remove `REACT_NATIVE` enum value
- `app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt` — remove any `ReactNativeBuildStep` invocation

**Step 2.1: Find all RN references**

Run: `Grep` with pattern `(?i)reactnative|react.native|react_native|hermes|soloader` over `app/src/`.

Note every file that references RN. Anything not on the modify list above must be tracked and fixed in this task.

**Step 2.2: Delete RN source files**

```bash
rm -rf app/src/main/kotlin/com/hereliesaz/ideaz/react/
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ReactNativeBuildStep.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/ReactNativeBuildStepTest.kt
rm -rf app/src/main/assets/templates/react_native/
```

**Step 2.3: Remove RN deps**

Edit `app/build.gradle.kts` — delete the three lines:
```
implementation(libs.react.android)
implementation(libs.hermes.android)
implementation(libs.soloader)
```

Edit `gradle/libs.versions.toml` — delete:
- Version refs: `reactNative`, `soloader`, and the broken `hermes` (no version line exists for it; just remove the library reference)
- Library entries: `react-android`, `hermes-android`, `soloader`

**Step 2.4: Remove ReactNativeActivity from manifest**

Edit `app/src/main/AndroidManifest.xml` — delete the activity block (lines 50–52 in current state).

**Step 2.5: Fix dangling references**

For every file Grep found in Step 2.1 that's not on the modify list above:
- If it imports `com.hereliesaz.ideaz.react.*`: remove the import + any code that uses it.
- If it has an `if (projectType == ProjectType.REACT_NATIVE)` branch: remove the branch.
- If it has an RN-only field/method: remove it.

In `models/ProjectType.kt`: remove the `REACT_NATIVE` enum case.
In `utils/ProjectAnalyzer.kt`: remove RN detection logic.
In `services/BuildService.kt`: remove `ReactNativeBuildStep` references.

**Step 2.6: Verify build**

Run: `./gradlew :app:assembleDebug 2>&1 | tee /tmp/task-2-build.log`
Expected: BUILD SUCCESSFUL. Zero compile errors.

If errors remain about RN-related symbols, repeat Step 2.5 until clean.

**Step 2.7: Verify tests**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tee /tmp/task-2-tests.log`
Expected: tests compile and pass (allow pre-existing failures from `phase-0-baseline.md`, but no *new* failures).

**Step 2.8: Commit**

```bash
git add -A
git commit -m "refactor: drop React Native support

Out of scope per ideaz-revival design. Deletes the react/ package,
ReactNativeBuildStep, RN templates, and unwires from ProjectAnalyzer
and AndroidManifest.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green, tests no worse than baseline, no `react`/`hermes`/`soloader` references survive (`Grep` confirms zero hits in `app/src/`).

---

## Task 3: Delete Flutter

**Why:** Per design, Flutter is permanently out of scope.

**Files to delete:**
- `app/src/main/assets/templates/flutter/` (whole directory)

**Files to modify:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt` — remove Flutter detection
- `app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt` — remove `FLUTTER` enum value
- Any `BuildService.kt` / `BuildDelegate.kt` flutter branches

**Step 3.1: Find all flutter references**

Run: `Grep` with pattern `(?i)flutter` over `app/src/`.

**Step 3.2: Delete flutter assets**

```bash
rm -rf app/src/main/assets/templates/flutter/
```

**Step 3.3: Fix dangling references**

For every file Grep found:
- Remove flutter detection branches.
- Remove `ProjectType.FLUTTER` references.

**Step 3.4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

**Step 3.5: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 3.6: Commit**

```bash
git add -A
git commit -m "refactor: drop Flutter support

Out of scope per ideaz-revival design. Removes flutter templates
and detection logic.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Zero `flutter` hits in `app/src/`.

---

## Task 4: Delete Python

**Why:** Per design, Python is permanently out of scope.

**Files to delete:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/PythonInjector.kt`
- `app/src/main/assets/templates/python/` (whole directory)

**Files to modify:**
- `gradle/libs.versions.toml` — remove `chaquopy` version + `chaquopy-runtime`, `chaquopy-target` library entries
- `app/src/main/kotlin/com/hereliesaz/ideaz/models/ProjectType.kt` — remove `PYTHON` enum value
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAnalyzer.kt` — remove Python detection
- Any `BuildService.kt` / `BuildDelegate.kt` python branches

**Step 4.1: Find all python references**

Run: `Grep` with pattern `(?i)python|chaquopy|pythonInjector` over `app/src/` and `gradle/`.

**Step 4.2: Delete python source + assets**

```bash
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/PythonInjector.kt
rm -rf app/src/main/assets/templates/python/
```

**Step 4.3: Remove chaquopy from version catalog**

Edit `gradle/libs.versions.toml` — remove `chaquopy = "..."` version and the `chaquopy-runtime` / `chaquopy-target` library entries.

**Step 4.4: Fix dangling references**

Remove `ProjectType.PYTHON`, python detection in `ProjectAnalyzer`, and any python branches in `BuildService` / `BuildDelegate`.

**Step 4.5: Verify build**

Run: `./gradlew :app:assembleDebug`

**Step 4.6: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 4.7: Commit**

```bash
git add -A
git commit -m "refactor: drop Python support

Out of scope per ideaz-revival design.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Zero `chaquopy` / `pythonInjector` references survive.

---

## Task 5: Delete Zipline + Redwood

**Why:** Hot-reload over Zipline is out of scope. Redwood codegen exists only to feed Zipline.

**Files to delete:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineCompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineManifestGenerator.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineManifestStep.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/RedwoodCodegen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/SimpleJsBundler.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/zipline/IdeazZiplineEventListener.kt` (and the `zipline/` package directory)
- `app/src/main/kotlin/com/hereliesaz/ideaz/services/JsCompilerService.kt`
- `app/src/test/java/com/hereliesaz/ideaz/buildlogic/ZiplineManifestGeneratorTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/buildlogic/RedwoodCodegenTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/buildlogic/SimpleJsBundlerTest.kt`
- `app/src/main/assets/kotlin-stdlib-js.jar` (the JS stdlib used by the Zipline guest path)

**Files to modify:**
- `app/build.gradle.kts` — remove `libs.zipline.core`, `libs.zipline.loader`, `libs.lazysodium.android` (only used by the unimplemented Ed25519 manifest signing TODO), and the JNA AAR override
- `gradle/libs.versions.toml` — remove `zipline`, `sodium`, `jna` versions + library entries
- `app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt` — remove all Zipline / Redwood / JS step invocations
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt` — there's a documented "Zipline-based Hot Reload logic is currently DISABLED" path; delete the dead code and the comments referencing it

**Step 5.1: Find all references**

Run: `Grep` with pattern `(?i)zipline|redwood|simpleJsBundler|jsCompilerService|lazysodium` over `app/src/`.

**Step 5.2: Delete source files**

```bash
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineCompile.kt
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineManifestGenerator.kt
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ZiplineManifestStep.kt
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/RedwoodCodegen.kt
rm app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/SimpleJsBundler.kt
rm -rf app/src/main/kotlin/com/hereliesaz/ideaz/zipline/
rm app/src/main/kotlin/com/hereliesaz/ideaz/services/JsCompilerService.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/ZiplineManifestGeneratorTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/RedwoodCodegenTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/SimpleJsBundlerTest.kt
rm app/src/main/assets/kotlin-stdlib-js.jar
```

**Step 5.3: Remove zipline + sodium + JNA deps**

Edit `app/build.gradle.kts` — delete:
```
implementation(libs.lazysodium.android) { exclude(...) }
implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
implementation(libs.hiddenapibypass)   // only used by JNA path; delete
implementation(libs.zipline.core)
implementation(libs.zipline.loader)
```

Edit `gradle/libs.versions.toml` — delete:
- versions: `zipline`, `sodium`, `jna`, `hiddenapibypass`
- libraries: `zipline-core`, `zipline-loader`, `lazysodium-android`, `jna`, `hiddenapibypass`

Also check `app/build.gradle.kts` packaging-options block for any `**/libsodium.so` / `**/libjnidispatch.so` `pickFirsts` — remove those lines if present (they're only relevant for the deleted libs).

**Step 5.4: Strip Zipline paths from BuildService**

Edit `app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt`:
- Remove every method invocation that references the deleted classes.
- Remove the build-step ordering comment about "SourceMap last" if it's about Zipline.
- Compile errors will guide you. Iterate until clean.

**Step 5.5: Strip Zipline path from MainViewModel**

Edit `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`:
- Remove the disabled Zipline hot-reload code block.
- Remove KDoc references to "VirtualDisplay/WebView" — change to just "WebView" (VirtualDisplay host goes in Task 8).

**Step 5.6: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Iterate fixes until clean.

**Step 5.7: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 5.8: Commit**

```bash
git add -A
git commit -m "refactor: drop Zipline + Redwood hot-reload

Out of scope per ideaz-revival design. Deletes Zipline and Redwood
build steps, the JS bundler, the JsCompilerService, and unused JNA +
LazySodium deps that were only present to support the unimplemented
Ed25519 manifest-signing TODO. Removes the disabled Zipline hot-reload
path from MainViewModel.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Zero `zipline`, `redwood`, `lazysodium`, `jna` references survive.

---

## Task 6: Delete on-device build toolchain

**Why:** Builds happen via remote GitHub Actions only. The on-device pipeline (kotlinc/aapt2/d8/Maven on a phone) is the most complex and most fragile part of the abandoned codebase. It goes.

**Files to delete (Kotlin):**
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/Aapt2Compile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/Aapt2Link.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ApkBuild.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ApkSign.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/D8Compile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/HttpDependencyResolver.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/KotlincCompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/JavaCompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ScalaCompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/SmaliCompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BaksmaliDecompile.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildOrchestrator.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildCacheManager.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildStep.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/GenerateSourceMap.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/HtmlSourceInjector.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ProcessAars.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ProcessManifest.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/WebBuildStep.kt` (`WebProjectHost` is the runtime; this is the builder — Phase 1 will rebuild from scratch)
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ToolManager.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/HybridToolchainManager.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/DependencyManager.kt`

**Files to delete (tests):**
- All tests under `app/src/test/java/com/hereliesaz/ideaz/buildlogic/` *except* `RemoteBuildManagerTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/utils/DependencyManagerTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/utils/HybridToolchainManagerTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/utils/ToolManagerTest.kt`
- `app/src/test/java/com/hereliesaz/ideaz/BuildPipelineTest.kt` (if it tests the on-device pipeline)

**Files to keep but trim:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/RemoteBuildManager.kt` — this is the remote-build path, the ONE we keep. Verify it doesn't reference any deleted class.
- `app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt` — strip out all references to the on-device steps. What remains: the foreground service shell + invocation of `RemoteBuildManager`.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/BuildDelegate.kt` — strip "Race to Build" branching. What remains: dispatch to remote, poll, install.

**Files to modify (deps):**
- `app/build.gradle.kts` — delete:
  - `implementation(kotlin("compiler-embeddable"))`
  - `implementation(libs.jaxb.api)`, `libs.javax.annotation.api`, `libs.validation.api`, `libs.glassfish.el`, `libs.slf4j.simple`, `libs.guava`, `libs.nb.javac.android`, `libs.r8`, `libs.scala.compiler`, `libs.smali`, `libs.baksmali`, `libs.kxml2`
  - All Maven Aether deps (`libs.maven.resolver.*`, `libs.maven.core`, `libs.aether.transport.file`, `libs.wagon.http.lightweight`, `libs.resolver.maven.resolver.supplier`)
  - `configurations.all { exclude(...); resolutionStrategy { ... commons-logging ... } }` block — only needed for Aether
  - Most of `packaging.resources.excludes` (the Maven-specific ones)

- `gradle/libs.versions.toml` — delete corresponding versions + library entries:
  - `kotlinCompilerEmbeddable`, `aetherTransportFile`, `mavenResolver`, `mavenResolverHttp`, `mavenCore`, `wagonHttp`, `jaxbApi`, `javaxAnnotation`, `validationApi`, `glassfishEl`, `kxml2`, `nbJavac`, `guava`, `r8`, `scalaCompiler`, `smali`, `slf4jSimple`
  - All `maven-resolver-*`, `aether-*`, `wagon-*`, `kotlin-compiler-embeddable`, `nb-javac-android`, `r8`, `scala-compiler`, `smali`, `baksmali`, `jaxb-api`, `javax-annotation-api`, `validation-api`, `glassfish-el`, `kxml2`, `slf4j-simple`, `guava`, `resolver-maven-resolver-supplier`, `wagon-http-lightweight`, `aether-transport-file`, `maven-core` library entries

**Step 6.1: Find all references**

Run: `Grep` with pattern `(?i)kotlincCompile|d8Compile|aapt2|apkSign|apkBuild|httpDependencyResolver|toolManager|hybridToolchain|processAars|processManifest|baksmali|smaliCompile|scalaCompile|javaCompile|simpleJsBundler|webBuildStep|buildOrchestrator|buildCacheManager|buildStep|dependencyManager|generateSourceMap|htmlSourceInjector` over `app/src/`.

This is a large list. Take notes — every file Grep returns will need changes.

**Step 6.2: Delete source files**

Run the `rm` commands listed above. Use `git rm` if you prefer, or plain `rm` followed by `git add -A`.

**Step 6.3: Delete tests**

```bash
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/Aapt2Test.kt 2>/dev/null || true   # if exists
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/BuildCacheManagerTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/BuildPipelineTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/ClassFinderTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/HtmlSourceInjectorTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/HttpDependencyResolverTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/ProcessAarsTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/ProcessManifestTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/buildlogic/WebBuildStepTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/utils/DependencyManagerTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/utils/HybridToolchainManagerTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/utils/ToolManagerTest.kt
rm app/src/test/java/com/hereliesaz/ideaz/BuildPipelineTest.kt 2>/dev/null || true
```

(Keep `RemoteBuildManagerTest.kt` — that's the remote path we're keeping.)

**Step 6.4: Remove deps**

Edit `app/build.gradle.kts` per the list above.
Edit `gradle/libs.versions.toml` per the list above.

**Step 6.5: Trim BuildService**

Open `app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt`. Read the whole file first. Then:
- Delete all imports for deleted classes.
- Delete all on-device build-step invocations.
- Keep: the foreground-service notification, the IPC, and any path that calls `RemoteBuildManager` (poll Releases → download artifact).
- The file should shrink dramatically (likely from 600+ lines to under 200).

**Step 6.6: Trim BuildDelegate**

Open `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/BuildDelegate.kt`. Read it. Then:
- Delete "Race to Build" branching — there's no race, only remote.
- Delete any state for local-build progress.
- Keep: remote-build dispatch, polling, install.

**Step 6.7: Fix dangling references**

Visit every file that Grep returned in Step 6.1. Remove imports + usages of deleted classes. Compile errors will tell you what's left.

**Step 6.8: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. The build should be **noticeably faster** now (no more compiling against `kotlin-compiler-embeddable`).

If errors remain, iterate.

**Step 6.9: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 6.10: Commit**

```bash
git add -A
git commit -m "refactor: drop on-device build toolchain

Out of scope per ideaz-revival design. Builds happen via remote
GitHub Actions only. Deletes the entire on-device pipeline (Aapt2,
D8, KotlincCompile, ApkBuild, ApkSign, ProcessManifest, ProcessAars,
HttpDependencyResolver, ToolManager, HybridToolchainManager,
DependencyManager, all language compilers) and the Maven Aether
dependency resolver. Trims BuildService and BuildDelegate to
remote-only flow.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. APK size noticeably smaller (compare to baseline). Zero `kotlinc` / `aapt2` / `aether` / `maven-resolver` references survive.

---

## Task 7: Delete VirtualDisplay-based AndroidProjectHost

**Why:** Per design, host architecture is System Alert Window overlay (already exists), not VirtualDisplay. The VirtualDisplay-launching host won't work without signature-level permissions on stock Android.

**Note:** `ScreenshotService.kt` *also* uses `VirtualDisplay`, but for its proper purpose (creating a virtual display for `MediaProjection` screen capture). **Keep** `ScreenshotService.kt` — it's used for region screenshots and remains valuable.

**Files to delete:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/project/AndroidProjectHost.kt`

**Files to modify:**
- Anywhere that references `AndroidProjectHost` (probably in `MainScreen.kt` or `IdeNavHost.kt`) — for now, replace with a placeholder Composable that shows "Android target host: coming in Phase 2" so the navigation still compiles. The real Phase 2 will rebuild this on top of `IdeazOverlayService`.

**Step 7.1: Find references**

Run: `Grep` with pattern `AndroidProjectHost` over `app/src/`.

**Step 7.2: Delete file**

```bash
rm app/src/main/kotlin/com/hereliesaz/ideaz/ui/project/AndroidProjectHost.kt
```

**Step 7.3: Replace usages with placeholder**

Wherever `AndroidProjectHost(...)` was called, replace with:
```kotlin
@Composable
fun AndroidProjectHostPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Android target host arrives in Phase 2.")
    }
}
```
Define this placeholder in the same file that *used* the host (probably `MainScreen.kt`), or as a tiny new file `app/src/main/kotlin/com/hereliesaz/ideaz/ui/project/AndroidProjectHostPlaceholder.kt` — whichever feels less invasive.

**Step 7.4: Verify build**

Run: `./gradlew :app:assembleDebug`

**Step 7.5: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 7.6: Commit**

```bash
git add -A
git commit -m "refactor: remove VirtualDisplay-based AndroidProjectHost

The Hybrid Host (VirtualDisplay) approach requires signature-level
permissions unavailable to sideloaded apps. Per ideaz-revival design,
Phase 2 will use System Alert Window overlay (IdeazOverlayService)
instead. Replaced AndroidProjectHost with a placeholder until then.

ScreenshotService's use of VirtualDisplay (for MediaProjection screen
capture) is intentionally retained — that's the API's proper use.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Navigation still works; selecting an Android-typed project shows the placeholder.

---

## Task 8: Delete JulesCliClient

**Why:** Per AGENTS.md, `JulesCliClient` is already deprecated and unused. Final removal.

**Files to delete:**
- `app/src/main/kotlin/com/hereliesaz/ideaz/api/JulesCliClient.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/api/GeminiCliClient.kt` (the parallel CLI-based Gemini client — also unused; will be replaced in Phase 1 with a proper `GeminiAdapter`)

**Step 8.1: Find references**

Run: `Grep` with pattern `JulesCliClient|GeminiCliClient` over `app/src/`.

**Step 8.2: Delete files + fix references**

```bash
rm app/src/main/kotlin/com/hereliesaz/ideaz/api/JulesCliClient.kt
rm app/src/main/kotlin/com/hereliesaz/ideaz/api/GeminiCliClient.kt
```

If Grep returned references in any *other* files, remove the imports and usages.

**Step 8.3: Verify build**

Run: `./gradlew :app:assembleDebug`

**Step 8.4: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 8.5: Commit**

```bash
git add -A
git commit -m "refactor(api): remove deprecated CLI clients

JulesCliClient was already deprecated per AGENTS.md.
GeminiCliClient is the parallel unused CLI-based client.
Phase 1 will introduce a clean GeminiAdapter (HTTP).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Zero references to either CLI client.

---

## Task 9: Stub or fix Jules call-site compile errors (if any)

**Why:** Stale `compile_log.txt` claimed `AIDelegate` was missing `projectId` arguments and `ProjectScreen` calls a removed `onSelectApk`. Verify against ground truth (Task 1's baseline) — if these errors persist after Tasks 2–8, fix them minimally so the app compiles. The proper fix happens in Phase 2.

**Step 9.1: Re-check current state**

Run: `./gradlew :app:assembleDebug 2>&1 | tee /tmp/task-9-pre.log`

If green, **skip to Step 9.7** — nothing to fix. Just commit a no-op note in `phase-0-baseline.md` saying "stubs unnecessary" and move on.

If errors:
- Compare error list to `phase-0-baseline.md`. New errors are likely caused by earlier deletion tasks; fix those first by re-checking the relevant task's "fix dangling references" step.
- Persistent errors involving `JulesApiClient`, `AIDelegate`, `ProjectScreen` are the ones to stub here.

**Step 9.2 (conditional): Stub `onSelectApk` removal in `ProjectScreen.kt`**

If `ProjectScreen.kt:339` (or wherever it sits now) calls `onSelectApk = { ... }` against a `SetupTab` that no longer accepts it: delete the `onSelectApk = { ... }` argument from the call site. The parameter still exists in `SetupTab.kt:43` so this should compile. If `SetupTab` parameter has been removed in some version: also delete the call.

**Step 9.3 (conditional): Stub `projectId` missing-argument in `AIDelegate.kt`**

If `AIDelegate.kt:75` and `AIDelegate.kt:155` (or wherever) call a function expecting `projectId` and don't pass one: add `projectId = ""` (empty stub). Add a TODO comment:
```kotlin
// TODO(phase-2): Wire real projectId. Stubbed empty for Phase 0 compile gate.
```

**Step 9.4 (conditional): Stub `JulesApiClient` signature drift**

If `JulesApiClient.kt:78`, `:81`, `:90` show argument-mismatch errors: align the override signatures to match the `IJulesApiClient` interface verbatim. The interface in `IJulesApiClient.kt` is the source of truth (`listSessions(pageSize: Int = 100, pageToken: String? = null)` etc.). Make `JulesApiClient.kt`'s overrides match. Don't touch `JulesApi.kt` (the Retrofit interface) — it already matches.

**Step 9.5: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

**Step 9.6: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

If `JulesApiClientTest.kt` fails to compile (per stale `test_log.txt`, it does): align the test's mock-call sites to match the interface signatures (same fix as Step 9.4 but on the test side). Add a TODO comment: real test coverage in Phase 2.

**Step 9.7: Commit**

If Steps 9.2–9.6 were no-ops, make an empty commit:
```bash
git commit --allow-empty -m "refactor(jules): no stubs needed; build was already green"
```

If stubs were applied:
```bash
git add -A
git commit -m "refactor(jules): stub broken call sites for Phase 0 compile gate

Stale compile log indicated drift between JulesApiClient overrides
and the IJulesApiClient interface, plus missing projectId arguments
in AIDelegate. Aligned overrides to interface and stubbed empty
projectIds with TODO(phase-2) markers. Real fix in Phase 2 (Jules
plumbing task).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** Build green. Tests compile. Any stubs are flagged with `TODO(phase-2)`.

---

## Task 10: Modernize Compose + lifecycle deprecation warnings

**Why:** Per `verify_build.log`, the build emitted ~25 deprecation warnings even when it succeeded. The design requires "zero deprecation warnings from deleted code paths" — many of these come from code we're keeping, so we fix them properly here.

**Known deprecations (from `verify_build.log`):**

| Symbol | Replacement | Files |
|---|---|---|
| `androidx.compose.ui.platform.LocalLifecycleOwner` | `androidx.lifecycle.compose.LocalLifecycleOwner` (different package) | `ui/SettingsScreen.kt:86`, `ui/web/WebProjectHost.kt:48` |
| `TabRow(...)` (basic, deprecated overload) | `PrimaryTabRow` or `SecondaryTabRow` | `ui/IdeBottomSheet.kt:130` |
| `TabRowDefaults.Indicator` | `TabRowDefaults.SecondaryIndicator` | `ui/IdeBottomSheet.kt:137` |
| `Modifier.tabIndicatorOffset(currentTabPosition)` | new `TabIndicatorScope.tabIndicatorOffset` overload | `ui/IdeBottomSheet.kt:138` |
| `Modifier.menuAnchor()` (deprecated overload) | new overload taking `ExposedDropdownMenuAnchorType` + `enabled` | `ui/SettingsScreen.kt:928, 975, 1025`, `ui/project/CreateTab.kt:51`, `ui/project/SetupTab.kt:200` |
| `LocalClipboardManager` | `LocalClipboard` | `ui/IdeBottomSheet.kt:41` |
| `WebSettings.allowFileAccessFromFileURLs` | (delete — not needed for `WebViewAssetLoader`) | `ui/web/WebProjectHost.kt:61` |
| `WebSettings.allowUniversalAccessFromFileURLs` | (delete — same reason) | `ui/web/WebProjectHost.kt:63` |
| `Activity.onBackPressed()` override | `OnBackPressedDispatcher.addCallback(...)` | (was in `react/ReactNativeActivity.kt` — already deleted in Task 2; verify no other call sites) |
| `FileObserver(String, Int)` constructor | `FileObserver(File, Int)` | `services/BuildService.kt:630` (this method may have been deleted in Task 6 — verify) |
| `AccessibilityNodeInfo.recycle()` | (delete the calls — auto-managed since API 30, our minSdk is 30) | `services/IdeazAccessibilityService.kt:93, 95, 97, 119, 123` |
| `ProjectConfigManager` `when` duplicate-branch warning | Investigate `utils/ProjectConfigManager.kt:261` | one fix |

**Step 10.1: Run a fresh build with `--warning-mode=all`**

Run: `./gradlew :app:assembleDebug --warning-mode=all 2>&1 | tee /tmp/task-10-warnings.log`

Search the log for `is deprecated`. The actual current list may differ from the table above.

**Step 10.2: Fix the `LocalLifecycleOwner` deprecation**

In `ui/SettingsScreen.kt:86` and `ui/web/WebProjectHost.kt:48`:
- Replace `import androidx.compose.ui.platform.LocalLifecycleOwner` with `import androidx.lifecycle.compose.LocalLifecycleOwner`.
- Add `implementation("androidx.lifecycle:lifecycle-runtime-compose")` to `app/build.gradle.kts` if not already present (check with `Grep` first; the BoM may already supply it via `lifecycleRuntimeKtx`).

**Step 10.3: Fix `TabRow` + `Indicator` deprecations**

In `ui/IdeBottomSheet.kt:130-138`:
- Replace `TabRow(selectedTabIndex = ..., ...)` with `SecondaryTabRow(selectedTabIndex = ..., ...)` — `SecondaryTabRow` matches the existing visual.
- Replace `TabRowDefaults.Indicator(...)` with `TabRowDefaults.SecondaryIndicator(...)`.
- For `Modifier.tabIndicatorOffset(currentTabPosition)` — within `SecondaryTabRow`'s `indicator` slot, the parameter is now scoped via `TabIndicatorScope`. Migrate per [Compose tabs migration guide](https://developer.android.com/jetpack/compose/components/tabs).

**Step 10.4: Fix `menuAnchor` deprecations**

For each call site (`ui/SettingsScreen.kt:928, 975, 1025`, `ui/project/CreateTab.kt:51`, `ui/project/SetupTab.kt:200`):
- Replace `.menuAnchor()` with `.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)` (or `PrimaryEditable` if it's a text field).

**Step 10.5: Fix `LocalClipboardManager`**

In `ui/IdeBottomSheet.kt:41`:
- Replace `import androidx.compose.ui.platform.LocalClipboardManager` with `import androidx.compose.ui.platform.LocalClipboard`.
- The new API uses suspend functions for clipboard reads/writes; rewrite the call site accordingly. (One spot only.)

**Step 10.6: Drop the WebSettings file-URL deprecations**

In `ui/web/WebProjectHost.kt:61, 63`:
- Delete the lines `webSettings.allowFileAccessFromFileURLs = true` and `webSettings.allowUniversalAccessFromFileURLs = true`.
- These are unsafe and unneeded once we move to `WebViewAssetLoader` in Phase 1.

**Step 10.7: Drop `AccessibilityNodeInfo.recycle()` calls**

In `services/IdeazAccessibilityService.kt:93, 95, 97, 119, 123`:
- Simply delete each `node.recycle()` line. From API 30 onward, recycling is automatic.

**Step 10.8: Fix `FileObserver` deprecation (if present)**

In `services/BuildService.kt:630` (verify the line still exists after Task 6's trimming):
- Replace `FileObserver(path: String, mask: Int)` with `FileObserver(file: File, mask: Int)`.
- If the entire `FileObserver` usage is part of the deleted on-device-build watcher, just delete the whole block.

**Step 10.9: Fix the duplicate-when-branch in `ProjectConfigManager`**

Read `utils/ProjectConfigManager.kt:261`. Identify which case is duplicated. Either:
- Delete the duplicate branch (most likely correct).
- Or merge the two branches if they were intentional.

**Step 10.10: Verify build with zero deprecations**

Run: `./gradlew :app:assembleDebug --warning-mode=all 2>&1 | tee /tmp/task-10-final.log`

Search for `is deprecated` — expected result: zero matches in *our code* (third-party deprecations from BOM/AndroidX may remain; those aren't in scope).

If any "is deprecated" hit comes from `app/src/main/kotlin/com/hereliesaz/ideaz/`: fix it before continuing.

**Step 10.11: Verify tests**

Run: `./gradlew :app:testDebugUnitTest`

**Step 10.12: Commit**

```bash
git add -A
git commit -m "refactor: clear Compose + lifecycle deprecation warnings

Migrates: LocalLifecycleOwner to lifecycle-runtime-compose package,
TabRow to SecondaryTabRow + SecondaryIndicator, menuAnchor to the
MenuAnchorType overload, LocalClipboardManager to LocalClipboard,
drops AccessibilityNodeInfo.recycle calls (auto-managed since API 30,
our minSdk is 30), and removes the WebSettings file-URL allow flags
that WebViewAssetLoader makes unnecessary.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** `./gradlew :app:assembleDebug --warning-mode=all` produces **zero** "is deprecated" lines from `com/hereliesaz/ideaz/` source.

---

## Task 11: Delete stale documentation

**Why:** Phase 0's last cleanup. Many doc files describe deleted features or were autogenerated audit reports. They mislead future readers.

**Files to delete:**
- `docs/react_native_implementation_plan.md`
- `docs/flutter_investigation.md`
- `docs/python_implementation.md`
- `docs/todo_python.md`
- `docs/todo_flutter.md`
- `docs/todo_react_native.md`
- `docs/todo_web.md` (web is now PWA, separate concern; this doc was for the deleted general-web path)
- `docs/Dynamic Android App Development Exploration.md`
- `docs/Modular Android Development Approaches.md`
- `docs/Hybrid Host Architecture Implementation Checklist.md`
- `docs/audit_report.md`
- `docs/contradictions_report.md`
- `docs/AZNAVRAIL_COMPLETE_GUIDE.md` (vendor-doc dump for an unrelated library)

**Files to update:**
- `docs/TODO.md` — replace with a pointer to `docs/plans/2026-05-01-ideaz-revival-design.md` and the active phase plan
- `docs/file_descriptions.md` — remove entries for deleted source files
- `docs/architecture.md` — rewrite to reflect new architecture (PWA primary, Android Phase 2)
- `docs/blueprint.md` — update vision to match the design doc
- `docs/screens.md` — remove RN/Flutter/Python screen mentions
- `docs/build_pipeline.md` — rewrite to "remote builds via GitHub Actions"
- `docs/jules-integration.md` — note "Phase 2" status; remove Jules CLI mentions
- `docs/data_layer.md` — remove on-device dependency-resolution mentions
- `docs/error_handling.md` — remove RN/Flutter/Python paths
- `docs/manifest.md` — remove `ReactNativeActivity` mention
- `docs/performance.md` — remove on-device build performance section
- `docs/platform_decision_helper.md` — narrow to "PWA or Android-native"
- `docs/task_flow.md` — update to new loops
- `docs/testing.md` — match the testing strategy from the design doc
- `docs/workflow.md` — update CI/CD section
- `AGENTS.md` — update "Recent Changes" + remove references to deleted features
- `README.md` — re-validate against new vision (existing one is mostly compatible)

**Step 11.1: Delete the stale files**

```bash
rm docs/react_native_implementation_plan.md
rm docs/flutter_investigation.md
rm docs/python_implementation.md
rm docs/todo_python.md
rm docs/todo_flutter.md
rm docs/todo_react_native.md
rm docs/todo_web.md
rm "docs/Dynamic Android App Development Exploration.md"
rm "docs/Modular Android Development Approaches.md"
rm "docs/Hybrid Host Architecture Implementation Checklist.md"
rm docs/audit_report.md
rm docs/contradictions_report.md
rm docs/AZNAVRAIL_COMPLETE_GUIDE.md
```

**Step 11.2: Replace `docs/TODO.md` with a pointer**

Overwrite with:
```markdown
# IDEaz Roadmap

The TODO list previously here was a milestone retrospective for the
abandoned-and-now-revived project. It has been superseded.

**Active design doc:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md)
**Active phase plan:** [`plans/2026-05-01-phase-0-triage.md`](plans/2026-05-01-phase-0-triage.md)

When Phase 0 completes, this file will point to Phase 1's plan.
```

**Step 11.3: Update remaining docs**

For each file in the *update* list above: read, identify references to deleted features, edit. Don't rewrite from scratch unless the file is mostly stale. Aim for surgical, evidence-based edits.

For `AGENTS.md`: the "Critical Known Issues / Discrepancies" section explicitly mentions Zipline + JulesCli — those issues are now resolved (by deletion). Update accordingly.

**Step 11.4: Verify nothing references deleted docs**

Run: `Grep` with pattern `react_native_implementation|flutter_investigation|python_implementation|todo_python|todo_flutter|todo_react_native|todo_web|Hybrid Host Architecture|audit_report|contradictions_report|AZNAVRAIL_COMPLETE_GUIDE` over the whole repo (`docs/` + `README.md` + `AGENTS.md`).

Fix any dangling links.

**Step 11.5: Verify build**

Run: `./gradlew :app:assembleDebug` (sanity check — docs shouldn't affect build).

**Step 11.6: Commit**

```bash
git add -A
git commit -m "docs: prune stale docs from deleted-feature era

Removes documentation for React Native, Flutter, Python, deleted web
path, Hybrid Host VirtualDisplay architecture, and stale audit
reports. Updates the remaining docs to match the revival design.
TODO.md now points to the design + active phase plan.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** No dangling cross-doc references. Each remaining doc accurately describes the current codebase.

---

## Task 12: Regenerate lint baseline

**Why:** `app/lint-baseline.xml` was filled with RN-related warnings — most are now obsolete. Easier to regenerate than to scrub by hand.

**Step 12.1: Delete current baseline**

```bash
rm app/lint-baseline.xml
```

**Step 12.2: Generate new baseline**

Run: `./gradlew :app:lintDebug 2>&1 | tee /tmp/task-12-lint.log`

This will *fail* with "lint baseline does not exist; created..." — which is what we want. The new baseline file appears at `app/lint-baseline.xml`.

If lint produces real errors that aren't baselined (the new baseline only captures warnings, not errors): fix them before continuing.

**Step 12.3: Inspect the new baseline**

Read `app/lint-baseline.xml`. Should be vastly smaller than before. If suspiciously huge or contains references to deleted paths: investigate.

**Step 12.4: Re-run lint to confirm green**

Run: `./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL with the baseline now in place.

**Step 12.5: Commit**

```bash
git add app/lint-baseline.xml
git commit -m "chore: regenerate lint baseline after Phase 0 deletions

Old baseline was full of RN/Flutter/Python warnings that no longer
apply. New baseline reflects current code only.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:** `./gradlew :app:lintDebug` green. New baseline materially smaller than the deleted one.

---

## Task 13: Final verification

**Why:** Lock in Phase 0 by proving the green-build gate from a cold start.

**Step 13.1: Clean everything**

Run: `./gradlew clean`

**Step 13.2: Cold build**

Run: `./gradlew :app:assembleDebug --warning-mode=all 2>&1 | tee /tmp/task-13-build.log`
Expected: BUILD SUCCESSFUL.

**Step 13.3: Cold tests**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tee /tmp/task-13-tests.log`
Expected: tests pass (or no worse than `phase-0-baseline.md`).

**Step 13.4: Cold lint**

Run: `./gradlew :app:lintDebug 2>&1 | tee /tmp/task-13-lint.log`
Expected: BUILD SUCCESSFUL.

**Step 13.5: Confirm zero in-codebase deprecations**

Run: `grep -E "(is deprecated|Deprecated in Java)" /tmp/task-13-build.log | grep "com/hereliesaz/ideaz" || echo "CLEAN"`
Expected output: `CLEAN`

If anything else appears, return to Task 10.

**Step 13.6: Confirm bumped version**

Edit `version.properties` — increment `minor` per `AGENTS.md`'s versioning strategy (Phase 0 is a major refactor).

**Step 13.7: Write phase-0 done marker**

Create `docs/plans/phase-0-complete.md` with:
- Date completed.
- Commit hash at completion: `git rev-parse HEAD`.
- Sizes: APK (`ls -lh app/build/outputs/apk/debug/*.apk`), source line count (`find app/src/main/kotlin -name "*.kt" | xargs wc -l | tail -1`).
- Comparison to baseline: how much shrank.

**Step 13.8: Final commit**

```bash
git add version.properties docs/plans/phase-0-complete.md
git commit -m "chore(phase-0): complete

Phase 0 triage done. Codebase compiles green, tests pass, lint passes,
zero in-codebase deprecation warnings. Ready for Phase 1 (PWA target
loop) planning.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

**Acceptance:**
- Cold `./gradlew :app:assembleDebug` green.
- Cold `./gradlew :app:testDebugUnitTest` no-worse-than-baseline.
- Cold `./gradlew :app:lintDebug` green.
- Zero "is deprecated" hits in `com/hereliesaz/ideaz/` source.
- `phase-0-complete.md` documents the win.

---

## After Phase 0

The branch is ready for review. Hand off:
1. Push the branch and open a PR (`gh pr create`).
2. Trigger `superpowers:requesting-code-review` against the PR diff.
3. Once merged, return to `superpowers:writing-plans` to write the **Phase 1A — Render** plan (the first PWA-target sub-phase).

Phase 0 is **the** unlock. Every subsequent phase plan depends on starting from a green build.
