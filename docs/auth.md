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
        *   **Git:** `GitManager` (JGit) uses `UsernamePasswordCredentialsProvider` for cloning/pushing private repos.
        *   **API:** `GitHubApiClient` for Releases, Forking, Secrets, and Reporting bugs.
        *   **Header:** `Authorization: Bearer <TOKEN>`
2.  **Google AI Studio API Key (Gemini)**
    *   **Key:** `google_api_key`
    *   **Usage:** Authenticating requests to the Gemini API (`GeminiApiClient`).
    *   **Header:** `x-goog-api-key: <KEY>`
3.  **Jules Project ID**
    *   **Key:** `KEY_JULES_PROJECT_ID`
    *   **Usage:** Identifying the project context for Jules API calls.
4.  **Jules API Key**
    *   **Usage:** Used for all calls to `jules.googleapis.com`.
    *   **Header:** `X-Goog-Api-Key: <KEY>`
    *   **Interceptor:** `AuthInterceptor` injects the key from `SettingsViewModel` into every request.

## 3. Keystore (Android Signing)
*   **Format:** JKS / Keystore file.
*   **Credentials:** Store Password, Key Alias, Key Password.
*   **Management:**
    *   Imported via SAF (`SettingsViewModel.importKeystore`).
    *   Stored in `filesDir/user_release.keystore`.
    *   Used by `ApkSign` build step.

## 4. Security Best Practices
*   **No Hardcoding:** Never hardcode API keys or tokens in the source code.
*   **Log Redaction:** Ensure logs (especially those sent to AI or GitHub) do not contain raw API keys. The `LoggingInterceptor` handles basic redaction of sensitive headers.
*   **Permissions:** The app requests sensitive permissions (Accessibility, Overlay). Respect the user's trust and only use these for their intended purpose.
*   **Encryption:** Use `SecurityUtils` (AES+PBKDF2) for exporting settings.

## 5. Social Sign-On (Planned)
*   **Status:** Not Implemented.
*   **Goal:** Future phases may implement Google Sign-In to simplify the onboarding process, but the BYOK model for API usage will likely remain.
