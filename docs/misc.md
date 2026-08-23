# Miscellaneous Notes

The previous version of this file described an on-device APK build toolchain
(`aapt2`, `kotlinc`, `d8`, `apksigner`, `ToolManager`, `BuildService`,
`BuildCacheManager`), a `setup_env.sh` that installed a JDK and the Android SDK,
and three dependencies this project has never had in its version catalog
(Zipline, LazySodium, Maven Resolver/Aether). None of it exists. What follows is
checked against `gradle/libs.versions.toml` and the source tree.

## Building IDEaz

JDK 21. There is no environment script; Gradle fetches what it needs.

```
./gradlew :app:assembleDebug      # Android APK
./gradlew :app:run                # desktop app
./gradlew :app:testDebugUnitTest  # unit tests
node webruntime/src/test/js/jsx-source-chain.test.mjs   # the element→source chain
```

## What runs on the device

Nothing compiles on the phone. The preview mounts the working tree in a WebView
and Babel transpiles JSX/TS in the browser — see `docs/architecture.md` §4. The
only artifact IDEaz builds is IDEaz.

## External libraries

*   **JGit** (`org.eclipse.jgit`) — all git operations.
*   **OkHttp** + **Retrofit** + **kotlinx-serialization** — the GitHub API client
    and the OpenAI-compatible and Anthropic adapters.
*   **google-genai** (`com.google.genai`) — the Gemini adapter.
*   **BouncyCastle** (`bcprov`/`bcpkix-jdk18on`) — `GithubSecretBox`'s
    libsodium-compatible sealed box, and JGit's security-bumped transitives.
*   **Sora Editor** (`io.github.Rosemoe.sora-editor`) — the code editor.
*   **AzNavRail** (`com.github.HereLiesAz.AzNavRail`) — navigation rail and bottom
    sheet, in both the Android and CMP flavors.
*   **Haze** (`dev.chrisbanes.haze`) — background blur.
*   **slf4j-android** — routes JGit's logging to Logcat.

## Known constraints

*   **No dev server.** Vite-only features do not work in the preview:
    `import.meta.env`, HMR, and glob / `?raw` / `?url` imports. ES module import
    cycles are detected and substituted with an empty module rather than hanging.
*   **Runtime cache.** The bundled `/__ideaz__/` runtime is cacheable and
    segmented by `VERSION_CODE`; project content is always `no-store`. An app
    upgrade therefore cannot serve stale runtime JS, and an edit is never served
    stale.
*   **React dev build.** `react-dom.umd.js` is deliberately the development
    build: `_debugSource` does not exist in the production one, and tap-to-source
    reads it.

## Tips

*   **Logs:** `adb logcat -s IDEaz ProjectConfigManager TemplateManager MainViewModel`.
*   **Crash reports:** `CrashReportingService` runs in a separate
    `:crash_reporter` process so it outlives the main one, and files issues
    against `HereLiesAz/IDEaz`.
