# AzNavRail v9 alignment + invisible-filler audit (design)

Date: 2026-05-18
Status: Spec — awaiting implementation plan
Scope: bundle A+B from the 2026-05-18 brainstorm

## Context

Two parallel cleanups land in one spec because they share files and verification:

1. **AzNavRail is not the sole layout authority it should be.** Several screens stack redundant `Box(Modifier.fillMaxSize())` wrappers and apply manual safe-zone padding (notably the `RAIL_TITLE_CLEARANCE = 80.dp` constant in `MainScreen.kt:45`) that compensates for the rail's own title rendering. `onscreen { }` already handles safe-zone padding; the manual clearances are dead weight that drift across screens and silently break when the rail changes its title metrics.
2. **`IdeNavRail.kt:23` uses `azSettings(...)`**, the v9 monolithic config call. The current guide (`docs/AZNAVRAIL_COMPLETE_GUIDE.md` §2) recommends the split form (`azConfig` / `azTheme` / `azAdvanced`). Both APIs exist in v9 (verified by decompiling `AzNavRailScope.class` in `AzNavRail-9.0.aar`), but the split form is the documented path and is the only one that exposes the help overlay + tutorials parameters as separate concerns. The app has no help overlay wired today.

The bottom-sheet DSL migration that motivated this brainstorm is already landed; this spec is the followup cleanup.

## Goal

Make AzNavRail the sole top-level layout authority for IDEaz. Remove all space-filling Boxes that exist only to wrap content. Eliminate hardcoded clearances. Migrate `IdeNavRail.kt` to the documented three-call configuration form and wire a help overlay.

## Non-goals

- Tutorials framework wiring (`tutorials = mapOf(...)`) — deferred to its own spec.
- Drag-and-drop relocatable rail items.
- Nested rails / FAB mode redesign.
- Any AI provider, build cycle, or PWA work — those are separate specs from the same brainstorm (C/D/E/F).

## Verified library facts (from `AzNavRail-9.0.aar`)

`AzNavRailScope` exposes both:

- `azSettings(packRailButtons, …, headerIconShape, …)` — monolithic, currently used.
- `azConfig(dockingSide, packButtons, noMenu, usePhysicalDocking, …)`
- `azTheme(activeColor, defaultShape, headerIconShape, translucentBackground, surfaceColors)`
- `azAdvanced(isLoading, enableRailDragging, onUndock, …, onOverlayDrag, …, onItemGloballyPositioned, …, helpList, tutorials)`

Exact parameter names are recovered from named-argument call sites in the guide. Signature compatibility must be checked at first compile — if a name has drifted between guide and AAR, fall back to positional or rename to the AAR name. This is the only known risk for the API migration.

## Design

### Part 1 — Invisible-filler audit (full pass)

**The rule** to document and enforce:

> A `fillMaxSize()` (or `fillMaxHeight()` / `fillMaxWidth()`) container is justified only when (a) it draws — a background, scrim, or border, (b) it is a hit-test surface — a gesture detector that actually consumes pointer events, or (c) it is the direct child of `onscreen { }` / `background { }` and needs to fill its slot. Any other use is removed.

**Concrete removals:**

- `MainScreen.kt:45` — delete `val RAIL_TITLE_CLEARANCE = 80.dp` and its doc comment.
- `MainScreen.kt:110-113` — collapse the outer `Box(Modifier.fillMaxSize())` that wraps the inner `Box(Modifier.fillMaxSize())`. The inner one stays only if it actually owns z-indexed children; otherwise inline the children into the `onscreen { }` block.
- `GitScreen.kt:100,103` — drop the manual `padding(16.dp)` if it's only there to clear the rail title; drop the `Spacer(height = RAIL_TITLE_CLEARANCE)`.
- `FileExplorerScreen.kt:73-74` — empty-state Box is structural-only; merge into the screen's root container.
- Every other screen importing `RAIL_TITLE_CLEARANCE` — remove the import and the spacer.
- `ContextualChatOverlay.kt:49` — keep. Has zIndex / overlay role.
- `SelectionOverlay.kt:30` — keep. Owns drag-gesture pointerInput.

