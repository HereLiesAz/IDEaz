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
Nearly every credential-shaped value is the exception, not just Gemini/Hugging Face:
`AndroidKeystoreCredentialStore` stores AES-GCM ciphertext for each key in
`SettingsViewModel.SECURE_CREDENTIAL_KEYS` in a dedicated private preferences file,
backed by a non-exportable Android Keystore key. That set covers the GitHub PAT,
every AI provider key (Jules, Gemini, Hugging Face, Groq, Cerebras, Mistral, OpenAI,
Anthropic, DeepSeek), and the keystore/key-signing passwords — 12 keys total, see
`docs/auth.md` §2 for the full accounting. Both credential-bearing preference files are excluded
from backup and device transfer.
*   **File:** Default shared preferences (values in `SECURE_CREDENTIAL_KEYS` instead live in the separate `ideaz_secure_credentials` file).
*   **Key Constants:** Defined in `SettingsViewModel`.
    *   `KEY_GITHUB_USER` (String): GitHub username (not a secret; stays in default preferences).
    *   `KEY_GITHUB_TOKEN` / `github_token` (String, **secure store**): GitHub PAT.
    *   `KEY_JULES_PROJECT_ID` (String): Project ID for Jules API (Phase 2).
    *   `google_api_key` (**secure store**): Gemini API key used by the default provider and consented local-model consultation.
    *   `hf_api_key` (**secure store**): Hugging Face token used by gated model downloads and hosted inference.
    *   `groq_api_key`, `cerebras_api_key`, `mistral_api_key`, `openai_api_key`, `anthropic_api_key`, `deepseek_api_key` (**secure store**): the remaining OpenAI-compatible provider keys, all routed through `AiAdapterFactory`/`OpenAiCompatibleAdapter`.
    *   `keystore_pass`, `key_pass` (**secure store**): Android app-signing passwords.
    *   `project_type` (String/Enum): Current project type (`ANDROID`, `WEB`; Phase 1 adds `PWA`).
    *   `last_opened_project` (String): Name of the last loaded project.
    *   `KEY_THEME` (Boolean/Int): Theme preference.
    *   `KEY_LOG_VERBOSITY` (String): Filter level for logs.

## 3. Git Data (`JGit`)
Version control data is managed by the JGit library, which interacts directly with the `.git` directory within each project folder.
*   **Storage:** Standard Git object database (`.git/objects`, `.git/refs`).
*   **Concurrency:** `MainViewModel` (via `GitDelegate`) uses a `Mutex` to serialize Git operations.

## 4. Build Artifacts
The shipped APK is always built on GitHub Actions; PWAs need no build. The on-device toolchain that used to assemble the APK itself (`aapt2`, `d8`, `kotlinc`) and its caches were removed in Phase 0 and stay removed. `RemoteBuildManager` downloads the Actions artifact into `context.cacheDir` for installation, then deletes it.

A separate, narrower on-device compiler was reintroduced since, but only for a **local preview**, never the shipped artifact: for Android (Compose Multiplatform) projects, `WasmCompilerService` compiles `commonMain`/`wasmJsMain` sources with an embedded `kotlin-compiler-embeddable` (loaded by reflection from dex archives staged out of `assets/wasm-compiler/`, not currently bundled in this repo) into `.wasm`/`.js`/`index.html` under `context.filesDir/www`, which `WebProjectHost` mounts and hot-reloads on an `ACTION_WASM_COMPILE_SUCCESS` broadcast.

## 5. Reporting Deduplication
`GithubIssueReporter` uses a dedicated `SharedPreferences` file or keys to track reported error hashes.
*   **Mechanism:** Stores a hash of the stack trace + timestamp.
*   **Policy:** Prevents duplicate reports for the same error within 24 hours.

## 6. Static Data & Assets
*   **Templates:** `assets/templates/` (Copied to new project directories). Phase 1 will add a single opinionated PWA template.
*   **Workflows:** Managed programmatically by `ProjectConfigManager`. YAML content is hardcoded in the codebase to ensure integrity even if assets are missing.

(There is no longer an `assets/tools/` directory — the on-device APK-build toolchain was removed in Phase 0. `WasmCompilerService` (§4) separately expects an `assets/wasm-compiler/` directory for its local-preview-only compiler; that directory is not currently bundled, so preview compilation degrades to a clean error instead of crashing.)

## 7. Configuration Models (`.ideaz/`)
*   **Config:** `config.json` stores project-specific settings (e.g., detected package name, schema type).
*   **History:** `prompt_history.json` stores local prompt history for the project.
*   **Screenshots:** `screenshots/` directory stores captured context images.
