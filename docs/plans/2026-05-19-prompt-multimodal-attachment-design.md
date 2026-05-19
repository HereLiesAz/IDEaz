# Prompt multimodal attachment (design)

Date: 2026-05-19
Status: Spec — awaiting implementation plan
Scope: medium-to-large. Refactors `ChatMessage` from `String content` to a part-list; each adapter learns its provider's multimodal wire format.

## Context

Companion to `2026-05-19-prompt-asset-attachment-design.md`. The asset spec covers "I'm adding this file to my project." This spec covers "look at this file so you understand what I want." The reference file goes *into the prompt*, not into the project tree.

Concrete use cases:
- "Make my page look like this image" (screenshot of a design)
- "Match this API response shape" (paste a JSON file)
- "Read this PDF and summarise it" (research doc — future, not in v1)
- "What's wrong with this error screenshot?" (debugging)

The four AI adapters wired today (Gemini, OpenAI-compatible, Gemini Nano, Gemini app bridge) have very different multimodal capabilities:

| Adapter | Image input | File input | Notes |
| :--- | :--- | :--- | :--- |
| Gemini (cloud) | ✅ | ✅ (some types) | `Part.inlineData(Blob)` with mime |
| OpenAI-compatible | ✅ for vision-capable models (GPT-4o, Llama-Vision, etc.) | ❌ (treat as text snippets) | `{type:"image_url", image_url:{url:"data:..."}}` |
| Gemini Nano (on-device) | ❌ | ❌ | Text-only API on `aicore` |
| Gemini app bridge | ✅ via `EXTRA_STREAM` on the share intent | ❌ | Image goes into the Gemini app; we still only get text back |

The `ChatMessage(role, content: String)` shape can't carry image bytes. Refactor is unavoidable.

## Goal

Same attach button users see for the asset path also lets them attach **as reference**. The user toggles between "Project asset" and "Reference for AI" on the chip itself. Reference attachments ride inline on the next AI turn and aren't written to the project tree.

## Non-goals

- Audio / video input (different per-provider plumbing, separate effort).
- Streaming attachments mid-conversation.
- PDF and document parsing — out for v1, even though Gemini supports PDFs natively. Add later.
- Reference attachments persisting across turns. Each attachment fires once, then is dropped.

## Design

### Core data shape

Refactor `ai/ConversationalAiClient.kt`:

```kotlin
sealed interface ChatPart {
    data class Text(val text: String) : ChatPart
    data class Image(val bytes: ByteArray, val mimeType: String) : ChatPart
    // Reserved for v1.1: data class FileBlob(...), data class Audio(...)
}

data class ChatMessage(
    val role: String,
    val parts: List<ChatPart>,
) {
    /** Convenience: collapse all Text parts. Used by Nano/bridge fallbacks. */
    val textContent: String get() = parts.filterIsInstance<ChatPart.Text>().joinToString("\n") { it.text }

    companion object {
        // Source-compat shim so existing call sites (hundreds of `ChatMessage("user", "...")`)
        // keep working. Drop once the codebase has fully migrated.
        operator fun invoke(role: String, content: String) =
            ChatMessage(role, listOf(ChatPart.Text(content)))
    }
}
```

The shim companion-invoke means **most existing call sites need no changes** — they keep passing strings.

### Per-adapter translation

**`GeminiAdapter`:**
- `toContent()` walks `parts`, building `Part.text(...)` and `Part.inlineData(Blob.fromBytes(bytes, mimeType))`.
- Drop the current `Part.builder().text(content)` single-part construction.

**`OpenAiCompatibleAdapter`:**
- If a message has *only* Text parts, send `content: "..."` as-is.
- If any part is Image, send `content` as an array: `[{type:"text", text}, {type:"image_url", image_url:{url:"data:<mime>;base64,<b64>"}}, ...]`.
- Falls back gracefully on non-vision models — the server returns "image input not supported," surfaced to the user.

**`GeminiNanoAdapter`:**
- Concatenates `textContent` from each message — drops images entirely.
- Logs once per chat: "Gemini Nano doesn't support images. Sent prompt text only."

**`GeminiAppBridgeAdapter`:**
- If there are no images, current `ACTION_SEND` + `EXTRA_TEXT` path runs unchanged.
- If exactly one image and no other non-text parts, switch the intent to `ACTION_SEND` with `type = mime`, write image to `cacheDir/<uuid>.<ext>`, set `EXTRA_STREAM = FileProvider.getUriForFile(...)`, set `FLAG_GRANT_READ_URI_PERMISSION`, also set `EXTRA_TEXT` to the user's text. The Gemini app shows the image and prompt; the accessibility-scrape still captures text response.
- If multiple images: send the text-only prompt with a note "[3 images attached but bridge supports one — first one sent.]" and forward the first. Document the limit.

**`IdeTools` / `dispatchIdeTool` / `IdeToolSchema`:** unchanged. Tools are still text-only.

### UI

Reuse `PromptInputAttachmentRow` from the asset spec. Each chip gets a small dropdown:
- **As asset** (default) → asset import path (existing).
- **As reference** → bytes are loaded into memory, attached as `ChatPart.Image` (for images) or as a `ChatPart.Text` with the file contents wrapped in a fenced code block (for text mime types).

