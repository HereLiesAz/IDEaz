# Multi-provider AI with free-tier routing (design)

Date: 2026-05-18
Status: Spec — awaiting implementation plan
Scope: brainstorm items **D** (more provider options, skip the Gemini-app intent bridge) + **E** (free-tier hosted providers)

## Context

Today the app supports two AI backends: Gemini (via the `google-genai` SDK, OpenAI-style tool-use loop, used by both the chat tab and the contextual prompt path) and Jules (REST, stateful sessions, GitHub-anchored, Android-only after the PWA-fast-path spec lands). Both need API keys the user must obtain, and Jules is gated behind a Google Cloud Project.

The user wants the app to be usable **for free as much as possible**. Three avenues:

1. **On-device** — Gemini Nano via `com.google.ai.edge.aicore` (free, no network, available on supported Pixel/Samsung devices). Capability-limited but ideal for short prompts and code edits.
2. **Free-tier hosted** — Groq, Cerebras, Hugging Face Inference, Mistral La Plateforme. All speak OpenAI-compatible JSON. User enters one key per provider; quotas are generous on the free tier.
3. **Bring-your-own-key** — current Gemini API path stays. Anthropic/OpenAI/etc. can be added as the same OpenAI-compatible adapter with a different base URL.

The Gemini-app intent bridge (clipboard-handoff or Accessibility Service) was considered and rejected during brainstorm: there is no public IPC contract that round-trips a Gemini response, so any bridge is either invasive (Accessibility Service) or jarring (manual copy/paste). Skipping it entirely.

Verified state of play:

- `ConversationalAiClient` interface **already exists** in the codebase. `GeminiAdapter : ConversationalAiClient` (lines 18-21 of `GeminiAdapter.kt`). The abstraction shape is already there.
- `AiModels` registry (`SettingsViewModel.kt:27-46`) is a `data class AiModel(id, displayName, requiredKey)` with a `findById()` lookup. Provider plumbing is data-driven — adding a provider is "add a constant, a `requiredKey`, and an entry to `availableModels`."
- Routing: `AIDelegate.startContextualAITask()` line 206 dispatches on `model.id` via a `when`. The chat-tab path (`MainViewModel.sendChatMessage()` line 179) hardcodes Gemini — needs to learn the same dispatcher.
- Tool-use schema: `GeminiAdapter` uses Gemini's `FunctionDeclaration` / `Schema` builders for the four tools (`read_file`, `write_file`, `list_files`, `apply_patch`). The schema is **not** provider-agnostic today.
- Keys: stored as plaintext `SharedPreferences` strings via `SettingsViewModel`. `KEY_GOOGLE_API_KEY`, `KEY_API_KEY` (Jules), `KEY_GITHUB_TOKEN`, `KEY_JULES_PROJECT_ID`. Adding new keys is a one-liner per provider.

So the architectural foundation exists — the work is filling in providers, extracting one shared tool-schema, and centralizing routing.

## Goal

Users can pick any of several AI providers — including a free on-device option — for both the chat tab and the contextual prompt path. The first turn after install works without the user having entered any key, by using Gemini Nano if the device supports it.

## Non-goals

- Image / vision input. Text-only.
- Streaming responses in the chat tab. Wait-then-render is fine for now.
- Cost tracking / quota dashboards.
- Routing logic that learns user preferences. The user picks via settings.
- A self-hosted / Ollama-style local server provider. On-device only via ML Kit / aicore.
- The Gemini-app intent bridge. Explicitly out of scope.

## Design

### Part 1 — Lift the tool schema out of `GeminiAdapter`

Today `GeminiAdapter` owns `FunctionDeclaration` + `Schema` builders for the four IdeTools. New module `app/src/main/kotlin/com/hereliesaz/ideaz/ai/ToolSchema.kt` exposes a provider-neutral description:

