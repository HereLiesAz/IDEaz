# Performance Guidelines

## 1. Threading & Concurrency
*   **Main Thread:** Keep the Main Thread free. No disk I/O, no network calls, no heavy computation.
*   **Coroutines:** Use `Dispatchers.IO` for blocking operations.
*   **Build Service:** The `BuildService` runs in a separate process with background priority (`THREAD_PRIORITY_BACKGROUND`) to keep the UI responsive.

## 2. Memory Management
*   **Large Objects:** Avoid loading large files (like entire APKs) into memory. Use streams.
*   **Bitmaps:** Recycle bitmaps if manually managing them (though Compose handles this mostly).
*   **Accessibility Nodes:** In `UIInspectionService`, recycle `AccessibilityNodeInfo` objects to prevent leaks.
*   **JGit:** Close `Git` instances immediately after use.

## 3. Build Speed
*   **Incremental Builds:** The `BuildOrchestrator` attempts to skip steps if inputs haven't changed.
*   **Parallelism:** Future improvements should run independent steps (e.g., compiling different modules) in parallel.
*   **Caching:** `HttpDependencyResolver` caches artifacts in `filesDir/local-repo`.

## 4. UI Rendering
*   **Lazy Lists:** Use `LazyColumn` for logs and long lists.
*   **Recomposition:** Use `remember` and `derivedStateOf` to minimize recomposition.
*   **Overlays:** The `UIInspectionService` overlay should be lightweight. Avoid complex layouts in the overlay.

## 5. Network
*   **Polling:** The AI activity polling (`MainViewModel`) should use an adaptive interval or long-polling to save battery/data.
*   **Data Usage:** Avoid re-downloading dependencies if they exist in the cache.
