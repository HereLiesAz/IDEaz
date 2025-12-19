# Miscellaneous Documentation

## 1. Project Templates
IDEaz includes templates in `assets/templates/` for:
*   **Android:** Basic "Hello World" with Gradle wrapper.
*   **Web:** `index.html`, `style.css`, `script.js`.
*   **React Native:** Minimal `App.js` and `app.json`.
*   **Flutter:** (Placeholder) Basic structure.

## 2. The "Jules-CLI" (Deprecated)
The project contains code for `JulesCliClient` which wraps a binary. This is currently **unreliable** on many Android devices due to `seccomp` filters and binary compatibility. The `JulesApiClient` (Retrofit) is the preferred method.

## 3. Logs
*   **Infrastructure Logs:** `[IDE]` tag.
*   **Build Logs:** `[BUILD]` tag.
*   **AI Logs:** `[AI]` tag.
*   **Git Logs:** `[GIT]` tag.

## 4. Updates
The app checks `https://api.github.com/repos/HereLiesAz/IDEaz/releases` for updates.
*   **Upgrade:** Remote version > Local version.
*   **Downgrade:** Remote version < Local version.
*   **Reinstall:** Versions match but hashes differ.
