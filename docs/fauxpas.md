# IDEaz IDE: Common Faux Pas & Best Practices (Screenshot-First Architecture)

This document outlines common pitfalls ("faux pas") that developers might encounter while working on the "Screenshot-First" IDEaz IDE.

### 1. Blocking the Main Thread
**The Faux Pas:** Performing any part of the `git pull -> compile -> relaunch` loop on Android's main thread.

**The Best Practice:**
-   The entire automated loop **must** be managed within a long-running background `Service`. Use Kotlin Coroutines on `Dispatchers.IO` for all file system and networking operations.

### 2. Insecure API Key Storage
**The Faux Pas:** Storing the user's provided Jules API key insecurely.

**The Best Practice:**
-   The user's API key **must** be stored using Android's `EncryptedSharedPreferences` to encrypt it at rest.

### 3. Poor User Feedback During AI Tasks
**The Faux Pas:** Leaving the user with no feedback during the multi-minute compilation and AI processing loop.

**The Best Practice:**
-   Provide immediate and continuous feedback. The architecture now supports two distinct feedback channels:
    -   **Contextual (Overlay):** For element-specific AI tasks, the `UIInspectionService` must show a floating log box.
    -   **Global (Bottom Sheet):** For builds and contextless AI tasks, the main app's bottom sheet must show a live log.

### 4. Inaccurate AI Context from Poor Screenshots
**The Faux Pas:** Sending low-resolution or poorly annotated screenshots to the Jules API. The AI's ability to map a visual element to code is entirely dependent on the quality of the image it receives. (Note: This is less relevant now as we are using source mapping, but the principle of good context remains).

**The Best Practice:**
-   **Precise Source Mapping:** Ensure the `source_map.json` is generated correctly and that the `UIInspectionService` accurately identifies the `RESOURCE_ID`.
-   **Resilient Prompting:** Structure the prompt to the AI to be resilient to some ambiguity. For example: "The user selected the element with ID `login_button` in `activity_main.xml`. Please find the corresponding component and apply the user's requested change."

### 5. Mixing Log Streams
**The Faux Pas:** Sending build/compile logs to the contextual (overlay) UI, or sending a contextual AI chat log to the global (bottom sheet) console.

**The Best Practice:**
-   **Strict Separation:** The `MainViewModel` must strictly enforce the dual-log system:
    -   `UIInspectionService` overlay *only* receives AI chat logs for the task it initiated.
    -   `IdeBottomSheet` *only* receives global build/compile logs and contextless AI chat.
-   This separation prevents user confusion and keeps the UI clean.