# IDEaz: UI/UX Design System

## Overview
IDEaz is a **visual, post-code creation engine**. The user acts as a director, interacting with their live app, while the AI acts as the engineer.

## The Core Interaction Model
The interaction model revolves around the **Live App** and the **IDE Overlay**.

1.  **The Live App:** The user's running application.
2.  **The IDE Overlay (`IdeazOverlayService`):** A system alert window service that hosts the IDE UI (Navigation Rail, Bottom Sheet, Settings) and intercepts touches for selection.
3.  **The Global Console (`IdeBottomSheet`):** A persistent bottom sheet in the host app for global logs and contextless chat.

## Modes of Operation

### 1. Interaction Mode (Dynamic Overlay)
*   **Purpose:** Allow the user to use their app normally.
*   **State:** `IdeazOverlayService` window (for the Rail) is **dynamic** (managed by `AzNavRail`). It automatically shrinks to wrap the content (Rail) when stationary to unblock the underlying app, and expands to full screen during drag operations to ensure smooth movement.
*   **Visuals:** The user sees their app cleanly with the Rail docked. The overlay is effectively transparent/minimized where not needed.
*   **Trigger:** User taps "Interact" or toggles the mode.

### 2. Selection Mode
*   **Purpose:** Allow the user to select UI elements to modify.
*   **State:** `IdeazOverlayService` window is full screen (`MATCH_PARENT`) and intercepts touches.
*   **Visuals:**
    *   **Drag-to-Select:** Dragging draws a selection rectangle.
    *   **Prompt:** A floating input box appears near the selection.
*   **Trigger:** User taps "Select".

### 3. Overlay Mode (Settings/Project)
*   **Purpose:** Configure the project or IDE settings.
*   **State:** `IdeazOverlayService` window is full screen and opaque.
*   **Visuals:** Settings or Project screens are visible.

## Key Interface Elements

### 1. The Unified Overlay
*   **Concept:** The IDE runs entirely within a System Alert Window (`TYPE_APPLICATION_OVERLAY`).
*   **Compliance:** The overlay service extends `AzNavRailOverlayService`, leveraging the library's built-in `WindowManager` handling for dynamic sizing and drag support.
*   **Transparency Rules:**
    *   **Interact/Select Mode:** The background MUST be transparent.
    *   **Settings/Setup:** The background MUST be **Opaque** (Solid).
*   **Attachment:** The overlay is persistent.

### 2. The Pull-Up Bottom Card (Console)
A versatile bottom sheet that provides visibility into the background processes.
*   **Tabs:** Build, Git, AI, All.
*   **Content:** Log output from respective sources.
*   **Empty State:** Displays context-aware messages (e.g., "No build logs yet") when no logs are available for the selected tab.
*   **Theming:** High contrast (Dark Grey/White in Dark Mode, Light Grey/Black in Light Mode).

### 3. The Update Popup
*   **Trigger:** Appears when a background build (Local or Remote) completes successfully while the user is interacting.
*   **Message:** "Updating, gimme a sec."
*   **Behavior:**
    *   **Clipboard:** Any text currently entered in a prompt input box **MUST** be automatically copied to the system clipboard.
    *   **Dismissal:** Disappears automatically when the new version of the app loads.

### 4. Persistent Notification
*   **Purpose:** Keeps the IDE "alive" and informative.
*   **Content:** Displays the **three most recent lines** of log output (collapsed) and **10+ lines** (expanded).
*   **Reliability:** Ensures the `IdeazOverlayService` is treated as a foreground service.

## Visual Language & Theming (`ui/theme`)
*   **Theme:** Material 3 Dark Mode (Default). High contrast.
*   **Palette:**
    *   **Background:** `#1E1E1E` (Dark Grey).
    *   **Surface:** `#2D2D2D`.
    *   **Primary:** `#BB86FC` (Purple).
    *   **Error:** `#CF6679`.
*   **Typography:** Monospace for code/logs. Sans-serif for UI (Standard Material 3 Expressive).
*   **Dark/Light Mode:** User-toggleable or System follow.

## Common UI Patterns
*   **`AzButton`:** Standard button.
*   **`AzTextBox`:** Standard input field.
*   **`LiveOutputBottomCard`:** Floating status card above the bottom sheet.

## Accessibility
*   **Content Descriptions:** All icons and images must have content descriptions.
*   **Touch Targets:** Minimum 48dp touch targets. Group related elements (e.g. Label + Switch) into a single toggleable row.
*   **Semantics:** Use `heading()` semantics for section titles.

## Layout Rule: AzNavRail Owns Top-Level Layout

AzNavRail is the sole top-level layout authority. Inside `onscreen { }` / `background { }` slots, do **not** add padding to clear the rail title, the rail strip, or the system bars — AzNavRail handles all of that.

A `fillMaxSize()` (or `fillMaxHeight()` / `fillMaxWidth()`) container is justified only when:

1.  **It draws** — a background, scrim, border, or other visible paint.
2.  **It hit-tests** — a gesture detector that actually consumes pointer events (`pointerInput`, `combinedClickable`, etc.).
3.  **It is the direct child of an AzNavRail slot** — `onscreen { }`, `background { }`, or `azBottomSheet { }` — and needs to fill that slot.
4.  **It owns alignment** — `Box(contentAlignment = ...)` or similar where the size is required for the alignment to be meaningful.

Anything else is redundant. Do not introduce a global "rail title clearance" constant — if a screen's content collides with the rail title, that is an AzNavRail bug to file upstream, not a per-screen spacer to add.

Bottom sheets mount via the `azBottomSheet` DSL on `AzNavHostScope`, never wrapped in `onscreen { }` — that gives the sheet the documented `zIndex(2f)` placement and the touch-targetable HIDDEN-detent strip at the screen bottom edge. See `docs/AZNAVRAIL_COMPLETE_GUIDE.md` §10.2.

## Production Interaction Contract

Every user-initiated operation must expose prerequisite validation and consequence before execution; named progress and cancellation policy during execution; a durable receipt on success; retained input, retry, and sanitized details on failure; and reconciliation after rotation, process death, connectivity loss, or upgrade. Accessibility and least-privilege disclosure apply at every stage. See [`ux_userflow_audit.md`](ux_userflow_audit.md) for the full flow inventory and release gates.

On-device failure cards remain outside model history. Cloud fallback must name the
destination and transmitted context beside a one-shot approval control; displaying a
failure never authorizes transmission.

A local model may request one read-only Gemini consultation before using any other
tool. The consent card shows the exact question/context and byte count; it names the
destination and states which data is excluded. Merely displaying the card performs no
network request. **Send once** transmits exactly that preview; **Keep local** resumes
without transmission. Cloud advice is untrusted text returned only to the local loop,
and Gemini receives no repository map, history, attachments, credentials, or IDE tools.

On-device file mutations stop at a review card before the WebView reloads. The card
lists every changed path and offers **Approve & reload** or **Reject**; an approved
edit retains its out-of-tree checkpoint as an **Undo edit** action until later project work
makes that rollback unsafe.
Validation failures replace approval with a recheck action. Recovery from a crash
during a write permanently disables automatic rollback for that checkpoint; later
validation cannot resurrect a destructive button merely by changing its clothes.

### Current release scope

Only PWA projects are selectable, matching the revival design's Phase 1 daily-driver scope. Other recognized repositories may be detected so their metadata is not rewritten, but their creation, initialization, and App View mounting remain disabled until their target loops pass end-to-end verification.
