# Port from aive: Azphalt store, Google AI Mode, keyless image generation

Source: HereLiesAz/aive. Branch: `claude/keyless-models` (PR #961) or a follow-up branch.

## 1. Azphalt store: LLM packages only
Workflows, roles, breeds, model weights and `moyai-session` are Aive-only. Not ported.
- [ ] `azphalt/AzphaltRepositoryClient`: index, search (`kind=llm`), detail, download with a size cap, revocations. OkHttp, not Ktor.
- [ ] `azphalt/AzphaltModels`: summary, manifest, the `llm` block, `directUseProblem()`.
- [ ] `azphalt/AzphaltPackageVerifier`: zip safety, SHA-256 file list, Ed25519 `signature.json` against the index signing keys, publisher pin per package (bcprov is already a dependency).
- [ ] `azphalt/StoreLlmProviders`: installed list in prefs; `providerId = azphalt:<id>`.
- [ ] Dynamic `AiModel`s from installed packages → `OpenAiCompatibleAdapter(baseUrl, key?, defaultModel)`; key slot when auth ≠ none.
- [ ] Store screen: search, prepared panel (trust, prompt handling, terms, inputs), install, remove.
- [ ] Tests: verifier (good, tampered, unlisted, untrusted), directUseProblem, URL rules.

## 2. Google AI Mode
- [ ] `GoogleAiMode` (common): URL `udm=50`, percent-encoding, answer extraction, refusal detection, polling (75 s timeout).
- [ ] `AndroidAiModeBrowser`: hidden WebView on Main, clear cache and cookies afterwards.
- [ ] `GoogleAiModeClient : ConversationalAiClient`: flattens the chat into one question (≤1,800 chars), no tools, so it can't edit code.
- [ ] Never picked automatically and never a failover target. Privacy text in Settings.
- [ ] Tests: extraction, refusal, polling with a fake clock.

## 3. Image generation (AI Horde, keyless; optional key)
- [ ] `ImageGenerator` interface + `AiHordeImageGenerator` (async → check → status → download; `apikey` 0000000000 when there's no key).
- [ ] IDE tool `generate_image(prompt, path)` writes the PNG into the project, behind the usual edit-review gate.
- [ ] UI: prompt → preview → save to path.
- [ ] Tests with MockWebServer.

## Review
(filled after implementation)
