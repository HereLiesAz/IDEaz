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
    *   `KEY_JULES_PROJECT_ID` (String): Project ID for Jules API (Phase 2).
    *   `google_api_key` (String): Gemini API Key (Phase 1 default provider).
    *   `project_type` (String/Enum): Current project type (`ANDROID`, `WEB`; Phase 1 adds `PWA`).
    *   `last_opened_project` (String): Name of the last loaded project.
    *   `KEY_THEME` (Boolean/Int): Theme preference.
    *   `KEY_LOG_VERBOSITY` (String): Filter level for logs.

## 3. Git Data (`JGit`)
Version control data is managed by the JGit library, which interacts directly with the `.git` directory within each project folder.
*   **Storage:** Standard Git object database (`.git/objects`, `.git/refs`).
*   **Concurrency:** `MainViewModel` (via `GitDelegate`) uses a `Mutex` to serialize Git operations.

## 4. Build Artifacts
Builds happen on GitHub Actions (Phase 2 Android target only); PWAs need no build. The on-device toolchain (`aapt2`, `d8`, `kotlinc`) and its caches were removed in Phase 0. `RemoteBuildManager` downloads the Actions artifact into `context.cacheDir` for installation, then deletes it.

## 5. Reporting Deduplication
`GithubIssueReporter` uses a dedicated `SharedPreferences` file or keys to track reported error hashes.
*   **Mechanism:** Stores a hash of the stack trace + timestamp.
*   **Policy:** Prevents duplicate reports for the same error within 24 hours.

## 6. Static Data & Assets
*   **Templates:** `assets/templates/` (Copied to new project directories). Phase 1 will add a single opinionated PWA template.
*   **Workflows:** Managed programmatically by `ProjectConfigManager`. YAML content is hardcoded in the codebase to ensure integrity even if assets are missing.

(There is no longer an `assets/tools/` directory — the on-device build toolchain was removed in Phase 0.)

## 7. Configuration Models (`.ideaz/`)
*   **Config:** `config.json` stores project-specific settings (e.g., detected package name, schema type).
*   **History:** `prompt_history.json` stores local prompt history for the project.
*   **Screenshots:** `screenshots/` directory stores captured context images.
