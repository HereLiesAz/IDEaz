# Flutter Implementation Investigation

## Constraints
*   **No Execution Permission**: The Android security sandbox (`noexec` mount option) prevents executing binary files from the application's private data directory (`filesDir`) or assets.
*   **Missing Toolchain**: The `dart` SDK and `flutter` engine binaries are not available on the device, and cannot be executed even if downloaded, due to the above constraint.
*   **Architecture**: Building Flutter apps requires compiling Dart code to native machine code (AOT for Release) or Kernel Snapshots (for Debug JIT). This requires the `dart` executable.

## Investigation Results

### 1. Local Dart Execution
*   **Status**: Failed / Not Feasible
*   **Reason**: Cannot execute `dart` binary.
*   **Workaround**: None within standard Android app permissions. Termux achieves this by installing a bootstrap environment, which is outside the scope of IDEaz's current architecture.

### 2. Universal Flutter Runner
*   **Status**: Not Feasible
*   **Reason**: Requires a custom Flutter engine capable of loading dynamic snapshots. While theoretically possible (similar to React Native's JS bundle loading), it requires a specialized runner app, not a generic "build and run" flow for arbitrary projects.

### 3. Remote Build Strategy (Selected)
*   **Status**: Implemented
*   **Mechanism**:
    *   Push code to GitHub.
    *   Trigger GitHub Actions (CI) via `android_ci_flutter.yml`.
    *   Cloud runner executes `flutter build apk`.
    *   IDEaz polls for the artifact and installs it.
*   **Verdict**: This is the only viable path for full Flutter support on this platform.

## Conclusion
The "Remote Build" strategy is the official supported method for Flutter development in IDEaz. Local compilation and execution are blocked by OS security policies.
