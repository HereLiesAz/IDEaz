# Cortex IDE: Performance Considerations (On-Device Architecture)

Performance for the Cortex IDE is measured by the perceived speed and reliability of the automated `Git -> Compile -> Relaunch` loop, and the responsiveness of the visual overlay.

## 1. On-Device Compile Loop Performance
This is the most critical performance bottleneck. The entire user experience depends on this loop being as fast as possible.

-   **Compilation Speed:** The on-device Gradle build is the slowest part of the process. We must heavily optimize the Gradle configuration for speed, potentially by:
    -   Enabling the Gradle Daemon.
    -   Using incremental compilation.
    -   Exploring other Gradle performance optimizations suitable for an Android environment.
-   **`git pull` Speed:** Network speed will impact how quickly the Cortex Service can pull new commits from the "Invisible Repository." The app should provide clear feedback to the user during this step.
-   **App Relaunch Speed:** The process of installing the new APK and restarting the user's application should be as seamless as possible to reduce user disorientation.

## 2. Cortex Overlay Performance
The visual overlay must feel instant and responsive.

-   **Low Latency:** The time from the user tapping the screen to the selection highlight and contextual prompt appearing must be minimal. The overlay service needs to be lightweight and highly optimized.
-   **Screenshot & Analysis:** The process of capturing a screenshot and analyzing it to determine context for the AI must be fast enough not to introduce noticeable lag. This process should be offloaded to a background thread to keep the UI responsive.

## 3. Background Service Reliability
The on-device "Cortex Service" must be robust and reliable, even on a resource-constrained mobile device.

-   **Resource Management:** The service must be careful with CPU and memory usage to avoid being killed by the Android OS. Long-running tasks like compilation must be handled gracefully.
-   **Battery Consumption:** Continuous background processing can drain the battery. The Cortex Service should be designed to be idle whenever possible and only consume significant resources when actively processing a user's request. `WorkManager` should be used to schedule tasks efficiently.
