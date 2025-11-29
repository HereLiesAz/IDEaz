# Jules Integration (API)

The IDEaz application integrates with the Jules AI Coding Agent using the `Jules API` client (`JulesApiClient`).

## Jules API Client (`JulesApiClient`)

The `JulesApiClient` is a Retrofit-based Kotlin implementation of the Jules API.

### Key Features
*   **Session Management**: Creates sessions with context (prompt, source repository).
*   **Activity Polling**: Polls for activities (plans, patches, messages) using `listActivities`.
*   **Source Listing**: Fetches available repositories from the user's account.
*   **Robustness**: Handles API authentication via `AuthInterceptor` and standard HTTP error handling.

### Usage in App
*   **MainViewModel**: Uses `JulesApiClient` to:
    *   Fetch the list of owned sources for the Project Screen.
    *   Create new sessions for contextual and contextless prompts.
    *   Poll for "Patch" activities. Note: In the `min-app` architecture, patches are primarily informational or used to guide `git pull` operations, as the actual build is handled remotely.
*   **Integration**: The app constructs valid `SourceContext` strings (e.g., `sources/github/{user}/{repo}`) to ensure correct API routing.

---

## Jules Tools CLI (Legacy)

The `JulesCliClient` is considered legacy and is not used in the active workflow for `min-app`. The application relies entirely on the API client.
