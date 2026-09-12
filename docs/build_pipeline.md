# Build Pipeline — Remote via GitHub Actions

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md).

## 1. Strategy

IDEaz does **not** build user projects on-device. The on-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether resolver) and "Race to Build" branching were removed in Phase 0. User-project Android builds happen on GitHub Actions; previewable web projects do not need an Android build step.

| Target | Build path |
|---|---|
| **Web/PWA** (Phase 1) | No Android build. IDEaz renders the working tree directly through `WebProjectHost`. |
| **Android** (future target loop) | Remote-only: Actions build → IDEaz downloads/install artifact. |

## 2. Workflow Injection

On project initialization IDEaz writes the appropriate CI workflow through `ProjectConfigManager`. Generated repositories receive a root `version.properties` containing `major`, `minor`, `patch`, and `build`; CI derives its build component from `git rev-list --count HEAD`. Repository variables such as `BUILD_COMMAND`, `ARTIFACT_PATH`, `RELEASE_COMMAND`, and `RELEASE_ARTIFACT_PATH` cover nonstandard project layouts.

## 3. Build Execution (future Android target)

1. IDEaz pushes the requested ref/tag.
2. GitHub Actions runs the project's Gradle wrapper.
3. IDEaz polls the release/workflow result.
4. A successful APK can be downloaded and installed by the Android-target loop.

## 4. Build Failures

Web/PWA projects have no Android build. A future Android target can route workflow failures back into the active AI/task surface. IDE infrastructure failures may be reported to `HereLiesAz/IDEaz` when the user has error reporting enabled.

## 5. Secrets

GitHub Actions workflows that need secrets read repository secrets. `RepoDelegate.uploadProjectSecrets` fetches the repository Actions public key, encrypts each value with the pure-JVM libsodium-compatible `GithubSecretBox`, uploads it through the Actions secrets API, and reports partial failures. See [`plans/phase-0-followups.md`](plans/phase-0-followups.md).

## 5.1 Dependency security inventory

`.github/workflows/dependency-submission.yml` submits only release-runtime classpaths. This covers dependencies packaged into IDEaz's shipping Android artifacts while excluding unit-test, instrumentation-test, lint, Gradle, AGP, plugin, and other CI-tooling classpaths.

Runtime dependency pins live in root `build.gradle.kts` under `subprojects { configurations.all { resolutionStrategy { ... } } }`, so both `:app` and `:webruntime` resolve the same patched versions.

---

## 6. Releasing IDEaz itself: GitHub + Google Play

IDEaz has two Android distributions from one source tree:

| Channel | Build type | Artifact | External installed-AI surface |
|---|---|---|---|
| GitHub Releases | `release` (`debug` for ordinary debug builds) | APK | Yes |
| Google Play | `play` | AAB | No |

The split is deliberate. The GitHub APK may expose the explicitly enabled installed-Gemini accessibility/overlay host. The Play bundle inherits only the policy-safe common manifest and uses provider APIs.

[`build-and-release.yml`](../.github/workflows/build-and-release.yml) owns the GitHub APK channel. [`publish-play.yml`](../.github/workflows/publish-play.yml) owns the Play AAB channel.

### 6.0 GitHub-Release APK channel

`build-and-release.yml` is split by trigger:

| Trigger | Publish step | Tag | Release | `prerelease` |
|---|---|---|---|---|
| `push` to `master` | **Publish Debug Build** | `debug-v$major.$minor` | Latest Debug Build | `true` |
| `workflow_dispatch` | **Publish Latest Release** | `v$major.$minor` | Latest Release | `false` |

The tags are major.minor so builds within one minor release roll into the same release. A feature-sized release advances `minor` in `version.properties`.

### 6.0.1 Build-number derivation

Both release workflows pass `-PversionBuild=$BUILD_NUMBER`, where `BUILD_NUMBER` is normally `git rev-list --count HEAD` and may be overridden by the numeric `BUILD_NUMBER_OVERRIDE` repository variable. This keeps CI version codes monotonic.

The static `build` field in `version.properties` remains the fallback only for local Gradle invocations that do not pass `-PversionBuild`.

### 6.0.2 Pinned unit-test JVM heap

`app/build.gradle.kts` pins the forked unit-test JVM heap so a full CI build cannot starve `testDebugUnitTest` after release/R8 tasks have already consumed runner memory.

### 6.1 Build signed artifacts locally

GitHub release APK/AAB experiments use the `release` build type; **Play candidates must use `play`** so the privileged GitHub-only manifest surface is absent.

