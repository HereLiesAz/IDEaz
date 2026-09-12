# Authentication & Security

## 1. Overview

IDEaz has no centralized user-account backend. Hosted provider APIs and GitHub use user-supplied credentials stored locally. The GitHub APK has one exception to the BYO-API-key rule: it may use a supported installed Gemini app after the user explicitly enables IDEaz's package-scoped accessibility bridge.

## 2. Credential storage

Every provider key/token plus signing passwords is stored through `AndroidKeystoreCredentialStore`: AES-GCM ciphertext in dedicated preferences protected by a non-exportable Android Keystore key. Legacy plaintext entries are migrated only after the secure write succeeds. Automatic backup/device transfer excludes credential stores; explicit settings export can include credentials only inside the user-password-protected `SecurityUtils` payload.

Failures are logged and surfaced through `SettingsViewModel.lastCredentialError`, because a handset running IDEaz often has no convenient adb/logcat surface.

### Credential slots

* `KEY_GITHUB_TOKEN` — GitHub API/JGit operations.
* `KEY_GOOGLE_API_KEY` — Gemini API / AI Studio credential.
* `KEY_API_KEY` — Jules.
* `KEY_GROQ_API_KEY`, `KEY_CEREBRAS_API_KEY`, `KEY_HF_API_KEY`, `KEY_MISTRAL_API_KEY`.
* `KEY_OPENAI_API_KEY`, `KEY_ANTHROPIC_API_KEY`, `KEY_DEEPSEEK_API_KEY`.
* `KEY_KEYSTORE_PASS`, `KEY_KEY_PASS` — Android signing secrets.

Non-secret preferences such as theme, model assignment, project paths and branch names remain in ordinary default `SharedPreferences`.

## 2.1 AI provider requirement vs API credential

`AiModel` deliberately distinguishes two concepts:

* `providerKey` — the credential slot an API transport uses.
* `requiredKey` — the setup-time requirement in the current distribution.

For every provider except Gemini they are the same. Gemini differs by distribution:

| Distribution | Gemini transport | Setup requirement |
|---|---|---|
| Google Play | Gemini API | `KEY_GOOGLE_API_KEY` required |
| GitHub APK | Installed Gemini app | no API key required when the bridge is used |
| GitHub APK with AI Studio key | Installed app primary, Gemini API fallback | key optional but used as fallback |

This distinction is important: making GitHub Gemini keyless must never accidentally make the Play API path keyless, and making setup keyless must not hide a saved AI Studio key from `AiAdapterFactory` when constructing the fallback client.

## 2.2 Installed Gemini bridge

The GitHub build's bridge is not an authentication token substitute smuggled into Play. It is a separate transport with a separate trust boundary:

* registered only by GitHub `debug`/`release` manifests;
* absent from the Play manifest;
* requires the user to enable IDEaz's accessibility service;
* accessibility events are restricted in XML to supported Gemini packages and additionally checked against the active bridge target;
* activates only while an IDEaz request is in flight;
* verifies that a candidate package actually resolves the explicit share intent before choosing it;
* serializes bridge requests process-wide because the consumer app exposes one foreground composer;
* shares a redacted, bounded project snapshot plus the IDEaz conversation and explicit current-turn attachments;
* returns any mutation through the same checkpoint/review/approval contract as API providers.

The bridge does not grant IDEaz a Google account credential, OAuth token, or API key.

## 2.3 Default model selection

An explicit AI Assignment wins. Otherwise `SettingsViewModel.defaultModelId()` ranks configured API providers by saved `providerKey`; when none is configured it falls back to Gemini. On Play that fallback still requires the Gemini API key at setup/use time. On GitHub it can be satisfied by the installed-app transport.

## 3. GitHub credential

The GitHub personal access token is used only for GitHub-backed actions such as private-repository git/API access and publishing. Local project creation, preview, editing and local commits do not require a GitHub account. Deploy/publish is where a missing token becomes relevant.

## 4. Android signing

A custom keystore is imported through SAF into app-private storage. Store/key passwords use the secure credential store; the alias is ordinary metadata. CI release signing uses repository secrets and environment variables documented in `build_pipeline.md`.

## 5. Security rules

* Never hardcode credentials.
* Never move a secure credential back into default preferences for convenience.
* Redact secrets from logs, repo snapshots and issue reports.
* Do not broaden package visibility or accessibility scope without a concrete target that requires it.
* Keep privileged external-AI services and permissions in GitHub-only manifests; Play must remain mechanically free of that surface.
* Preserve project-root containment when constructing any payload that can leave the device; `RepoSnapshot` rejects symlinks and canonical paths outside the project.
* Use `SecurityUtils` only for user-requested portable settings export, not routine credential persistence.

## 6. Social sign-on

Not implemented. If account sign-on is added later, it does not erase the distribution boundary above: Play provider access must continue to use supported provider mechanisms, while GitHub-only installed-app automation remains a separately disclosed feature.