**Per-screen audit checklist** (each screen must be opened and verified, not just grep'd):

- `ProjectScreen.kt`
- `SettingsScreen.kt`
- `GitScreen.kt`
- `FileExplorerScreen.kt`
- `FileContentScreen.kt`
- `ContextualChatOverlay.kt`
- `SelectionOverlay.kt`
- `PromptPopup.kt`
- `IdeBottomSheet.kt` (its outer Box at line 94 draws a background — keep)
- `WebProjectHost` and any IDE host placeholder composables

For each, answer: does the outermost container draw, hit-test, or fill an AzNavRail slot? If none of the three, remove it and trust `onscreen { }`.

### Part 2 — AzNavRail v9 API migration

Replace the single `azSettings(...)` call in `IdeNavRail.kt:23-35` with three calls:

```kotlin
azConfig(
    packButtons = true,
    dockingSide = AzDockingSide.LEFT,
    noMenu = false,
    usePhysicalDocking = false,
)

azTheme(
    defaultShape = AzButtonShape.RECTANGLE,
    headerIconShape = AzHeaderIconShape.NONE,
    activeColor = MaterialTheme.colorScheme.primary,
    translucentBackground = Color.Black.copy(alpha = 0.5f),
)

azAdvanced(
    isLoading = false,
    enableRailDragging = enableRailDraggingOverride ?: false,
    onUndock = onUndock,
    onOverlayDrag = onOverlayDrag,
    helpEnabled = showHelp,
    helpList = ideHelpList,
    onDismissHelp = onDismissHelp,
)
```

Exact parameter names verified at first compile.

### Part 3 — Help overlay wiring

- New state in `MainScreen.kt`: `var showHelp by remember { mutableStateOf(false) }`.
- Pass `showHelp` and `onDismissHelp = { showHelp = false }` into `ideNavRail(...)`.
- In `IdeNavRail.kt`, add a `azHelpRailItem(id = "help", text = "Help")` near the bottom of the rail (after Settings). It opens the overlay.
- Build `ideHelpList: Map<String, String>` inside `IdeNavRail.kt` keyed by rail item id:

| id                  | help text (one short sentence)                           |
| ------------------- | -------------------------------------------------------- |
| `project_settings`  | Open the project's settings and switches.                |
| `git`               | Git status, history, and remote actions.                 |
| `main`              | IDEaz actions: prompt, build, deploy, mode.              |
| `prompt`            | Open the prompt input to instruct the AI.                |
| `build`             | Trigger a build and open the console.                    |
| `reload`            | Soft-reload the PWA preview.                             |
| `hard_reload`       | Cache-bypassing reload of the PWA preview.               |
| `deploy`            | Push the current PWA to its remote host.                 |
| `mode_toggle`       | Switch between Interact and Select on the live preview.  |
| `file_explorer`     | Browse and open files in the project.                    |
| `settings`          | App-wide settings: theme, API keys, providers.           |

### Part 4 — Documentation

Add a new section to `docs/UI_UX.md` (or `CLAUDE.md` if `UI_UX.md` is reference-only):

> **Layout rule.** AzNavRail owns top-level layout. Inside `onscreen { }`, do not pad for the rail title or the system bars — AzNavRail handles that. A `fillMaxSize()` container is justified only when it draws, hit-tests, or is the direct child of an AzNavRail slot. Anything else is redundant.

## Files touched

- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeNavRail.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/GitScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/FileExplorerScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/ProjectScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/SettingsScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/FileContentScreen.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/PromptPopup.kt`
- Any other file importing `RAIL_TITLE_CLEARANCE` (full grep at implementation time)
- `docs/UI_UX.md` or `CLAUDE.md` — new layout rule

## Verification

Per `AGENTS.md` and memory `feedback_verify_full_build.md`, a full build is required (compileDebugKotlin alone misses lint, signing, tests, packaging).

1. `./gradlew :app:assembleDebug` — `BUILD SUCCESSFUL` (grep for the string; pipe exit codes are misleading per `feedback_exit_code_piping.md`).
2. `./gradlew build` — `BUILD SUCCESSFUL`.
3. Install debug APK and exercise each screen:
   - **Project screen:** title visible, no clipping at the top, no excess gap.
   - **Git screen:** same. Filter the log — visible. Action buttons reachable.
   - **File Explorer:** list scrolls from the correct top edge; empty state centered without an outer Box.
   - **Settings:** all sections visible; theme toggle works; rotate to landscape — no rail-vs-content overlap.
   - **IDE / Web host:** WebView fills its slot edge-to-edge between rail and bottom-sheet strip.
   - **Selection overlay:** drag-to-select still hits.
   - **Contextual chat overlay:** appears above content, still dismissable.
   - **Bottom sheet:** unchanged behavior — PEEK initial, drag through all detents, HIDDEN strip touch-targetable.
4. Tap the new **Help** rail item: overlay opens; each rail item shows its one-line entry; tapping a card expands it; `Dismiss` (or scrim tap) closes the overlay.
5. Rotate device on every screen: no spacer-induced jumping; rail and content reflow via AzNavRail's own logic.

## Risks

- **Title collision:** without `RAIL_TITLE_CLEARANCE`, content may collide with the rail's auto-rendered screen title on screens that drew from y=0. Mitigation: if any screen genuinely collides, that is an AzNavRail bug to file upstream — do not re-add a global constant. Local fix in the offending screen only.
- **`azConfig`/`azTheme`/`azAdvanced` named-arg drift:** parameter names recovered from the guide may not match the v9 AAR exactly. Mitigation: first-compile verification; rename at call site. Both APIs coexist, so if migration stalls, fall back to `azSettings` for that one screen and file the discrepancy.
- **Help overlay UX:** v9's help overlay renders entries for *all* rail items by default; supplying `helpList` only adds extras. Verify the default coverage before deciding which keys to populate — over-populating creates noise.

## Open questions deferred to implementation

- Should `helpEnabled` be a setting (persisted) or transient (resets on app restart)? Default to transient; promote later if requested.
- Should the help trigger be `azHelpRailItem` (visible button) or `azHelpSubItem` under the IDEaz host (tucked away)? Default to visible — discoverability over rail real estate.
