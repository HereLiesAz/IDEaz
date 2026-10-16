# The Aive integration

IDEaz uses [The Aive](https://github.com/HereLiesAz/aive) through its bound remote service. The protocol is described in The Aive's `docs/Remote-API.md`. IDEaz keeps a copy of its constants in `ai/aive/AiveRemoteProtocol.kt`; if the protocol changes, update both copies.

## Requirements

- The Aive is installed: either `com.hereliesaz.aive` (Play) or `com.hereliesaz.haive` (GitHub).
- IDEaz and The Aive are signed with the same key. The service is protected by a signature permission, `com.hereliesaz.aive.permission.REMOTE`.
  - The Aive defines that permission. IDEaz only requests it, and must never declare it: a second definition under a different key makes installation fail.
  - Play App Signing gives each app its own key unless both apps use the same upload and signing key. With separate keys, binding fails, and IDEaz says so in the chat.

## Chat model

Select **The Aive (linked provider)** as an AI assignment. `AiveAdapter` then sends The Aive the following:

- a redacted project snapshot, capped at 240 KB (`RepoSnapshot`)
- the conversation
- the instruction to answer with at most one unified diff

The Aive runs the prompt on its linked provider. If no provider is linked, the request fails; The Aive never falls back to a keyless gateway on its own. `TextPatchEdits` applies the diff behind a checkpoint and the usual review gate, which is the same path the installed-Gemini bridge uses.

## Workflows

Typing `/aive <objective>` in the chat does the following:

1. The objective is queued in The Aive. The project name is the IDEaz app name, and `origin` is passed along when it is a GitHub repository; credentials are stripped from the URL.
2. IDEaz opens The Aive, which launches the workflow through the same path as its own launch form. If The Aive cannot be opened, the chat says the workflow is only queued and starts when you open The Aive.
3. IDEaz watches the run and posts to the chat when the workflow waits for approval and when it completes, fails or is cancelled. It follows the run id when The Aive's launch reply carries one; otherwise the first run with the same objective seen while still unfinished, so an earlier finished run with that objective is ignored.

## Keyless models

Kilo Gateway, LLM7 and OVHcloud are OpenAI-compatible endpoints that IDEaz calls without a key. When the key is blank, `OpenAiCompatibleAdapter` omits the `Authorization` header. These endpoints and models mirror The Aive's `HostedLlmProviders`, so keep the two lists in sync.
