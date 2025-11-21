# IDEaz IDE: Authentication & Tooling

This document outlines the authentication strategy for the IDEaz IDE application and the management of its core toolchain.

## 1. User Authentication for IDEaz IDE
**Goal:** To provide a seamless and secure login experience for users of the IDEaz IDE app itself.

The authentication for the main app will follow standard, user-friendly web and mobile practices.

-   **Primary Method: Social Sign-On:** Users will sign in using trusted third-party providers like Google. This avoids the need for traditional password management.
-   **Authentication Flow:** The app will use a standard OAuth 2.0 flow to authenticate the user and receive a JWT to maintain the session.

## 2. API Key Management: The "Bring Your Own Key" (BYOK) Model
**Goal:** To ensure all calls to the Jules and Gemini APIs are authenticated without exposing a secret key in the app.

User authentication is completely separate from API authentication. To use the AI's code generation capabilities, the user must provide their own API keys.

-   **User-Provided Keys:** The user is responsible for obtaining their own API keys from the respective platforms (Jules, Google AI Studio).
-   **Input and Storage:** The user will enter these keys into the "Settings" screen. The app will then save these keys securely on the device using Android's **`EncryptedSharedPreferences`**.
-   **Usage:**
    -   **Jules:** The `JulesCliClient` does not use the API key directly. It is assumed the native `libjules.so` binary is pre-configured or handles its own auth (e.g., prompting for a login) via its CLI interface. The `JulesApiClient` uses the key for HTTP requests.
    -   **Gemini:** The `GeminiApiClient` retrieves the securely stored key and uses it for all HTTP API calls.

## 3. GitHub Authentication
**Goal:** To enable repository management and automated bug reporting.

-   **Personal Access Token (PAT):** The user provides a GitHub PAT with `repo` scope in the Settings screen.
-   **Usage:**
    -   **Project Management:** Used by `GitManager` and `GitHubApiClient` to clone repositories, create new repositories, and push changes.
    -   **Automated Bug Reporting:** Used by `GithubIssueReporter` to automatically open issues on the `HereLiesAz/IDEaz` repository when an internal IDE error occurs. If no token is present (or the API call fails), the app falls back to opening a browser window with the issue details pre-filled.