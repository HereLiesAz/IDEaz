# The Aive integration

IDEaz uses [The Aive](https://github.com/HereLiesAz/aive) through its bound remote service. The protocol is described in The Aive's `docs/Remote-API.md`. IDEaz keeps a copy of its constants in `ai/aive/AiveRemoteProtocol.kt`; if the protocol changes, update both copies.

## Requirements

- The Aive is installed: either `com.hereliesaz.aive` (Play) or `com.hereliesaz.haive` (GitHub).
- IDEaz and The Aive are signed with the same key. The service is protected by a signature permission, `com.hereliesaz.aive.permission.REMOTE`.
  - The Aive defines that permission. IDEaz only requests it, and must never declare it: a second definition under a different key makes installation fail.
  - Play App Signing gives each app its own key unless both apps use the same upload and signing key. With separate keys, binding fails, and IDEaz says so in the chat.

Instead of installing The Aive on this device, you can pair with one on another device through a relay; see [Remote device (relay)](#remote-device-relay).

## Chat model

Select **The Aive (linked provider)** as an AI assignment. `AiveAdapter` then sends The Aive the following:

- a redacted project snapshot, capped at 240 KB (`RepoSnapshot`; 200 KB over the relay)
- the conversation
- the instruction to answer with at most one unified diff

The Aive runs the prompt on its linked provider. If no provider is linked, the request fails; The Aive never falls back to a keyless gateway on its own. `TextPatchEdits` applies the diff behind a checkpoint and the usual review gate, which is the same path the installed-Gemini bridge uses.

## Workflows

Typing `/aive <objective>` in the chat does the following:

1. The objective is queued in The Aive. The project name is the IDEaz app name, and `origin` is passed along when it is a GitHub repository; credentials are stripped from the URL.
2. On this device, IDEaz opens The Aive, which launches the workflow through the same path as its own launch form. If The Aive cannot be opened, the chat says the workflow is only queued and starts when you open The Aive.
3. IDEaz watches the run and posts to the chat when the workflow waits for approval and when it completes, fails or is cancelled. It follows the run id when The Aive's launch reply carries one (`KEY_RUN_ID`). When the reply carries a launch id (`KEY_LAUNCH_ID`, protocol version 2), a later `MSG_LAUNCH_OUTCOME` reporting that launch failed ends the watch and the chat shows The Aive's reason, as over the relay; otherwise the first run with the same objective seen while still unfinished, so an earlier finished run with that objective is ignored.

Over the relay, step 2 differs: IDEaz cannot open The Aive on another device, so the chat says "Queued on your Aive device. It starts when you open The Aive there." and follows the run from the status the device sends back. If The Aive refuses the launch, the chat shows its reason.

## Remote device (relay)

When The Aive runs on another device, IDEaz reaches it through a Cloudflare relay (the Worker lives in HereLiesAz/aive under `relay/`; its wire contract is in The Aive's `docs/Remote-API.md`, `## Relay`). `AiveTransports` picks the route for each call: The Aive on this device if it is installed and binds, else the relay if a pairing code is saved, else an error that names both options. Both the chat model and `/aive` use whichever route that is.

### Pairing

1. In The Aive on the other device, turn on Remote access and copy the pairing code.
2. In IDEaz, open Settings, find "The Aive on another device", tap Paste and then Save. Test connection sends a ping and waits for the pong. Remove deletes the code.

The code is `aive1:` followed by base64url JSON holding the relay address (https only), the room, a client token and a 32-byte key. IDEaz checks all of that before saving it. It is stored with the API keys in the Android Keystore-backed credential store. A settings export holds it only inside the password-encrypted export file, as it does for API keys.

### How it works

- IDEaz opens a WebSocket to `<relay>/v1/rooms/<room>?role=client` with `Authorization: Bearer <clientToken>` when it needs one, shares it between overlapping calls, and closes it shortly after the last use. A request that fails before it is sent is retried once; one already sent is not, since The Aive may have acted on it.
- Each message is sealed with AES-256-GCM under the pairing key (`AiveRelayCodec`): a fresh nonce per frame, and the room, direction and sequence number bound in as associated data. Each sender uses a random session id with a strictly increasing sequence number and a timestamp. IDEaz drops frames that fail authentication, repeat or go backwards in sequence, or are more than 10 minutes off.
- Requests carry an id and the reply is matched to it. When the relay reports `host-offline`, a request fails at once with "Your Aive device is offline or Remote access is off".
- Prompts over the relay are capped at 500,000 bytes. IDEaz shrinks the project snapshot to 200 KB for it and fails with a clear message if the conversation alone is still too long.

### Security notes

- The pairing code is a bearer secret. Anyone who holds it can use your Aive and read its replies. Do not paste it into chats or tickets.
- The relay sees ciphertext and metadata only: the room id, message sizes and timing, and when each side is online. It never sees prompts, replies or project files.
- To revoke access, turn Remote access off in The Aive (and pair again later for a new code). Removing the code in IDEaz only stops this device.

## Keyless models

Kilo Gateway, LLM7 and OVHcloud are OpenAI-compatible endpoints that IDEaz calls without a key. When the key is blank, `OpenAiCompatibleAdapter` omits the `Authorization` header. These endpoints and models mirror The Aive's `HostedLlmProviders`, so keep the two lists in sync.

## Deploy and your GitHub token

Unrelated to The Aive but worth stating here with the other network flows: **Deploy** (the rail item) sends your GitHub token, the repository name and the pushed commit SHA to the maintainer's Worker at `workflows.hereliesaz.workers.dev`, which verifies that the token can push to the repository. The Worker builds and publishes only repositories owned by HereLiesAz; for other owners it refuses after receiving that request. Details: [`workflow.md`](workflow.md) §2.2.
