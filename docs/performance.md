# Performance Guidelines

## 1. Build Speed
*   **Caching:** `BuildCacheManager` caches intermediate outputs (`.flat`, `.dex`).
    *   **Rule:** If inputs (source file hash + dependency hash) haven't changed, skip the step.
*   **Parallelism:**
    *   Downloading dependencies (`HttpDependencyResolver`) should be parallelized.
    *   **Note:** `aapt2` and `d8` are CPU intensive; avoid running them concurrently with UI heavy tasks.

## 2. UI Responsiveness
*   **Overlay:** The overlay service runs in a separate process (`:inspection_service`) to avoid jank in the main UI, but AIDL calls are synchronous by default.
    *   **Fix:** Use `oneway` in AIDL for non-blocking notifications.
*   **Lists:** Use `LazyColumn` for logs. Limit the buffer size (e.g., 1000 lines) to prevent OOM.

## 3. Memory Management
*   **Large Files:** Do not read entire APKs or large source files into String variables. Use Streams.
*   **Bitmaps:** Recycle bitmaps in `ScreenshotService`.

## 4. Network
*   **Timeouts:** Set generous timeouts (60s) for AI requests as LLMs can be slow.
*   **Retry:** Implement exponential backoff for network failures (`RetryInterceptor`).
