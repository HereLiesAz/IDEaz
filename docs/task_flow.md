# Task Flow

## 1. The "Edit-Build-Test" Loop
1.  **Edit:** User prompts AI -> Patch applied to `src/`.
2.  **Build:**
    *   `BuildService` creates `build/generated`.
    *   Compiles Resources (`aapt2`).
    *   Compiles Kotlin (`kotlinc`).
    *   Dexes (`d8`).
    *   Packages (`ApkBuilder`).
    *   Signs (`ApkSigner`).
3.  **Deploy:** `ApkInstaller` installs the APK.
4.  **Launch:** App restarts.

## 2. The "Contextual Chat" Flow
1.  **Selection:** User drags rect on Overlay.
2.  **Capture:** `UIInspectionService` captures coordinates.
3.  **Prompt:** User types "What is this?".
4.  **Enrichment:** `AIDelegate` captures:
    *   Screenshot (cropped).
    *   View Hierarchy (from AccessibilityNodeInfo).
5.  **Send:** Request sent to Gemini/Jules.
6.  **Response:** Answer displayed in Overlay bubble.

## 3. The "Sync" Flow
1.  **Check:** `GitManager.status()`.
2.  **Commit:** `GitManager.add()` -> `GitManager.commit()`.
3.  **Pull:** `GitManager.pull()` (Rebase strategy).
4.  **Push:** `GitManager.push()`.
5.  **Conflict:** If conflict, abort and notify user (Auto-resolution is risky).