```kotlin
data class AiToolParam(val name: String, val type: String, val description: String, val required: Boolean = true)
data class AiToolSpec(val name: String, val description: String, val params: List<AiToolParam>)

object IdeToolSchema {
    val readFile = AiToolSpec("read_file", "...", listOf(AiToolParam("path", "string", "...")))
    val writeFile = AiToolSpec("write_file", "...", listOf(
        AiToolParam("path", "string", "..."),
        AiToolParam("content", "string", "..."),
    ))
    val listFiles = AiToolSpec("list_files", "...", listOf(AiToolParam("path", "string", "...")))
    val applyPatch = AiToolSpec("apply_patch", "...", listOf(AiToolParam("patch", "string", "...")))
    val all = listOf(readFile, writeFile, listFiles, applyPatch)
}
```

Each adapter converts this neutral form into its provider's native schema. `GeminiAdapter` keeps doing the `FunctionDeclaration` translation but reads from `IdeToolSchema.all`.

### Part 2 — Add an OpenAI-compatible adapter

`OpenAiCompatibleAdapter : ConversationalAiClient` lives at `app/src/main/kotlin/com/hereliesaz/ideaz/ai/OpenAiCompatibleAdapter.kt`. Constructor:

```kotlin
class OpenAiCompatibleAdapter(
    private val baseUrl: String,    // "https://api.groq.com/openai/v1"
    private val apiKey: String,
    private val model: String,      // "llama-3.3-70b-versatile"
    private val tools: IdeTools,
    private val httpClient: OkHttpClient = sharedClient,
) : ConversationalAiClient
```

The wire format follows the OpenAI Chat Completions schema with function-calling: `messages: [{role, content}]`, `tools: [{type:"function", function:{name, description, parameters}}]`, `tool_choice: "auto"`. The same tool-use loop as `GeminiAdapter` — call API, parse `tool_calls`, dispatch to `IdeTools`, append `tool` role messages with results, loop until the response has no tool calls.

This single adapter covers **Groq, Cerebras, Hugging Face Inference (TGI), Mistral La Plateforme, OpenAI, Anthropic via its OpenAI-compat endpoint, and any other OpenAI-protocol server**. Per-provider config is just `baseUrl` + `model` + key.

### Part 3 — Add a Gemini Nano on-device adapter

`GeminiNanoAdapter : ConversationalAiClient` at `app/src/main/kotlin/com/hereliesaz/ideaz/ai/GeminiNanoAdapter.kt`. Uses `com.google.ai.edge.aicore` (the AICore SDK exposed via Google Play services on supported devices).

```kotlin
class GeminiNanoAdapter(
    private val context: Context,
    private val tools: IdeTools,
) : ConversationalAiClient {
    suspend fun chat(messages: List<ChatMessage>): String {
        if (!GenerativeModel.isAvailable(context)) {
            return "Gemini Nano is not available on this device."
        }
        // …
    }
}
```

Caveats codified in the adapter:

- **Tool-use:** Gemini Nano's on-device API does **not** support function-calling. The adapter will run as a one-shot completion only, no tool dispatch. If the user's prompt clearly needs file-edit tools (the "Build" action does), the adapter returns text only; tool-using paths must route to a different model. Document this in the settings UI.
- **Availability:** falls back to a clear error string if the device lacks AICore, so the routing layer can mark Nano unavailable and pick the next provider.
- **No streaming.** Same as the others.

### Part 4 — Provider registry

Expand `AiModels` in `SettingsViewModel.kt`:

```kotlin
val GEMINI_NANO  = AiModel("GEMINI_NANO",  "Gemini Nano (on-device)", requiredKey = "")  // no key
val GROQ_LLAMA   = AiModel("GROQ_LLAMA",   "Groq · Llama 3.3 70B",     KEY_GROQ_API_KEY)
val CEREBRAS_LLAMA = AiModel("CEREBRAS_LLAMA", "Cerebras · Llama 3.1 70B", KEY_CEREBRAS_API_KEY)
val HF_INFERENCE = AiModel("HF_INFERENCE", "Hugging Face Inference",   KEY_HF_API_KEY)
val MISTRAL_SMALL = AiModel("MISTRAL_SMALL", "Mistral Small (free)",   KEY_MISTRAL_API_KEY)
```

