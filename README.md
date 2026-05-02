<h1 style="text-align:center"><b>[</b>oo<b>]</b> <br>IDEaz</h1>

This isn't no-code. This is not vibe coding. And this sure as hell ain't straight-up coding.      
This is what every emulator, visual preview, drag and drop WYSIWYG environment was leading up to.

### Development that feels like it's just you and your IDEaz -- The Post-Code IDE for Android.

**Philosophy:**
IDEaz adopts a "Post-Code" philosophy. The primary workflow is visual: Interact with your running app, select what you want to change, and prompt the AI to make it happen. The IDE handles the code generation, git operations, and build process in the background.

*   **Primary Workflow (Post-Code):** Run App -> Visual Select -> AI Prompt -> AI Edit -> Compile -> Run.
*   **Auxiliary Tools (Escape Hatches):** While the goal is to never touch code, we acknowledge reality. A full **File Explorer** and **Code Editor** are included for debugging, verification, or manual intervention when the AI gets stuck. These are tools, not the workspace.

**Architecture:**
IDEaz targets two project shapes:
*   **PWA (Phase 1, daily driver):** the project renders in `WebProjectHost` (a WebView). Edits are conversational with **Gemini** (BYO-key, tool-use); reload is sub-second.
*   **Android app (Phase 2, heavy artillery):** the project is sideloaded onto the device. IDEaz observes via a System Alert Window overlay (`IdeazOverlayService`) and routes element-tap context to **Jules**, which opens PRs that auto-build via GitHub Actions.

**Key Features:**
*   **Repository-Based:** Every project is a GitHub repository. Git is the source of truth.
*   **Remote Builds:** Android builds happen on GitHub Actions. There is no on-device toolchain. PWAs need no build step at all.
*   **AI Integrated:** Phase 1 — Gemini (conversational). Phase 2 — Jules (agentic). Phase 3+ — pluggable adapters for Claude, OpenAI, etc.

See [`docs/plans/2026-05-01-ideaz-revival-design.md`](docs/plans/2026-05-01-ideaz-revival-design.md) for the active design.
