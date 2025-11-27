# Data Layer Specification

## Overview
IDEaz uses a file-system-centric data layer combined with `SharedPreferences` for configuration. It does **not** currently use a relational database like Room, deviating from initial specifications to reduce complexity and dependency overhead.

## 1. Project Storage (`filesDir`)
Projects are stored in the application's internal private storage to ensure sandbox isolation.
*   **Root:** `context.filesDir`
*   **Project Path:** `context.filesDir/{projectName}`
*   **Access:** Direct `java.io.File` access.
*   **Backup:** The entire `filesDir` is subject to Android Auto Backup (configured in `backup_rules.xml`).

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
*   **Concurrency:** `MainViewModel` uses a `Mutex` (`gitMutex`) to serialize Git operations and prevent index locking issues.

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
