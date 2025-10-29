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

-   **Foreground Service Guarantee:** The entire interactive `git pull -> compile -> relaunch` loop, including the network call to the Jules API, must be managed within a **Foreground Service**. This is critical to ensure the OS does not kill the process during an active user session and to minimize scheduling latency that could be introduced by other APIs like `WorkManager`. The service's persistent notification also provides necessary transparency to the user that a task is running.
-   **Appropriate Use of `WorkManager`:** `WorkManager` is not suitable for the primary, user-initiated interactive loop due to its deferrable nature. However, it is the recommended tool for other, truly deferrable background tasks, such as pre-fetching dependencies or performing maintenance on the local Git repository when the device is idle and charging.
-   **Battery Consumption:** While the Foreground Service is active, battery consumption will be higher. The service must be designed to stop its foreground status and enter an idle state as soon as a user's request is complete to conserve power.
