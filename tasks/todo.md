# Port from aive: Azphalt store, Google AI Mode, keyless image generation

Source: HereLiesAz/aive. Branch: `claude/keyless-models` (PR #961) or a follow-up branch.

## 1. Azphalt store: LLM packages only
Workflows, roles, breeds, model weights and `moyai-session` are Aive-only. Not ported.
- [x] `azphalt/AzphaltRepositoryClient`: index, search (`kind=llm`), detail, download with a size cap, revocations. OkHttp, not Ktor.
- [x] `azphalt/AzphaltModels`: summary, manifest, the `llm` block, `directUseProblem()`.
- [x] `azphalt/AzphaltPackageVerifier`: zip safety, SHA-256 file list, Ed25519 `signature.json` against the index signing keys, publisher pin per package (bcprov is already a dependency).
- [x] `azphalt/StoreLlmProviders`: installed list in prefs; `providerId = azphalt:<id>`.
- [x] Dynamic `AiModel`s from installed packages → `OpenAiCompatibleAdapter(baseUrl, key?, defaultModel)`; key slot when auth ≠ none.
- [x] Store screen: search, prepared panel (trust, prompt handling, terms, inputs), install, remove.
- [x] Tests: verifier (good, tampered, unlisted, untrusted), directUseProblem, URL rules.

## 2. Google AI Mode
- [x] `GoogleAiMode` (common): URL `udm=50`, percent-encoding, answer extraction, refusal detection, polling (75 s timeout).
- [x] `AndroidAiModeBrowser`: hidden WebView on Main, clear cache and cookies afterwards.
- [x] `GoogleAiModeClient : ConversationalAiClient`: flattens the chat into one question (≤1,800 chars), no tools, so it can't edit code.
- [x] Never picked automatically and never a failover target. Privacy text in Settings.
- [x] Tests: extraction, refusal, polling with a fake clock.

## 3. Image generation (AI Horde, keyless; optional key)
- [x] `ImageGenerator` interface + `AiHordeImageGenerator` (async → check → status → download; `apikey` 0000000000 when there's no key).
- [x] IDE tool `generate_image(prompt, path)` writes the PNG into the project, behind the usual edit-review gate.
- [x] UI: prompt → preview → save to path.
- [x] Tests with MockWebServer.

## Review
Independent review requested changes; addressed:
- `generate_image` generation now runs before the NonCancellable edit (`prepareIdeTool`), interruptible; only the write is checkpointed. Temp-file write.
- Store trust root is compiled in per repository (`AZPHALT_PINNED_SIGNING_KEYS`), not taken from the index.
- An unsigned version of a package that was installed signed is refused.
- Horde image download: size cap enforced while reading, base64 capped, host allow-list.
- AI Mode: human check times out (5 min) and is skipped when no screen is watching; page reads time out; hidden view only navigates to Google; no app-wide cache clear.

Still open:
- Revocation list is unsigned (the store publishes no signature): a tampered reply can hide a revocation, never enable anything.
- AI Mode cookies fall back to the shared jar on WebViews without MULTI_PROFILE.

Follow-up (claude/review-followups):
- Installed store models are re-checked against revocations at start-up and when the store opens; revoked ones are switched off and labelled.
- AI Mode's hidden and visible views use their own WebView profile.
- Nothing exercised live: azphalt.store install, AI Horde, Google AI Mode.
