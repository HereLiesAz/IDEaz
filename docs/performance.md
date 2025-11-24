# IDEaz: Performance Considerations

Performance for IDEaz is measured by the perceived speed and reliability of the AI-driven development loop and the responsiveness of the UI inspection service.

## 1. On-Device "No-Gradle" Build Pipeline Performance
This is the most critical performance area. The user experience is directly tied to the speed of the on-device build.

-   **Compilation Speed:** The "No-Gradle" approach, which directly invokes native command-line tools like `libaapt2.so` and `libkotlinc.so`, is designed to be significantly faster and less resource-intensive than a full Gradle build.
-   **Incremental Builds:** The speed of iterative changes hinges entirely on the incremental build system. The file hash comparison must be rapid, and the logic for selectively skipping build steps must be robust to ensure near-instant rebuilds for minor code changes.
-   **Dependency Resolution:** The on-device Maven resolver is a potential bottleneck. Performance will be managed by aggressive caching of downloaded artifacts to minimize network requests.
-   **Toolchain Packaging:** By packaging all executable tools as native libraries in `jniLibs`, we avoid any file extraction or `setExecutable(true)` calls at runtime, improving startup performance and reliability.

## 2. UI Inspection Service Performance
The visual overlay provided by the `AccessibilityService` must feel instant and responsive.

-   **Low Latency:** The time from the user tapping the screen to the UI element's properties being identified and sent to the Host App must be minimal. The service must efficiently traverse the accessibility node tree to avoid any noticeable lag.
-   **Memory Footprint:** As a persistent, privileged service, the UI Inspection Service must have a minimal memory footprint to avoid being terminated by the Android OS.
-   **UI Rendering Overhead:** The service is now responsible for inflating and managing a floating UI using the `WindowManager`. This adds rendering overhead to the service's process. This UI must be lightweight (simple XML or a very constrained ComposeView) to prevent jank or increased memory pressure.

## 3. Multi-Process Architecture and IPC
The stability of the IDE depends on the reliability and performance of its multi-process architecture.

-   **IPC Performance:** Communication between the Host App and the `BuildService` relies on AIDL. Communication with the `UIInspectionService` relies on system Broadcasts. While effective, broadcasts are less performant than a direct binding. This channel must be used only for essential, non-streaming state changes (e.g., "show prompt," "submit prompt"), while the heavier log streaming is one-way (VM to Overlay).
-   **Service Reliability:** The On-Device Build Service and the UI Inspection Service must be robust. As they run in separate processes, their resource consumption must be carefully managed to prevent the OS from terminating them, which would disrupt the entire development workflow.