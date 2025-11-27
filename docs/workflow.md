# Developer Workflow

## 1. Getting Started
1.  **Clone:** Clone the `HereLiesAz/IDEaz` repository.
2.  **Setup:** Run `./setup_env.sh` to install JDK and Android SDK.
3.  **Open:** Open in Android Studio or your preferred editor.

## 2. Making Changes
1.  **Branch:** Create a new branch for your feature/fix.
2.  **Code:** Implement changes.
3.  **Test:** Run unit tests locally (`./gradlew testDebugUnitTest`).
4.  **Verify:** Run a full build (`./gradlew build`).
5.  **Docs:** Update `TODO.md` and other docs.

## 3. Pull Requests
1.  **Push:** Push your branch.
2.  **PR:** Open a Pull Request.
3.  **Review:** Wait for code review.
4.  **Merge:** Squash and merge.

## 4. Release Strategy
*   **Tags:** Releases are triggered by pushing a tag (e.g., `v1.0.0`).
*   **CI:** The `release.yml` workflow builds the APK and publishes it to GitHub Releases.
*   **Debug Release:** The `android_ci_jules.yml` updates a `latest-debug` release on every push to `main`.

## 5. Working with Agents (Jules)
*   **Instructions:** Provide clear, atomic instructions.
*   **Verification:** Always verify the agent's work.
*   **Feedback:** If the agent fails, explain why and provide context.