Add the four new `KEY_*_API_KEY` constants + their `saveX`/`getX` accessors in `SettingsViewModel`. `AiModels.availableModels` returns the full list.

### Part 5 — Adapter factory

`AiAdapterFactory.create(model: AiModel, context: Context, tools: IdeTools, settings: SettingsViewModel): ConversationalAiClient` centralizes "given a model id, build the right adapter with the right key." Replaces the scattered `GeminiAdapter(apiKey, tools)` instantiations in `MainViewModel.sendChatMessage()` and `AIDelegate`.

Inside the factory:

```kotlin
return when (model.id) {
    AiModels.GEMINI_NANO -> GeminiNanoAdapter(context, tools)
    AiModels.GROQ_LLAMA  -> OpenAiCompatibleAdapter(
        baseUrl = "https://api.groq.com/openai/v1",
        apiKey  = settings.getApiKey(model.requiredKey),
        model   = "llama-3.3-70b-versatile",
        tools   = tools,
    )
    AiModels.CEREBRAS_LLAMA -> OpenAiCompatibleAdapter(
        baseUrl = "https://api.cerebras.ai/v1",
        apiKey  = settings.getApiKey(model.requiredKey),
        model   = "llama3.1-70b",
        tools   = tools,
    )
    AiModels.HF_INFERENCE -> OpenAiCompatibleAdapter(
        baseUrl = "https://api-inference.huggingface.co/v1",
        apiKey  = settings.getApiKey(model.requiredKey),
        model   = "meta-llama/Llama-3.3-70B-Instruct",
        tools   = tools,
    )
    AiModels.MISTRAL_SMALL -> OpenAiCompatibleAdapter(
        baseUrl = "https://api.mistral.ai/v1",
        apiKey  = settings.getApiKey(model.requiredKey),
        model   = "mistral-small-latest",
        tools   = tools,
    )
    AiModels.JULES_DEFAULT -> JulesAdapter(...)   // wraps existing runJulesTask
    AiModels.GEMINI_FLASH, AiModels.GEMINI_PRO -> GeminiAdapter(
        apiKey = settings.getGoogleApiKey(), tools = tools,
    )
    else -> error("Unknown model: ${model.id}")
}
```

### Part 6 — Replace ad-hoc routing with the factory

- `MainViewModel.sendChatMessage()` line 179: remove the hardcoded `GeminiAdapter(...)` and instead look up the assignment via `settingsViewModel.getAiAssignment(KEY_AI_ASSIGNMENT_DEFAULT)` and call the factory. The chat tab now respects the user's chosen model.
- `AIDelegate.startContextualAITask()` line 205: replace the explicit `when` with a factory call. The contextual prompt path automatically supports every registered model.
- Jules wraps remain — they're behind a `JulesAdapter` shim that exposes the `ConversationalAiClient.chat()` shape but internally creates a session and polls. This unifies the call site even though Jules's wire shape is different.

### Part 7 — Settings UI

Add a "Free providers" section in `SettingsScreen.kt` listing each new provider with a one-line description and a key input. Use the existing `AzTextBox` for keys. Each provider's row hyperlinks to the signup page (open in browser, the existing `Intent.ACTION_VIEW` pattern at SettingsScreen.kt:389+). Keep keys plaintext for now — the existing storage policy is plaintext; encryption is a separate concern outside this spec.

Add a default-model selector that lists every model with a status pill: "ready" (key present or no key needed), "needs key" (greyed out), "unavailable" (Nano on unsupported device).

### Part 8 — First-run experience

On app launch, if no `aiAssignment` is set, check `GeminiNanoAdapter.isAvailable(context)`:

- If yes → default `aiAssignment` to `GEMINI_NANO`.
- If no → default to `GEMINI_FLASH` and show the existing key prompt.