Reference attachments are not written to disk. They live on the chip until submit, then attach to the next outgoing `ChatMessage` and clear.

### MIME handling

- `image/*` → `ChatPart.Image(bytes, mime)`.
- `text/*`, `application/json`, `application/xml`, `application/javascript`, `application/x-yaml` → load as UTF-8, embed as `ChatPart.Text("File `<name>`:\n```\n<content>\n```")`.
- `application/pdf` → for v1, refuse with "PDF reference not yet supported. Convert to image or paste text." (Gemini natively supports PDFs; revisit in v1.1.)
- Everything else (binary, unknown) → "This file type can't be sent as reference. Attach as asset instead?" with a button to switch.

### Token / size limits

- **Hard cap reference image size at 4 MB** post-decode. Bigger images get downscaled with `Bitmap.compress(JPEG, 85)` until under the cap. Avoids 413s on the cloud providers.
- **Hard cap text reference at 64 KB.** Bigger files refused with a clear message — the user should split or attach as asset and use `read_file` in the conversation.
- Vision capability check: each provider's `AiModel` registry entry gets `supportsImages: Boolean`. The chip's "As reference" toggle is disabled when the active provider doesn't support it (with a tooltip).

## Files touched

New:
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/ChatPart.kt` (or fold into `ConversationalAiClient.kt`).

Heavily modified:
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/ConversationalAiClient.kt` — new `ChatPart` sealed type, `ChatMessage` refactor with source-compat shim.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/GeminiAdapter.kt` — `toContent()` walks parts.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/OpenAiCompatibleAdapter.kt` — message-content array shape when images present.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/GeminiNanoAdapter.kt` — flatten to text, log notice.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/bridge/GeminiAppBridgeAdapter.kt` — image-via-stream branch, FileProvider entry.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/SettingsViewModel.kt` — add `supportsImages: Boolean` field to `AiModel`; update each registered model.

Minor:
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/widget/PromptInputAttachmentRow.kt` — dropdown on chip.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/PromptPopup.kt` and `ContextlessChatInput.kt` — collect reference parts at submit, attach to outgoing `ChatMessage`.
- `AndroidManifest.xml` — `FileProvider` entry for the bridge's outgoing image URIs (path config in `res/xml/file_paths.xml`).
- Tests: a unit test that round-trips an image through each adapter's part translation (using Robolectric or pure-jvm mocks for HTTP).

## Verification

1. Builds: `./gradlew :app:assembleDebug` then `./gradlew build` — both `BUILD SUCCESSFUL`.
2. **Source-compat sanity:** confirm no existing call site needed updating thanks to the companion-invoke shim. Grep for `ChatMessage(` and verify each still compiles.
3. **Gemini (cloud):** attach a PNG as reference, prompt "describe this image". Response should reference the image.
4. **OpenAI-compatible (Groq Llama 3.3 70B):** same prompt — should error cleanly because that model isn't vision-capable. Switch to a vision-capable Groq model (or OpenAI gpt-4o-mini via a user-entered key) and verify success.
5. **Nano:** attach an image. Submit. Verify the model returns text-only and the chat log shows "Gemini Nano doesn't support images" once.
6. **Bridge:** attach one image. Verify the Gemini app foregrounds with the image attached, accessibility-scrape returns its description text.
7. **Bridge with two images:** verify warning, first image forwarded, second dropped.
8. **Text reference:** attach a JSON file < 64 KB. Verify it embeds as a fenced code block in the prompt.
9. **Provider switch:** with an attached image, swap the active provider in Settings → reference-mode chips re-evaluate against the new model's `supportsImages`.

## Risks

- **`ChatMessage` refactor blast radius.** The companion-invoke shim makes most call sites source-compatible, but binary compatibility for any external module is broken. Mitigation: this is an app module, no external consumers; still, run the full build and the existing test suite, not just compile.
- **`ByteArray` in a data class.** Kotlin `data class` with `ByteArray` produces a `.equals()` that compares references, not contents. We don't compare ChatMessage equality in the tool-use loop (we append, not dedupe), but document it so future code doesn't assume value semantics.
- **OpenAI-compat schema divergence.** Some "OpenAI-compatible" providers (HF Inference at times, Mistral occasionally) don't accept the array-content shape. Mitigation: detect 4xx with "invalid content type" and re-send text-only with a notice.
- **Bridge `EXTRA_STREAM` permissions.** Requires a `FileProvider` entry; `READ_URI_PERMISSION` flag must be set. Forgetting either causes a silent share into the Gemini app. Mitigation: tested manually, write a Robolectric test that the intent has the flag.
- **Privacy of cached reference images.** They live in `cacheDir/` and stay until cache is cleared. Mitigation: clean up files we wrote ourselves immediately after the bridge's `chat()` returns (or times out).

## Open questions deferred

- Audio / video: PRD says "future." Out for v1.
- Multi-image grids: Gemini and GPT-4o both accept multiple images per turn; the wire format already handles it (parts list). We just need to lift the bridge's "one image only" cap. Trivial follow-up.
- Image *editing* (the user's image is modified by the AI and returned): different feature entirely, Imagen / Nano Banana / DALL·E territory.
