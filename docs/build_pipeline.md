# Build Pipeline — Remote via GitHub Actions

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md), §"Out of scope, permanently".

## 1. Strategy

IDEaz does **not** build user projects on-device. The on-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether resolver) and "Race to Build" branching were removed in Phase 0. All Android builds happen on GitHub Actions; PWAs do not need a build step at all.

| Target | Build path |
|---|---|
| **PWA** (Phase 1) | No build. IDEaz renders the working tree directly through `WebProjectHost`. |
| **Android** (Phase 2) | Remote-only. IDEaz pushes a tag → GitHub Actions builds → IDEaz polls Releases → downloads the APK → sideloads via `PackageInstaller`. |

## 2. Workflow Injection

On "Save & Initialize" in the Setup tab, IDEaz force-pushes a standardized set of workflow files to the project repo:

* `.github/workflows/android_ci.yml` — debug build on push.
* `.github/workflows/release.yml` — tagged release build, attaches signed APK to the GitHub Release.

`ProjectConfigManager` owns the YAML content (hardcoded, not asset-loaded, so a missing assets directory cannot break initialization).

## 3. Build Execution (Phase 2)

1. **Tag and push.** `BuildDelegate` creates a tag, pushes it to the repo.
2. **GitHub Actions runs.** The injected `release.yml` runs the build on a hosted runner; the project's existing Gradle / `build.gradle.kts` does the work. IDEaz does not provide a toolchain — the workflow uses GitHub's pre-installed JDK and the project's Gradle wrapper.
3. **`RemoteBuildManager` polls the Releases API** for an artifact matching the tag.
4. **Download and install.** When the artifact appears, `RemoteBuildManager` downloads it; `BuildService` hands it to `PackageInstaller`, which sideloads it. `MainActivity`'s `packageInstallReceiver` auto-launches the new install.

## 4. Build Failures

If the workflow fails, `BuildDelegate` pulls the workflow log (via the GitHub API), routes it to the AI:

* **Phase 1 (PWA):** N/A — no Actions build for PWAs.
* **Phase 2 (Android):** the build log is dispatched into the active Jules session as a follow-up activity ("the build failed: <log>"). Jules is expected to push a fix; the cycle repeats.

If the failure looks like an IDE-infrastructure bug rather than a user-code bug (heuristic: stack-trace from `com.hereliesaz.ideaz`, missing tool, etc.), `GithubIssueReporter` files an Issue against `HereLiesAz/IDEaz` with the `jules` label instead.

## 5. Secrets

GitHub Actions workflows that need API keys (e.g., signing keystore, AI provider keys when CI uses them) read from repository secrets. `RepoDelegate.uploadProjectSecrets` is the device-side path that pushes secrets to the repo via the GitHub Actions secrets API. **It is currently a no-op stub** awaiting libsodium re-integration — see [`plans/phase-0-followups.md`](plans/phase-0-followups.md).

---

## 6. Releasing IDEaz itself to Google Play (App Bundle)

IDEaz can ship to the Play Console as a signed **Android App Bundle (.aab)** in
addition to the GitHub-Release APK channel described above. The two channels coexist:
the existing [`build-and-release.yml`](../.github/workflows/build-and-release.yml)
still builds and publishes a signed **APK** to GitHub Releases on every push, while the
new [`publish-play.yml`](../.github/workflows/publish-play.yml) builds and (optionally)
uploads a signed **AAB** to Play on manual dispatch.

### 6.1 Build a signed AAB locally

```bash
# Point the release signingConfig at your keystore via env vars, then bundle.
export KEYSTORE_FILE=/abs/path/to/release.keystore
export KEYSTORE_PASSWORD=••••••
export KEY_ALIAS=upload
export KEY_PASSWORD=••••••            # OpenSSL-built PKCS12 uses the store password

./gradlew :app:bundleRelease -PversionBuild=$(git rev-list --count HEAD)
# → app/build/outputs/bundle/release/app-release.aab  (signed)
```