This makes the app usable out of the box on supported Android devices with zero configuration.

## Files touched

New:

- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/ToolSchema.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/OpenAiCompatibleAdapter.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/GeminiNanoAdapter.kt`
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/JulesAdapter.kt` (shim around `runJulesTask`)
- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/AiAdapterFactory.kt`

Modified:

- `app/src/main/kotlin/com/hereliesaz/ideaz/ai/GeminiAdapter.kt` — consume `IdeToolSchema` instead of inline declarations.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/SettingsViewModel.kt` — add four new key constants + accessors; expand `AiModels.availableModels`.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt` — chat-tab routing via factory.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/AIDelegate.kt` — contextual-task routing via factory.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/SettingsScreen.kt` — new provider rows + default-model selector.
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — add `com.google.ai.edge.aicore:aicore:0.0.1-exp01` (or current latest stable) and `com.squareup.okhttp3:okhttp` (likely already present).

## Verification

1. `./gradlew :app:assembleDebug` and `./gradlew build` — `BUILD SUCCESSFUL`.
2. **Fresh install on a Nano-capable device:** open app, send a prompt without entering any key. Expect: Nano responds. Settings shows Nano as the active default with a "ready" pill.
3. **Fresh install on a non-Nano device:** open app, send a prompt. Expect: the existing Gemini key prompt appears. Nano shows "unavailable" in settings.
4. **Enter a Groq key:** flip default to Groq · Llama 3.3 70B. Send a chat prompt — should route through `OpenAiCompatibleAdapter` with the Groq base URL. Verify the network call in the system log tab.
5. **Run a tool-using prompt** with Groq selected: "write hello.txt with the word hi" → check that `IdeTools.writeFile` runs and the file appears. Proves the OpenAI function-calling schema works against Groq.
6. **Try the same on Cerebras / HF / Mistral** after entering each key.
7. **Set default back to Gemini Flash:** verify regression-free — existing user experience untouched.
8. **Pick Nano + a tool-requiring prompt:** verify the adapter responds with text only and logs "Gemini Nano does not support tools" rather than crashing.
9. **Pick Jules for an Android project:** verify the `JulesAdapter` shim runs the existing session-poll flow unchanged.

## Risks

- **OpenAI-compat schema variance.** Not every provider implements OpenAI function-calling identically. Groq and Cerebras do; HF Inference depends on the served model; Mistral has its own `tool_calls` quirks. Mitigation: keep tool-use loop liberal in what it accepts (handle missing fields), log raw response bodies when tool parsing fails, gate per-provider with a "tools supported" flag if needed.
- **Gemini Nano dependency size & API stability.** `aicore` is still labeled experimental in some channels. Mitigation: gate behind `isAvailable()` and a `BuildConfig.ENABLE_NANO` flag if needed to ship without it on low-end devices.
- **Key leakage.** Plaintext SharedPreferences for keys is the existing pattern; we're not making it worse, but the new providers' keys are also at risk. Mitigation: out of scope here. File a follow-up to migrate all keys to `EncryptedSharedPreferences` in one pass.
- **Settings UI sprawl.** Adding 5+ provider rows to the settings screen risks clutter. Mitigation: group under a single collapsible "AI Providers" section; show only providers with keys entered in the default-model dropdown by default.
- **Routing surprises.** A user who configured Jules globally may be surprised when the PWA fast-path spec routes around Jules. Mitigation: log the route decision; document the per-project-type override behavior.

## Open questions deferred

- Should the AI assignment be per-project-type instead of global? (e.g., "Nano for chat, Groq for contextual tasks, Jules for Android only"). Default to **per-task** (matching existing `KEY_AI_ASSIGNMENT_*` constants); per-project-type can come later.
- Should we expose a "cheapest-available" auto-routing mode? Possible follow-up. Out of this spec.
- Streaming. Likely the next request; explicit non-goal here.
