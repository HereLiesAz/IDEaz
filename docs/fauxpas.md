# Peridium IDE: Common Faux Pas & Best Practices

This document outlines common pitfalls ("faux pas") that developers might encounter while working on the Peridium IDE.

### 1. Blocking the Main Thread
**The Faux Pas:** Performing long-running operations like compilation or file I/O on the main UI thread.

**The Best Practice:**
-   The on-device build pipeline **must** run in the `On-Device Build Service`, which operates in a separate process to avoid blocking the Host App's UI. All file and network operations in the Host App should use Kotlin Coroutines on `Dispatchers.IO`.

### 2. Insecure API Key Storage
**The Faux Pas:** Storing the user's provided Jules API key insecurely.

**The Best Practice:**
-   The user's API key **must** be stored using Android's `EncryptedSharedPreferences` to encrypt it at rest.

### 3. Poor User Feedback During AI & Build Tasks
**The Faux Pas:** Leaving the user with no feedback during the AI processing and on-device compilation loops.

**The Best Practice:**
-   Provide immediate and continuous feedback using non-blocking UI elements in the Host App. Use the AIDL callback mechanism to stream build logs and status updates from the `On-Device Build Service` to the UI.

### 4. Inaccurate AI Context from Poor Source Mapping
**The Faux Pas:** Failing to provide the AI with precise and sufficient context, leading to incorrect or irrelevant code modifications.

**The Best Practice:**
-   **Precise Source Mapping:** The accuracy of the AI depends on the `source_map.json` generated during the build. This map must be 100% accurate in linking a UI element's resource ID to its exact file path and line number.
-   **Rich Contextual Prompting:** The prompt sent to the Jules API must be programmatically enriched. It should include not only the user's instruction and the selected code snippet but also any other relevant files (e.g., `colors.xml`, `themes.xml`) that provide necessary context for the change.
-   **Acknowledge Limitations:** The system must be designed with the understanding that the AI might make mistakes. The automated AI debugging loop, which feeds compilation errors back to the AI, is a critical part of making the architecture viable.
