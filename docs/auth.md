# Authentication & Security

## 1. Overview
IDEaz currently operates on a **Bring Your Own Key (BYOK)** model. It does not have a centralized backend for user accounts. Authentication is handled locally via keys stored on the device.

## 2. API Key Management

### Storage Mechanism
*   **Secure credentials:** Every provider token/key, plus the app-signing passwords, are stored as AES-GCM ciphertext in the dedicated `ideaz_secure_credentials` preferences file (`AndroidKeystoreCredentialStore`). Its non-exportable AES key is generated and retained by Android Keystore. Preference keys are hashed (SHA-256), so neither credentials nor provider names are stored in plaintext there. The full set of secure keys (`SettingsViewModel.SECURE_CREDENTIAL_KEYS`): Jules API key, GitHub PAT, Google AI Studio (Gemini) key, and the six free/paid OpenAI-compatible provider keys (Groq, Cerebras, Hugging Face, Mistral, OpenAI, Anthropic, DeepSeek), plus the keystore and key-signing passwords. Everything else (theme, log level, AI-provider *assignment* choices, project paths, GitHub username/branch, etc.) stays in ordinary default `SharedPreferences` since none of it is a secret.
*   **Encryption detail:** `Cipher.init(ENCRYPT_MODE, key)` is called with **no caller-supplied IV** — the AndroidKeyStore provider generates one internally and it's read back via `cipher.iv` before being stored alongside the ciphertext. An AndroidKeyStore-backed key created with `setRandomizedEncryptionRequired(true)` (the default) rejects a caller-provided IV outright (`InvalidAlgorithmParameterException`); this bit a full pass of secure-credential saves in production for a stretch before being caught and fixed. See `encryptCredential`/`decryptCredential` in `AndroidKeystoreCredentialStore.kt` for the reasoning, and don't reintroduce a caller-supplied IV on the encrypt path.
*   **Migration:** Reading a legacy plaintext credential (from before a given key was moved into `SECURE_CREDENTIAL_KEYS`, or from an older app version) first writes it successfully to the secure store, then removes the default-preferences value. A failed secure write leaves the legacy value intact rather than converting a security migration into a credential shredder.
*   **Failure visibility:** Every secure-credential read/write/migration failure is logged with the underlying exception (`SettingsViewModel.lastCredentialError`) and surfaced directly in the Settings screen's save-result Toast (e.g. "GitHub Token Save Failed: <reason>"), since most devices this ships to have no adb/logcat access. A cleanup-only failure (removing an already-migrated legacy plaintext entry) is logged but never reported as a save failure — only the real credential write's own outcome determines success.
*   **Backup:** Both default credential preferences and the secure ciphertext preferences are excluded from cloud backup and device transfer because restored ciphertext would not have its original Keystore key.
*   **Explicit export:** User-requested settings export may include the token only inside the `SecurityUtils` payload protected by a password of at least eight characters. Routine logs, WorkManager data, notifications, and automatic backups do not include it.

### Supported Keys
1.  **GitHub Personal Access Token (PAT)**
    *   **Key:** `KEY_GITHUB_TOKEN`
    *   **Scope Required:** `repo`, `workflow`, `contents: write`.
    *   **Usage:**
        *   **Git:** `GitManager` (JGit) uses `UsernamePasswordCredentialsProvider` for cloning/pushing private repos.
        *   **API:** `GitHubApiClient` for Releases, Forking, Secrets, and Reporting bugs.
        *   **Header:** `Authorization: Bearer <TOKEN>`
        *   **Save side effect:** saving a valid token immediately triggers `MainViewModel.fetchGitHubRepos()`, refreshing the Clone tab's repo list without waiting for the tab to remount.
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
5.  **Free-tier OpenAI-compatible providers** — Groq (`KEY_GROQ_API_KEY`), Cerebras (`KEY_CEREBRAS_API_KEY`), Hugging Face Inference (`KEY_HF_API_KEY`, also gates on-device model downloads), Mistral (`KEY_MISTRAL_API_KEY`).
6.  **Paid-tier OpenAI-compatible providers** — OpenAI (`KEY_OPENAI_API_KEY`), Anthropic (`KEY_ANTHROPIC_API_KEY`), DeepSeek (`KEY_DEEPSEEK_API_KEY`).
    *   All six route through `AiAdapterFactory`/`OpenAiCompatibleAdapter`; each key is entered once in Settings and selected per-task via the AI Assignments dropdowns, or picked automatically as the app's default model (see §2.1).

### 2.1 Default model selection
`SettingsViewModel.getAiAssignment(KEY_AI_ASSIGNMENT_DEFAULT)` no longer hardcodes Gemini. When the user hasn't explicitly chosen a "Default" AI assignment, it walks `AiModels.defaultRanking` (Gemini → Anthropic → OpenAI → DeepSeek → Groq → Cerebras → HF → Mistral → Jules → Gemini CLI, on-device fallbacks last) and picks the highest-ranked model whose required key is actually saved, falling back to Gemini only when no provider key has been entered anywhere. An explicit "Default" choice from the AI Assignments dropdown always wins over the ranked auto-pick.

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
*   **Permissions:** The app requests sensitive permissions (Accessibility, Overlay, Post Notifications, Install Unknown Apps). Broad storage permissions (`MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE`) and `PACKAGE_USAGE_STATS`/`QUERY_ALL_PACKAGES` were removed during the P0.2 permission-minimization pass; SAF pickers cover file access instead, and package visibility is scoped to a `<queries>` block naming only the specific packages IDEaz needs to see. Respect the user's trust and only use the remaining permissions for their intended purpose.
*   **Encryption:** Use `SecurityUtils` (AES+PBKDF2) for exporting settings.
*   **At-rest secrets:** Use `AndroidKeystoreCredentialStore` for every key in `SECURE_CREDENTIAL_KEYS` (§2); never copy them back into default preferences for convenience.

## 5. Social Sign-On (Planned)
*   **Status:** Not Implemented.
*   **Goal:** Future phases may implement Google Sign-In to simplify the onboarding process, but the BYOK model for API usage will likely remain.
