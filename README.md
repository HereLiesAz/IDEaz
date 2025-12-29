<h1 style="text-align:center"><b>[</b>oo<b>]</b> <br>IDEaz</h1>

This isn't no-code. This is not vibe coding. And this sure as hell ain't straight-up coding.      
This is what every emulator, visual preview, drag and drop WYSIWYG environment was leading up to.

### Development that feels like it's just you and your IDEaz -- The Post-Code IDE for Android.

**Philosophy:**
IDEaz adopts a "Post-Code" philosophy. The primary workflow is visual: Interact with your running app, select what you want to change, and prompt the AI to make it happen. The IDE handles the code generation, git operations, and build process in the background.

*   **Primary Workflow (Post-Code):** Run App -> Visual Select -> AI Prompt -> AI Edit -> Compile -> Run.
*   **Auxiliary Tools (Escape Hatches):** While the goal is to never touch code, we acknowledge reality. A full **File Explorer** and **Code Editor** are included for debugging, verification, or manual intervention when the AI gets stuck. These are tools, not the workspace.

**Architecture:**
IDEaz uses a **Hybrid Host** architecture.
*   **Host Mode (Primary):** The target app (Android or Web) runs *inside* the IDE window (using VirtualDisplay or WebView). The "Overlay" is a Composable layer drawn on top of this host.
*   **System Overlay (Legacy/Fallback):** The traditional System Alert Window overlay (`IdeazOverlayService`) is available for specific use cases or external app inspection but is secondary to the integrated Host experience.

**Key Features:**
*   **Repository-Based:** Every project is a Git repository.
*   **Local & Remote Builds:** "Race to Build" strategy uses local toolchain (`aapt2`, `d8`) and remote GitHub Actions simultaneously.
*   **AI Integrated:** Built-in AI agents (Jules/Gemini) drive development.
