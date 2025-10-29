# Cortex IDE: Common Faux Pas & Best Practices (Intent-Driven Architecture)

This document outlines common pitfalls ("faux pas") that developers might encounter while working on the intent-driven Cortex IDE.

### 1. Blocking the Main Thread
**The Faux Pas:** Performing any part of the `git pull -> compile -> relaunch` loop on Android's main thread. This will freeze the Cortex IDE's UI for a long time, leading to an "Application Not Responding" (ANR) error.

**The Best Practice:**
-   **Use a Background Service:** The entire automated loop **must** be managed within a long-running background `Service`.
-   **Use `WorkManager` for Reliability:** For the actual AI calls, which can be lengthy, use `WorkManager` with a persistent notification to ensure the OS does not kill the process.
-   **Coroutines for I/O:** Within the service, use Kotlin Coroutines on `Dispatchers.IO` for all file system (JGit) and networking (Jules API) operations.

### 2. Insecure API Key Storage
**The Faux Pas:** Storing the user's provided Jules API key in a regular `SharedPreferences` file or, even worse, in a plaintext file on the device. This would allow a malicious app or a user with a rooted device to easily steal the key.

**The Best Practice:**
-   **Use EncryptedSharedPreferences:** The user's API key **must** be stored using Android's `EncryptedSharedPreferences`. This encrypts the key at rest, providing a strong layer of security.
-   **Handle with Care:** Treat the key as highly sensitive data. Avoid logging it or exposing it unnecessarily within the app.

### 3. Poor User Feedback During AI Tasks
**The Faux Pas:** Leaving the user with no feedback after they've submitted a prompt. The background process can take several minutes, and a lack of communication will make the app feel broken.

**The Best Practice:**
-   **Provide Immediate Feedback:** As soon as the user submits a prompt, show an immediate, non-blocking notification (e.g., "Jules is starting your request...").
-   **Communicate State Changes:** The background Cortex Service should broadcast its current state (e.g., "Pulling changes," "Compiling app," "Debugging error..."). The UI should listen for these broadcasts and display a simple, non-technical status to the user.
-   **Use Persistent Notifications:** For the long-running compilation and AI call steps, the service must post a persistent foreground notification to keep the user informed, even if they leave the Cortex IDE app.

### 4. Brittle Visual-to-Code Mapping
**The Faux Pas:** Assuming the mapping of a user's screen selection to a source code component will be simple or 100% accurate. This is the hardest technical challenge of the project and a brittle implementation will lead to the AI consistently editing the wrong thing.

**The Best Practice:**
-   **Treat as an R&D Spike:** The task of creating this mapping (Task 2.3 in the `todo.md`) must be treated as a dedicated research spike.
-   **Start with Simple Heuristics:** The first version might rely on simple heuristics from the Android View hierarchy (e.g., resource IDs, content descriptions).
-   **Acknowledge Limitations:** The system must be designed with the understanding that this mapping may not always be perfect. The AI prompts should be structured to be resilient to this (e.g., "The user selected a button with the text 'Submit' near the center of the screen...").
