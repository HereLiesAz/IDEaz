# Performance Guidelines

## 1. Threading & Concurrency
*   **Main Thread:** Keep the Main Thread free. No disk I/O, no network calls, no heavy computation.
*   **Coroutines:** Use `Dispatchers.IO` for blocking operations (File I/O, Network).
*   **Build Service:** The `BuildService` runs in a separate process (`:build_process`) with background priority (`THREAD_PRIORITY_BACKGROUND`) to keep the UI responsive.
*   **Scope Management:** Use `viewModelScope` appropriately. For long-running operations that should survive configuration changes, use `WorkManager` or a bound Service (`BuildService`).

## 2. Memory Management
*   **Large Objects:** Avoid loading large files (like entire APKs) into memory. Use streams.
*   **Bitmaps:** Recycle bitmaps if manually managing them (though Compose handles this mostly).
*   **Accessibility Nodes:** In `UIInspectionService`, recycle `AccessibilityNodeInfo` objects to prevent leaks.
*   **JGit:** Close `Git` instances immediately after use.
*   **View Leaks:** Be careful with passing `Activity` or `View` references to background threads or Singletons.

## 3. Build Speed
*   **Configuration Cache:** Enabled in `gradle.properties` (`org.gradle.configuration-cache=true`) to significantly reduce configuration time on subsequent builds.
*   **Incremental Builds:** The `BuildOrchestrator` attempts to skip steps if inputs haven't changed.
    *   `BuildCacheManager` caches intermediate outputs (`.flat`, `.dex`).
*   **Parallelism:**
    *   Downloading dependencies (`HttpDependencyResolver`) should be parallelized.
    *   Future improvements should run independent steps (e.g., compiling different modules) in parallel.
    *   **Note:** `aapt2` and `d8` are CPU intensive; avoid running them concurrently with UI heavy tasks.

## 4. UI Rendering / Responsiveness
*   **Overlay:** The overlay service runs in a separate process (`:inspection_service`) to avoid jank in the main UI, but AIDL calls are synchronous by default.
    *   **Fix:** Use `oneway` in AIDL for non-blocking notifications.
*   **Lazy Lists:** Use `LazyColumn` for logs and long lists. Limit buffer size (e.g. 1000 lines).
*   **Recomposition:** Use `remember` and `derivedStateOf` to minimize recomposition.
*   **Haze/Blur:** Haze effects are expensive; use sparingly or disable on low-end devices.

## 5. Network
*   **Polling:** The AI polling loop (`AIDelegate`) uses an adaptive interval (e.g., 3s) to balance responsiveness and data usage.
*   **Timeouts:** Set generous timeouts (60s) for AI requests as LLMs can be slow.
*   **Retry:** Implement exponential backoff for network failures (`RetryInterceptor`).
*   **Compression:** Ensure API responses are GZIP compressed.
