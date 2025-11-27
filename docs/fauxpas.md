# Faux Pas: Anti-Patterns and Mistakes to Avoid

## 1. Coding & Architecture
*   **Magic Strings:** Do not use hardcoded strings (e.g., `"filesDir/project"`). Use constants or `ProjectAnalyzer`.
*   **Main Thread Blocking:** Never perform disk I/O or network calls on the Main thread. Use `Dispatchers.IO`.
*   **Receiver Type Mismatch:** In Compose, be careful with `this` scope inside `apply` or `with` blocks, especially within `content` lambdas.
*   **Process Leaks:** Do not assume variables in `MainActivity` are available in `BuildService`. They are separate processes.
*   **Singleton Abuse:** Be cautious with Singletons that hold state, as the process might die and restart.

## 2. Build & Tools
*   **Gradle Reliance:** Do not try to run `./gradlew` *inside* the Android app. It won't work. Use `BuildService`.
*   **Exit commands:** Do not use `exit` in shell scripts intended for the `run_in_bash_session` tool if it blocks the session.
*   **Asset Modification:** Do not try to modify assets at runtime. They are read-only.
*   **Absolute Paths:** Do not hardcode `/data/user/0/...`. Use `context.filesDir`.

## 3. UI/UX
*   **Blocking UI:** Do not block the UI while waiting for a build. Show a progress indicator.
*   **System Permissions:** Do not assume you have permissions. Always check and request.
*   **Notification Channel:** Do not post notifications without creating a channel first (API 26+).

## 4. Git & Data
*   **Force Push:** Avoid `git push --force` on the main branch unless you are absolutely sure.
*   **Secret Exposure:** Do not commit `local.properties` or files containing keys.
*   **JGit Closing:** Always close `Git` instances to prevent file handle leaks (`Inflater has been closed` errors).

## 5. Agent Behavior
*   **Hallucination:** Do not invent file paths or library methods.
*   **Unverified Changes:** Do not mark a task as done without verification.
*   **Ignoring Errors:** Do not ignore "Command failed" messages. Investigate.
