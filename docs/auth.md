# Authentication & Security

## 1. Overview
IDEaz currently operates on a **Bring Your Own Key (BYOK)** model. It does not have a centralized backend for user accounts. Authentication is handled locally via keys stored on the device.

## 2. API Key Management

### Storage Mechanism
*   **Implementation:** Keys are stored in Android's `SharedPreferences` (specifically `PreferenceManager.getDefaultSharedPreferences(context)`).
*   **Security Note:** Currently, standard `SharedPreferences` is used. Future improvements should migrate to `EncryptedSharedPreferences` for better security.

### Supported Keys
1.  **GitHub Personal Access Token (PAT)**
    *   **Key:** `KEY_GITHUB_TOKEN`
    *   **Scope Required:** `repo`, `workflow`, `contents: write`.
    *   **Usage:**
        *   Cloning private repositories (`GitManager`).
        *   Pushing changes (`GitManager`).
        *   Creating repositories (`MainViewModel`).
        *   Reporting bugs (`GithubIssueReporter`).
2.  **Google AI Studio API Key (Gemini)**
    *   **Key:** `google_api_key`
    *   **Usage:** Authenticating requests to the Gemini API (`GeminiApiClient`).
3.  **Jules Project ID**
    *   **Key:** `KEY_JULES_PROJECT_ID`
    *   **Usage:** Identifying the project context for Jules API calls.

## 3. GitHub Integration
*   **Credentials Provider:** `GitManager` uses `UsernamePasswordCredentialsProvider` with the stored GitHub user and token.
*   **User Identity:** `KEY_GITHUB_USER` stores the username.
*   **Fallback:** If no token is present, read-only operations on public repos might work, but push operations will fail.

## 4. Social Sign-On (Planned)
*   **Status:** Not Implemented.
*   **Goal:** Future phases may implement Google Sign-In to simplify the onboarding process, but the BYOK model for API usage will likely remain.

## 5. Security Best Practices for Agents
*   **No Hardcoding:** Never hardcode API keys or tokens in the source code.
*   **Log Redaction:** Ensure logs (especially those sent to AI or GitHub) do not contain raw API keys. The `LoggingInterceptor` should handle this.
*   **Permissions:** The app requests sensitive permissions (Accessibility, Overlay). Respect the user's trust and only use these for their intended purpose.