```bash
export KEYSTORE_FILE=/abs/path/to/release.keystore
export KEYSTORE_PASSWORD=••••••
export KEY_ALIAS=upload
export KEY_PASSWORD=••••••

# GitHub release build
./gradlew :app:assembleRelease -PversionBuild="$BUILD_NUMBER"

# Policy-safe Play bundle
./gradlew :app:bundlePlay -PversionBuild="$BUILD_NUMBER"
# → app/build/outputs/bundle/play/app-play.aab
```

The release-grade signing configuration reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Local builds without release-signing environment variables can fall back to the debug keystore for build verification; those artifacts are not uploadable to Play.

### 6.2 Build-component override (`-PversionBuild`)

Play rejects duplicate or lower `versionCode`s. `-PversionBuild=<n>` replaces only the build component while preserving `major`, `minor`, and `patch` from `version.properties`.

The packed formula remains `major·1e6 + minor·1e4 + patch·100 + build`; using the same formula for both distributions keeps upgrades monotonic.

### 6.3 Modular delivery & size

* AAB delivery lets Play generate density/ABI/language splits automatically.
* `play` is initialized from the release-grade configuration, including R8/resource shrinking, then compile-time disables external-AI automation/overlay code and omits their manifest declarations.
* `:webruntime` exposes a matching `play` build type and stays install-time/fused, so the same bundled web runtime exists in both GitHub APK and Play installs.

### 6.4 Publishing via the workflow

[`publish-play.yml`](../.github/workflows/publish-play.yml) is `workflow_dispatch`-only with three inputs:

| Input | Options | Default | Meaning |
|---|---|---|---|
| `track` | internal / alpha / beta / production | `internal` | Play track |
| `status` | draft / completed | `draft` | Release status |
| `publish` | boolean | `false` | off = build/artifact only; on = upload to Play |

The workflow reconstructs the signing keystore, resolves the package name, builds the dedicated `play` AAB, uploads the AAB as an Actions artifact, and publishes only when `publish=true`.

Repository-variable overrides:

| Variable | Meaning |
|---|---|
| `BUILD_NUMBER_OVERRIDE` | Numeric build component instead of git commit count |
| `ANDROID_BUILD_FILE` | Build file used to discover `applicationId`; default `app/build.gradle.kts` |
| `PLAY_BUNDLE_COMMAND` | Preferred Play build command override; default `./gradlew bundlePlay` |
| `BUNDLE_COMMAND` | Legacy compatible fallback when `PLAY_BUNDLE_COMMAND` is unset |

The workflow intentionally still honors `ANDROID_BUILD_FILE` and legacy `BUNDLE_COMMAND` so existing repository configuration does not silently stop applying after the distribution split.

### 6.5 Required repository secrets

| Secret | Used for |
|---|---|
| `KEYSTORE_PRIVATE` | PEM private key — reconstructed into upload keystore |
| `KEYSTORE_CHAIN` | PEM certificate chain |
| `KEYSTORE_PASSWORD` | Keystore/key password |
| `KEY_ALIAS` | Key alias |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Publisher service-account JSON |
| `GOOGLE_SERVICES_API_KEY`, `PROJECT_ID`, `CLIENT_ID` | Optional google-services injection |

### 6.6 One-time Play setup

1. Create a Google Cloud service account and JSON key.
2. Grant it the required Play Console release permissions.
3. Store the JSON as `PLAY_SERVICE_ACCOUNT_JSON`.
4. The first release of a brand-new Play app still requires the usual manual Play Console app creation / Play App Signing setup; subsequent releases can use the workflow.

### 6.7 Data safety & privacy

The **Play build does not declare** the GitHub distribution's installed-AI accessibility service, `SYSTEM_ALERT_WINDOW`, or special-use foreground overlay service. Those permissions therefore are not part of the Play review/data-safety surface.

The Play Data safety form must still match data actually sent by the Play build: prompts/source/context sent to user-selected hosted AI providers, and repository/account data sent to GitHub when the user uses GitHub-backed features. Crash reports are opt-in and may be filed to the public IDEaz issue tracker as disclosed in-app.

The **GitHub APK** has a separate permission story: its installed-Gemini bridge is user-enabled, package-scoped to supported Gemini packages, idle outside an in-flight handoff, and shares a redacted bounded project snapshot plus explicit prompt attachments. Keep that disclosure accurate in Settings and `docs/architecture.md` whenever the bridge changes.
