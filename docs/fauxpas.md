# Cortex IDE: Common Faux Pas & Best Practices

This document outlines common pitfalls ("faux pas") that developers might encounter while working on the Cortex IDE project. Adhering to the best practices listed here will help ensure the codebase remains clean, performant, and maintainable.

### 1. Blocking the Main Thread
**The Faux Pas:** Performing long-running operations like file I/O (reading/writing project files), networking (API calls to the AI backend), or complex computations directly on Android's main thread. This will cause the application to freeze, leading to an "Application Not Responding" (ANR) error and a terrible user experience.

**The Best Practice:**
-   **Use Kotlin Coroutines:** All I/O-bound or CPU-bound work must be offloaded from the main thread. Use Kotlin Coroutines with the appropriate dispatchers (`Dispatchers.IO` for I/O, `Dispatchers.Default` for CPU-intensive work) for all asynchronous operations.
-   **Leverage ViewModel Scopes:** Launch coroutines from `viewModelScope` to ensure they are automatically cancelled when the ViewModel is cleared, preventing memory leaks and unnecessary work.

### 2. Inefficient State Management
**The Faux Pas:** Managing UI state with mutable variables directly within Composable functions or using multiple, disconnected state holders. This leads to unpredictable UI behavior, bugs that are hard to reproduce, and a codebase that is difficult to reason about.

**The Best Practice:**
-   **Adhere to Unidirectional Data Flow (UDF):** Strictly follow the MVVM architecture outlined in the project blueprint. State should flow down from the ViewModel to the UI, and events should flow up from the UI to the ViewModel.
-   **Use `StateFlow` or `MutableState`:** Expose UI state from ViewModels using `StateFlow` and collect it in the UI using `collectAsStateWithLifecycle()`. Use `mutableStateOf` for state within Composables only for simple, transient UI state.
-   **Events Over Callbacks:** Use a shared `Flow` or a `Channel` to send one-shot events (like showing a toast or navigating) from the ViewModel to the UI.

### 3. "Chatty" or Inefficient Backend Communication
**The Faux Pas:** Sending frequent, small requests or overly large, un-curated data payloads to the backend AI service for every minor action. This will drain the device battery, consume excessive mobile data, increase server costs, and result in high-latency responses.

**The Best Practice:**
-   **Leverage the Client-Side Agent:** As designed in the blueprint, the on-device "client-side agent" must be used to intelligently batch and pre-process context. Debounce user input (e.g., for inline code completion) to avoid sending requests on every keystroke.
-   **Package Context Intelligently:** The client should gather all necessary local context (active file, cursor position, project structure summary) into a single, well-structured request rather than making multiple calls to fetch context.

### 4. JGit Compatibility Issues
**The Faux Pas:** Assuming the latest version of the JGit library will work out-of-the-box on Android. As noted in the blueprint, newer versions of JGit have dependencies on Java NIO APIs that are not fully supported on the Android runtime, which can lead to unexpected crashes.

**The Best Practice:**
-   **Prioritize the JGit Spike:** The task of verifying JGit compatibility must be treated as a high-priority technical spike at the very beginning of the project.
-   **Isolate and Test:** Create a separate test module or a small sample app to thoroughly test all required Git operations (clone, pull, push, commit, branch) on various Android versions before integrating the library into the main application.
-   **Consider a Fork/Patch:** Be prepared to fork and patch an older, more compatible version of JGit if necessary, as was done by previous projects like `agit`.

### 5. Hardcoding Secrets
**The Faux Pas:** Placing sensitive information like API keys, secret keys for signing tokens, or other credentials directly in the source code (e.g., in a Kotlin file or `build.gradle`). This is a major security vulnerability, as these secrets can be easily extracted from the compiled APK.

**The Best Practice:**
-   **Use `secrets-gradle-plugin`:** Store secrets in a `local.properties` file (which is included in `.gitignore`) and access them in the app using the `com.google.android.libraries.mapsplatform.secrets-gradle-plugin`.
-   **Backend Secret Management:** For the backend service, never hardcode secrets. Use a proper secret management solution like Google Secret Manager or environment variables injected into the Cloud Run container.