* The `release` signingConfig in [`app/build.gradle.kts`](../app/build.gradle.kts)
  reads `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from the
  environment. **With none of them set, the release build falls back to the debug
  keystore** so a plain `./gradlew bundleRelease` still succeeds for testing — but that
  bundle is *not* uploadable to Play. Never commit a keystore or its passwords.
* Omit `-PversionBuild` for purely local builds; `versionCode`/`versionName` then come
  from `version.properties` as before.

### 6.2 versionCode override (`-PversionBuild`)

Play rejects an upload whose `versionCode` is **duplicate or lower** than a previous
one. To guarantee a strictly-increasing code, CI passes the git commit count:

```
-PversionBuild=$(git rev-list --count HEAD)
```

When `-PversionBuild=<n>` is supplied it becomes **both** the `versionCode` (verbatim,
monotonic) **and** the build component (`d`) of the `a.b.c.d` `versionName`. When it is
absent, the historic file-driven formula
(`major·1e6 + minor·1e4 + patch·100 + build`) is used and `version.properties` is left
untouched. See [`app/build.gradle.kts`](../app/build.gradle.kts).

### 6.3 Modular delivery & size

* **Automatic splits (free):** because the artifact is an `.aab`, Play generates
  per-device splits by **screen density, ABI, and language** automatically. There is
  nothing extra to build — one bundle, optimized per device at install.
* **R8 + resource shrinking:** already enabled on `release`
  (`isMinifyEnabled = true`, `isShrinkResources = true`); keep rules live in
  [`app/proguard-rules.pro`](../app/proguard-rules.pro).
* **Dynamic feature module — `:webruntime`:** the ~4.5 MB bundled in-browser web
  runtime (Babel, React UMD/shims, `ideaz-loader`, served by `WebProjectPathHandler`)
  lives in the [`:webruntime`](../webruntime) `com.android.dynamic-feature` module
  instead of the base. It is delivered **install-time** with `<dist:fusing
  dist:include="true"/>`:
  * **Why install-time, not on-demand?** IDEaz is *also* distributed as a bare signed
    APK via GitHub Releases, and on-demand modules are absent from a standalone APK —
    the PWA runtime would vanish off Play. Install-time + fusing keeps the assets in
    *every* install path, reachable synchronously through the base `AssetManager` with
    no `SplitInstall` call and no offline failure. The base app stays installable on
    its own.
  * **Flipping to on-demand (optional follow-up):** change `<dist:install-time/>` to
    `<dist:on-demand/>` in [`webruntime/src/main/AndroidManifest.xml`](../webruntime/src/main/AndroidManifest.xml),
    add the Play Feature Delivery dependency (`com.google.android.play:feature-delivery`
    + `…-ktx`), enable `SplitCompat` (e.g. make `MainApplication` extend
    `SplitCompatApplication`), and have `WebProjectPathHandler.serveRuntimeAsset`
    request the split via `SplitInstallManager` and await it before reading assets. This
    only pays off if IDEaz is distributed **exclusively** through Play.
* **Play in-app updates (optional follow-up):** the Play Core / Feature Delivery
  in-app-update API can prompt users to update once builds flow through Play. Not wired
  up yet.

### 6.4 Publishing via the workflow

[`publish-play.yml`](../.github/workflows/publish-play.yml) is `workflow_dispatch`-only
with three inputs:

| Input | Options | Default | Meaning |
|---|---|---|---|
| `track` | internal / alpha / beta / production | `internal` | Play track to release on |
| `status` | draft / completed | `draft` | Release status on that track |
| `publish` | boolean | `false` | **off → build + attach the `.aab` artifact only; nothing reaches Play.** on → upload via `r0adkll/upload-google-play@v1` |

It checks out with `fetch-depth: 0`, reconstructs the upload keystore from secrets,
sets up JDK 21 + Gradle, runs `:app:bundleRelease` with the commit-count
`versionCode`, uploads the `.aab` as a build artifact, and — only when `publish=true`
— pushes it to Play. The package name is read from `app/build.gradle.kts` at runtime
(not hardcoded). Defaults are deliberately safe: **internal track, draft status, no
publish.**

### 6.5 Required repository secrets

| Secret | Used for |
|---|---|
| `KEYSTORE_PRIVATE` | PEM private key — reconstructed into the upload keystore |
| `KEYSTORE_CHAIN` | PEM certificate chain for that key |
| `KEYSTORE_PASSWORD` | Keystore **and** key password (OpenSSL PKCS12 default) |
| `KEY_ALIAS` | Key alias inside the keystore |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud service-account JSON for the Play Publisher API (plaintext) |
| `GOOGLE_SERVICES_API_KEY`, `PROJECT_ID`, `CLIENT_ID` | `google-services.json` injection (optional; reused from existing CI) |

The first four already power the existing APK release workflow — reuse them as-is.

### 6.6 One-time Play setup (manual, by the maintainer)

1. In **Google Cloud**, create a **service account** and a JSON key for it.
2. In the **Play Console** → *Users & permissions*, invite that service account and
   grant it **release** permission (at minimum *Release apps to testing tracks* and, if
   you want `track: production`, production access).
3. Paste the JSON key into the `PLAY_SERVICE_ACCOUNT_JSON` repo secret.
4. **The very first release of a brand-new app must be uploaded *manually* in the Play
   Console** (create the app, upload one bundle, accept the Play App Signing terms).
   The Publisher API can only update an app that already exists — it cannot create one.
   After that first manual upload, `publish-play.yml` can publish every subsequent
   build.

### 6.7 Data safety & privacy

IDEaz ships **no ads** and declares **no `AD_ID`** permission, so there is no ad-ID
disclosure to make. It does, however, send data to third parties the **Play Data
safety** form must cover: source/prompts to **Google Gemini / generative-AI APIs** and
**Jules**, and repository data to **GitHub**. It also requests sensitive permissions
(`MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, accessibility services,
`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`) that Play will require justification for at
review. Keep a current **privacy policy** URL in the listing and ensure the Data safety
declarations match what the app actually transmits before promoting beyond the internal
track.
