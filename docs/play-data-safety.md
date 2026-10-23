# Google Play Data safety notes

Effective 2026-10-23. Companion to [`../PRIVACY.md`](../PRIVACY.md). These notes say what the Play
build's Data safety form must declare, derived from what the code does. Re-check them whenever a
network call is added or removed.

## Declare: no collection

IDEaz collects no data for its own purposes. It has no IDEaz backend, no accounts, no analytics and
no advertising.

| Data safety category | Collected by IDEaz | Notes |
|---|---|---|
| Crash logs | **No** | There is no crash reporter. IDEaz sends no crash data anywhere. |
| Diagnostics (performance, other) | **No** | No telemetry. Logs stay in the on-device Android log. |
| App activity, web browsing, location, contacts, files shared with IDEaz's developer | **No** | |
| Device or other IDs | **No** | |

"Collected" here means transmitted to the developer or to a service IDEaz's developer operates for
the developer's use. The one developer-operated endpoint is the Deploy Worker below.

## Declare: data the user sends to third parties they choose

These flows are user-initiated and go to services the user configured. Declare them as data shared
with third parties, optional, for app functionality:

- **AI providers** (Google Gemini, Anthropic, OpenAI, DeepSeek, Groq, Cerebras, Hugging Face, Mistral
  with the user's own key; Kilo Gateway, LLM7 and OVHcloud keyless): prompts, conversation, element
  context, attachments and a redacted, bounded project snapshot. When one provider hits its quota,
  IDEaz resends the same request to the next one (keyless first, then any keyed provider), so a
  prompt can reach a provider other than the one chosen.
- **GitHub** (`github.com`, `api.github.com`): the user's token and repository content, when the user
  clones, publishes or deploys.
- **Deploy Worker** (`workflows.hereliesaz.workers.dev`, Cloudflare): on Deploy, the user's GitHub
  token, repository name and commit SHA. Not collected for analytics; used to build and publish.
- **The Aive relay** (Cloudflare), only if the user pastes a pairing code: end-to-end encrypted
  traffic; the relay sees metadata only.

## Security practices

- Data in transit is encrypted (HTTPS; the Aive relay adds AES-256-GCM).
- API keys and tokens are stored in an Android Keystore-backed store.
- The user can delete all data by clearing app storage or uninstalling. IDEaz holds no server-side
  copy to delete.

## Not covered

Android and Google Play diagnostics that the platform collects independently of the app.
