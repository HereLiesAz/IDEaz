# **The Remote Build Pipeline**

IDEaz eliminates the complexity of local compilation by offloading the entire build process to GitHub Actions. This ensures a consistent, reproducible build environment and removes the need for bundling heavy build tools within the IDE app itself.

## **1. Architecture**

The build pipeline consists of three main components:

1.  **IDE Client (Android):** Triggers builds and consumes the final artifact.
2.  **GitHub Actions (Remote):** Executes the standard Gradle build process.
3.  **GitHub Releases (Storage):** Stores the compiled APK as an asset.

## **2. The Build Sequence**

### **Step 1: Trigger**
When a change is made (either by the user via AI prompts or a manual sync), the IDE Client commits the changes and pushes them to the GitHub repository.
*   If a build is explicitly requested, the IDE triggers a `workflow_dispatch` event for the `android_ci_jules.yml` workflow.
*   Alternatively, the workflow may be configured to run automatically on `push` to the main branch.

### **Step 2: Remote Execution**
The GitHub Action runner picks up the job.
1.  **Checkout:** Clones the repository.
2.  **Setup:** Sets up Java and the Android SDK.
3.  **Build:** Runs `./gradlew assembleDebug` to compile the app.
4.  **Sign:** Signs the APK with a debug keystore.
5.  **Release:**
    *   Creates or updates a GitHub Release tag (e.g., `latest-debug`).
    *   Uploads the signed APK as an asset to this release.

### **Step 3: Polling & Download**
The IDE Client polls the GitHub API for the status of the workflow run.
*   **Status Check:** It checks `GET /repos/{owner}/{repo}/actions/runs/{run_id}`.
*   **Artifact Discovery:** Once the run succeeds, it looks for the release asset in `GET /repos/{owner}/{repo}/releases/tags/latest-debug`.

### **Step 4: Installation**
1.  **Download:** The IDE downloads the APK file from the GitHub Release asset URL.
2.  **Install:** The `BuildService` (now strictly a Download & Install service) uses the Android `PackageInstaller` API to install the APK.
3.  **Launch:** Upon successful installation (`ACTION_PACKAGE_REPLACED`), the IDE automatically launches the target application.

## **3. Dependency Management**
Since the build happens remotely using standard Gradle, there is **no need for custom on-device dependency resolution**.
*   **Standard Gradle:** The project uses standard `build.gradle.kts` and `libs.versions.toml` files.
*   **No Bundled Libs:** The IDE does not need to ship with any AndroidX or Material libraries.

## **4. Error Handling**
*   **Build Failures:** If the remote Gradle build fails, the workflow logs are retrieved via the GitHub API (`GET /repos/{owner}/{repo}/actions/runs/{run_id}/logs`).
*   **Log Analysis:** These logs are parsed and sent to the AI (Jules) to diagnose and fix the issue automatically.
