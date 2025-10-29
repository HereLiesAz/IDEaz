# Cortex IDE: Common Faux Pas & Best Practices (Screenshot-First Architecture)

This document outlines common pitfalls ("faux pas") that developers might encounter while working on the "Screenshot-First" Cortex IDE.

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
-   Provide immediate and continuous feedback using non-blocking UI elements and a persistent foreground notification for the background service.

### 4. Inaccurate AI Context from Poor Screenshots
**The Faux Pas:** Sending low-resolution or poorly annotated screenshots to the Jules API. The AI's ability to map a visual element to code is entirely dependent on the quality of the image it receives.

**The Best Practice:**
-   **High-Resolution Capture:** Ensure that the screenshot capture process generates a high-quality, lossless PNG image.
-   **Clear Annotation:** The user's selection must be clearly and unambiguously highlighted on the screenshot (e.g., with a bright red, semi-transparent box).
-   **Resilient Prompting:** Structure the prompt to the AI to be resilient to some ambiguity. For example: "The user selected the area in the red box on this screenshot. It appears to be a button with the text 'Submit'. Please find the corresponding component and apply the user's requested change."
-   **Acknowledge Limitations:** The system must be designed with the understanding that the AI might make mistakes. The automated debugging loop is a critical part of making this architecture viable.
