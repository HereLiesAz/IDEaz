# Jules Integration (API & CLI)

The IDEaz application integrates with the Jules AI Coding Agent using a hybrid approach. While the `Jules Tools CLI` is packaged with the application, the primary interaction mechanism on Android is now the `Jules API` client (`JulesApiClient`) due to execution stability issues with the CLI binary on some devices.

## Jules API Client (`JulesApiClient`)

The `JulesApiClient` is a Retrofit-based Kotlin implementation of the Jules API. It mirrors the structure of the official TypeScript SDK (`@kiwina/jules-api-sdk`).

### Key Features
*   **Session Management**: Creates sessions with context (prompt, source repository).
*   **Activity Polling**: Polls for activities (plans, patches, messages) using `listActivities`.
*   **Source Listing**: Fetches available repositories from the user's account.
*   **Robustness**: Handles API authentication via `AuthInterceptor` and standard HTTP error handling.

### Usage in App
*   **MainViewModel / AIDelegate**: Uses `JulesApiClient` to:
    *   Fetch the list of owned sources for the Project Screen.
    *   Create new sessions for contextual and contextless prompts.
    *   Poll for "Patch" activities to automatically apply code changes.
*   **Integration**: The app constructs valid `SourceContext` strings (e.g., `sources/github/{user}/{repo}`) to ensure correct API routing.

---

## Jules Tools CLI (Legacy / Reference)

*Note: The CLI integration (`JulesCliClient`) is preserved in the codebase (and the binary `libjules.so` is bundled) but is currently **bypassed** in favor of the API client for core workflows.*

The legacy CLI integration was designed to wrap the `jules` command line tool.

### Legacy Implementation Details
*   **Binary:** `libjules.so` (Node.js runtime + script).
*   **Wrapper:** `JulesCliClient.kt`.
*   **Issues:** On-device execution of the Node runtime proved unstable on certain Android API levels due to seccomp filters and signal handling.
*   **Status:** Retained for potential future use or debugging, but not active in the production flow.
