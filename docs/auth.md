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
