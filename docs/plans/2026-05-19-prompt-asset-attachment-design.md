# Prompt asset attachment (design)

Date: 2026-05-19
Status: Spec — awaiting implementation plan
Scope: small, no AI-protocol changes. Adds "attach file as project asset" to both prompt inputs.

## Context

Today the user can only send text to the AI. Real prompts often need files: "add this logo," "use this CSV as the data source," "include this font." There's no plumbing.

There are two distinct user intents — this spec covers the simpler one. The other ("attach as reference for the AI to look at") needs multimodal message support and is in `2026-05-19-prompt-multimodal-attachment-design.md`.

**Asset attachment** is purely a project-files operation:
1. User picks a file.
2. App copies it into the project directory at a sensible relative path.
3. Prompt text is auto-annotated so the AI knows the file is now available (e.g. "I added `assets/logo.png`. Use it as the page header.").
4. `ProjectFileObserver` (already wired at `MainViewModel.kt:321`) auto-fires WebView reload on the write, so PWA previews update immediately.

Zero changes to `ChatMessage`, zero changes to any AI adapter, zero new dependencies. Works across all six providers today.

## Goal

A paperclip button on the contextual prompt popup and on the chat-tab input. Tapping it opens the system file picker; the chosen file lands in the project dir; the prompt is annotated; the AI sees the file when it reads the project tree.

## Non-goals

- Cloud-stored files (Drive, Photos) beyond what SAF surfaces by default — SAF handles those transparently anyway.
- Inline multimodal — separate spec.
- Editing files after attach (the user can use the AI or the file explorer).
- Streaming uploads or progress bars — copies are local-only and effectively instant.

## Design

### Components

1. **`ProjectAssetImporter`** (new) at `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAssetImporter.kt`:
   - `suspend fun import(context: Context, projectDir: File, projectType: ProjectType, sourceUri: Uri): ImportResult`
   - Resolves a friendly filename via `OpenableColumns.DISPLAY_NAME`; falls back to `Uri.lastPathSegment`.
   - Picks a destination subdir by `ProjectType`:
     - WEB/PWA → `<projectDir>/assets/` (so HTML can reference `assets/<name>`)
     - ANDROID → `<projectDir>/app/src/main/res/raw/` for binary, `<projectDir>/app/src/main/assets/` for everything else.
     - UNKNOWN → `<projectDir>/`
   - Resolves filename collisions with `(2)`, `(3)`, etc. (don't overwrite without consent).
   - Sanitises filename: drops path separators, trims whitespace, no leading dot.
   - Returns `ImportResult(relativePath, displayName, sizeBytes)`.

2. **`AttachedAsset`** data class (in the same file or a sibling models file): `{ relativePath: String, displayName: String, sizeBytes: Long }`. State in the prompt composables.

3. **`PromptInputAttachmentRow`** (new shared composable) at `app/src/main/kotlin/com/hereliesaz/ideaz/ui/widget/PromptInputAttachmentRow.kt`:
   - Renders the attach button + a horizontal list of attached-asset chips.
   - Each chip shows filename + size, with an X to remove.
   - Removing a chip *also* deletes the file from disk (within current prompt session — the user changed their mind before submit).

### Wiring

**`PromptPopup` (overlay):**
- Add `attachedAssets: List<AttachedAsset>` state, hoisted into the popup.
- Insert `PromptInputAttachmentRow` between the text field and the submit button.
- On submit, build a final prompt string: `originalPrompt + "\n\nAttached files:\n" + assets.joinToString("\n") { "- " + it.relativePath }`. Send via existing `onSubmit(String)` — no new callback.
- Clear state on dismiss.

**`ContextlessChatInput` (chat tab):**
- Same pattern. State lifted into `IdeBottomSheet`'s `ExpandedContent` (since the chat tab is shared across detents).
- Final message text built the same way before calling `onSend`.

### File picker

`androidx.activity.compose.rememberLauncherForActivityResult` with `ActivityResultContracts.GetContent()` and MIME type `"*/*"`. SAF handles cloud/local/Drive uniformly with zero runtime permissions.

### Concurrency

Asset copy runs on `Dispatchers.IO` inside a `viewModelScope.launch` so the UI stays responsive. Large files (videos, big PSDs) could take a beat — the UI shows the chip in a loading state until import completes, then enables the submit button.

## Files touched

New:
- `app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProjectAssetImporter.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/widget/PromptInputAttachmentRow.kt`

Modified:
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/PromptPopup.kt` — add attachment row + state.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/ContextlessChatInput.kt` — add attachment row.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/IdeBottomSheet.kt` — hoist asset state if attaching from chat tab; pass through to `ContextlessChatInput`.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt` — expose `importAsset(uri, onResult)` that resolves the active project dir + type, calls `ProjectAssetImporter`, surfaces errors to `stateDelegate.appendBuildLog`.

No manifest changes (SAF doesn't need permissions). No new dependencies.

## Prompt annotation format

Append, after a blank line, exactly:

```
Attached files:
- assets/logo.png
- assets/data.csv
```

The AI can `read_file` any of them via `IdeTools`. Keep the format dead simple so all six providers parse it equivalently.

## Verification

1. `./gradlew :app:assembleDebug` and `./gradlew build` — both `BUILD SUCCESSFUL`.
2. **PWA flow:** open a PWA project, expand the bottom sheet → chat tab → attach button → pick a PNG. Chip appears showing filename + size. Type "use this as a header image". Submit. Confirm: file lands in `<projectDir>/assets/<name>.png`, prompt sent to AI contains the annotation line, WebView reloads automatically (file observer), AI responds and references the asset.
3. **Overlay flow:** same checks from the contextual prompt popup.
4. **Collision:** attach two files with the same source name. Second one lands as `name (2).ext`.
5. **Remove chip:** attach, then tap X. File deleted from disk, chip removed.
6. **Android project:** attach a binary (PNG) → lands in `res/raw/`. Attach a CSV → lands in `assets/`. Submit → AI sees both.
7. **Large file:** attach a ~20 MB video. UI doesn't block; chip shows loading; submit disabled until import completes.

## Risks

- **Asset placement guess for Android.** `res/raw/` rejects filenames with hyphens/spaces/uppercase. Mitigation: sanitise to `[a-z0-9_]+` for that destination; fall back to `assets/` when sanitisation would lose information.
- **Filename collisions across sessions.** Re-attaching the same file picks `(2)` rather than overwriting. That's safe but can clutter `assets/`. Mitigation: show a snackbar "Replaced foo.png (existing renamed to foo (1).png)" if the user prefers — out of scope here, default keeps both.
- **SAF returns a content URI for Google Drive / cloud sources.** `contentResolver.openInputStream` handles those transparently, but the read can fail if offline. Mitigation: catch + surface an error to the build log; no retry.
- **Sensitive files.** Nothing prevents the user from attaching their `.ssh/id_rsa`. We're not a sandbox; that's their call. Mitigation: none beyond the existing trust model.

## Open questions deferred

- Should attached assets persist across prompts (the file is in `projectDir` forever) or be flagged "remove after this turn"? Default: persist (they're real project files now).
- Should the AI's tool list include a new `mark_asset` tool to register the file as a managed asset? Default: no — keeps the surface area minimal.
