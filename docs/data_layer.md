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


<!-- Merged Content from docs/docs/data_layer.md -->

# Data Layer Specification

## Overview
IDEaz uses a file-system-centric data layer combined with `SharedPreferences` for configuration. It does **not** currently use a relational database like Room, deviating from initial specifications to reduce complexity and dependency overhead.

## 1. Project Storage (`filesDir` & External)
Projects are primarily stored in the application's internal private storage, but can also be registered from external storage.
*   **Internal Root:** `context.filesDir/projects`
*   **Internal Path:** `context.filesDir/projects/{projectName}`
*   **External Projects:** Projects may reside in external storage (e.g., Documents, SD Card) if registered by the user.
    *   **Import:** External projects are typically copied to internal storage for performance and compatibility with build tools (which often dislike `content://` URIs).
    *   **Mapping:** `SettingsViewModel` stores a `project_paths` JSON map linking Project Name -> Filesystem Path.
*   **Backup:** The entire `filesDir` is subject to Android Auto Backup. External projects are **not** automatically backed up by the app's internal backup rules.

## 2. Configuration (`SharedPreferences`)
User settings and lightweight state are stored in `SharedPreferences`.
*   **File:** Default shared preferences.
*   **Key Constants:** Defined in `SettingsViewModel`.
    *   `KEY_GITHUB_USER` (String): GitHub username.
    *   `KEY_GITHUB_TOKEN` (String): GitHub PAT.
    *   `KEY_JULES_PROJECT_ID` (String): Project ID for Jules API.
    *   `google_api_key` (String): Gemini API Key.
    *   `project_type` (String/Enum): Current project type (ANDROID, WEB, etc.).
    *   `last_opened_project` (String): Name of the last loaded project.
    *   `KEY_THEME` (Boolean/Int): Theme preference.
    *   `KEY_LOG_VERBOSITY` (String): Filter level for logs.

## 3. Git Data (`JGit`)
Version control data is managed by the JGit library, which interacts directly with the `.git` directory within each project folder.
*   **Storage:** Standard Git object database (`.git/objects`, `.git/refs`).
*   **Concurrency:** `MainViewModel` (via `GitDelegate`) uses a `Mutex` to serialize Git operations.

## 4. Build Artifacts
Build outputs are strictly isolated.
*   **Location:** `{projectDir}/build/`
*   **Clean:** This directory can be safely deleted to force a clean build.
*   **Cache:** `{projectDir}/build/cache/` (Managed by `BuildCacheManager`).

## 5. Reporting Deduplication
`GithubIssueReporter` uses a dedicated `SharedPreferences` file or keys to track reported error hashes.
*   **Mechanism:** Stores a hash of the stack trace + timestamp.
*   **Policy:** Prevents duplicate reports for the same error within 24 hours.

## 6. Static Data (Assets)
*   **Tools:** `assets/tools/` (Copied to `filesDir/tools` on launch).
*   **Templates:** `assets/templates/` (Copied to new project directories).
*   **Workflows:** `assets/workflows/` (Injected into `.github/workflows`).
