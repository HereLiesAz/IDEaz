# Data Layer Documentation

This document describes how IDEaz manages data, configuration, and state.

## 1. Preferences (`SettingsViewModel`)
User configuration is stored in `SharedPreferences` (XML).

### Key Storage
*   **API Keys:** Stored in plaintext in `SharedPreferences` (Private Mode).
    *   `api_key`: Jules API Key.
    *   `google_api_key`: Gemini API Key.
    *   `github_token`: GitHub PAT.
*   **Security:**
    *   **Export:** When exporting settings, keys are encrypted using AES-256 with a key derived from a user password via `PBKDF2WithHmacSHA256`.
    *   **Import:** Decrypts the JSON payload and restores preferences.

### Project Configuration
*   **Current Project:** `app_name`, `github_user`, `branch_name`.
*   **Project List:** `project_list` (Set<String>) stores names of local projects.
*   **Project Paths:** `project_paths` (JSON Map) maps names to absolute paths.

## 2. API Integration

### Jules API (`JulesApiClient`)
*   **Purpose:** Interaction with the Jules AI Coding Agent.
*   **Endpoints:**
    *   `POST /sessions`: Create a new coding session.
    *   `POST /sendMessage`: Send a prompt to an existing session.
    *   `GET /activities`: Poll for agent responses and artifacts (patches).
    *   `GET /sources`: List available repositories.
*   **Data Models:** Defined in `api/models.kt`.
*   **Authentication:** `X-Goog-Api-Key` header via `AuthInterceptor`.

### GitHub API (`GitHubApiClient`)
*   **Purpose:** Repository management (Clone, Fork, Release check).
*   **Authentication:** Bearer Token (`github_token`).

## 3. Local File System

### Project Directory Structure (`filesDir/{ProjectName}`)
*   **`src/`**: Source code.
*   **`build/`**: Local build artifacts (excluded from Git).
*   **`setup_env.sh`**: Environment setup script.
*   **`AGENTS.md`**: Agent instructions.

### Build Cache (`cacheDir/build_cache`)
*   Stores hashes of inputs to skip redundant build steps (e.g., `Aapt2Compile`).

## 4. Git Layer (`GitManager`)
*   **Library:** JGit (Pure Java implementation of Git).
*   **Operations:**
    *   `clone`: Initialize from remote.
    *   `commit`: Save local changes.
    *   `push`: Sync with remote.
    *   `diff`: Generate patch for AI.
*   **Authentication:** Uses `UsernamePasswordCredentialsProvider` with the GitHub Token.

## 5. State Management (`StateDelegate`)
*   **Global State:** Shared across the app via `MainViewModel` delegates.
*   **Logs:** `_buildLog` (MutableStateFlow) buffers build output.
*   **Progress:** `_isBuilding` (Boolean) and `_buildProgress` (Float).
