# Performance Guidelines

## 1. Threading & Concurrency
*   **Main Thread:** Keep the Main Thread free. No disk I/O, no network calls, no heavy computation.
*   **Coroutines:** Use `Dispatchers.IO` for blocking operations (File I/O, Network).
*   **Build Service:** `BuildService` runs in a separate process (`:build_process`) at background priority (`THREAD_PRIORITY_BACKGROUND`). Post-Phase-0 it is a thin shell around `RemoteBuildManager` (poll Actions + download APK), so the workload is light.
*   **Scope Management:** Use `viewModelScope` for UI-bound jobs. For long-running work that should survive configuration changes, use a bound `Service`.

## 2. Memory Management
*   **Large Objects:** Avoid loading large files (downloaded APKs) fully into memory. Stream them.
*   **Bitmaps:** Recycle bitmaps if managing them manually.
*   **Accessibility Nodes:** From API 30 (our `minSdk`) onward, `AccessibilityNodeInfo` is auto-managed — do not call `recycle()`. Phase 0 removed all stale `recycle()` calls.
*   **JGit:** Close `Git` instances immediately after use.
*   **View Leaks:** Be careful passing `Activity` or `View` references to background threads / Singletons.

## 3. UI Rendering / Responsiveness
*   **Overlay (Phase 2):** `IdeazOverlayService` runs in its own process to keep main UI smooth. AIDL calls should be `oneway` for non-blocking notifications.
*   **Lazy Lists:** Use `LazyColumn` for log/chat lists. Limit buffer size (e.g. 1000 lines).
*   **Recomposition:** Use `remember` and `derivedStateOf` to minimize recomposition.
*   **Haze/Blur:** Effects are expensive; use sparingly.

## 4. Network
*   **AI streaming:** `ConversationalAiClient` adapters (Phase 1 Gemini) stream responses; render text deltas incrementally rather than waiting for the full response.
*   **Polling:** AI polling loops (Jules in Phase 2) use an adaptive interval (~3s) and **never time out** — agents need time to think.
*   **Timeouts:** Set generous timeouts (60s) for AI requests.
*   **Retry:** Exponential backoff on network failures via `RetryInterceptor`.
*   **Compression:** Ensure API responses are GZIP compressed.

## 5. WebView (Phase 1)
*   **Asset loading:** Phase 1 will use `WebViewAssetLoader` so the PWA gets a proper origin (`https://appassets.androidplatform.net`) — much faster than `file://` URIs and unlocks service worker support.
*   **Reload:** Default to a hard reload after AI edits land. Cache-busting is a polish concern.

## 6. APK Size
The Phase 0 deletion pass dropped tens of MB of native libraries (React Native `.so`s, libsodium, JNA, the entire on-device toolchain). The release APK should now strip cleanly. Track size in `phase-0-complete.md` after Task 13.
