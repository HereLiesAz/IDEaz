# Authentication Documentation

## 1. Jules API Authentication
*   **Method:** API Key.
*   **Header:** `X-Goog-Api-Key: <KEY>`
*   **Interceptor:** `AuthInterceptor` injects the key from `SettingsViewModel` into every request.
*   **Storage:** `SharedPreferences` (Private).
*   **Usage:** Used for all calls to `jules.googleapis.com`.

## 2. GitHub Authentication
*   **Method:** Personal Access Token (PAT).
*   **Header:** `Authorization: Bearer <TOKEN>`
*   **Usage:**
    *   **API:** `GitHubApiClient` for Releases, Forking, Secrets.
    *   **Git:** `GitManager` (JGit) uses `UsernamePasswordCredentialsProvider` (User + Token).

## 3. Google Gemini (Vertex AI)
*   **Method:** API Key (AI Studio).
*   **Header:** `x-goog-api-key: <KEY>`
*   **Usage:** Used for `GeminiApiClient` (Contextual Chat / Fallback).

## 4. Keystore (Android Signing)
*   **Format:** JKS / Keystore file.
*   **Credentials:** Store Password, Key Alias, Key Password.
*   **Management:**
    *   Imported via SAF (`SettingsViewModel.importKeystore`).
    *   Stored in `filesDir/user_release.keystore`.
    *   Used by `ApkSign` build step.

## 5. Security Best Practices
*   **No Hardcoding:** Never hardcode keys in source files.
*   **Logs:** Redact sensitive headers in `HttpLoggingInterceptor`.
*   **Encryption:** Use `SecurityUtils` (AES+PBKDF2) for exporting settings.


<!-- Merged Content from docs/docs/auth.md -->

# Authentication & Security

## 1. Overview
IDEaz currently operates on a **Bring Your Own Key (BYOK)** model. It does not have a centralized backend for user accounts. Authentication is handled locally via keys stored on the device.

## 2. API Key Management

### Storage Mechanism
*   **Implementation:** Keys are stored in Android's `SharedPreferences` (specifically `PreferenceManager.getDefaultSharedPreferences(context)`).
*   **Encryption:** Critical keys like `KEY_GITHUB_TOKEN` and API keys are protected using `SecurityUtils` (AES encryption with PBKDF2 derived keys) when exported/imported.
*   **Best Practice:** Future improvements should migrate internal storage to `EncryptedSharedPreferences` for better security at rest.

### Supported Keys
1.  **GitHub Personal Access Token (PAT)**
    *   **Key:** `KEY_GITHUB_TOKEN`
    *   **Scope Required:** `repo`, `workflow`, `contents: write`.
    *   **Usage:**
        *   Cloning private repositories (`GitManager`).
        *   Pushing changes (`GitManager`).
        *   Creating repositories (`RepoDelegate`).
        *   Reporting bugs (`GithubIssueReporter`).
        *   Remote Builds (GitHub Actions).
2.  **Google AI Studio API Key (Gemini)**
    *   **Key:** `google_api_key`
    *   **Usage:** Authenticating requests to the Gemini API (`GeminiApiClient`).
3.  **Jules Project ID**
    *   **Key:** `KEY_JULES_PROJECT_ID`
    *   **Usage:** Identifying the project context for Jules API calls.

## 3. GitHub Integration
*   **Credentials Provider:** `GitManager` uses `UsernamePasswordCredentialsProvider` with the stored GitHub user and token.
*   **User Identity:** `KEY_GITHUB_USER` stores the username.
*   **Fallback:** If no token is present, read-only operations on public repos might work, but push/private operations will fail.

## 4. Social Sign-On (Planned)
*   **Status:** Not Implemented.
*   **Goal:** Future phases may implement Google Sign-In to simplify the onboarding process, but the BYOK model for API usage will likely remain.

## 5. Security Best Practices for Agents
*   **No Hardcoding:** Never hardcode API keys or tokens in the source code.
*   **Log Redaction:** Ensure logs (especially those sent to AI or GitHub) do not contain raw API keys. The `LoggingInterceptor` handles basic redaction.
*   **Permissions:** The app requests sensitive permissions (Accessibility, Overlay). Respect the user's trust and only use these for their intended purpose.
