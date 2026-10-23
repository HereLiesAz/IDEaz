# IDEaz Privacy Policy

Effective date: 2026-10-23

IDEaz is an Android app that edits your web projects with an AI model you choose. This page
says what data leaves your device, where it goes, and what never does.

## The short version

- **IDEaz sends no automatic error, crash or build-log reports. Anywhere. Ever.** There is no
  crash reporter, no error collector, no analytics, no advertising and no IDEaz account.
- The only data that leaves your device is what **you send** to the AI providers you use and to
  the GitHub and Cloudflare services you configure. Each is described below.
- Your projects, API keys, tokens and chat history are stored on your device.

## What IDEaz does not do

- It does not report crashes. If IDEaz crashes, Android handles it; nothing is uploaded and no
  GitHub issue is filed for you.
- It does not collect, upload or batch error messages, stack traces, logs or build logs.
- It does not include analytics, telemetry, crash-reporting or advertising libraries.
- It does not request access to your contacts, location, microphone or camera.

Earlier versions could file a crash report as a public GitHub issue. That feature, its consent
dialog and its Settings toggle have been removed. On first launch of this version IDEaz also deletes
the leftover setting values it kept.

## What you send, and to whom

### AI providers

When you send a prompt, IDEaz sends your message, the conversation so far, the element context
you tapped, any attachments you added, a redacted and size-limited snapshot of the project, and
the file contents the model asks to read, to the provider assigned to that task in Settings:
Google Gemini, Anthropic, OpenAI, DeepSeek, Groq, Cerebras, Hugging Face or Mistral using your own
API key, or a keyless free gateway (Kilo Gateway, LLM7, OVHcloud) that needs no key. Their
handling of that data is governed by their own terms and privacy policies. The keyless gateways
are anonymous free tiers and may log prompts.

**Automatic provider switching.** If a provider reports that its quota or rate limit is used up,
IDEaz resends the same request to the next available provider: the keyless gateways first, then
any other provider for which you saved a key. Each provider is tried at most once per request,
and IDEaz shows a short notice in the chat when it switches. This means a prompt can reach a
provider other than the one you picked. If you do not want that, do not use the keyless models and
save a key for only one provider.

### The Aive (optional)

If you use The Aive, IDEaz sends it the redacted project snapshot and the conversation. If you
paste a pairing code to reach The Aive on another device, the traffic goes through a Cloudflare
relay as AES-256-GCM ciphertext; the relay sees only metadata (room id, sizes, timing, online
state).

### Installed Gemini app (GitHub APK only)

If you turn on the optional bridge in the GitHub build, IDEaz hands your prompt to the Gemini app
installed on your device, and reads the response from it. The Google Play build does not include
this.

### GitHub (optional)

If you clone, publish or deploy, IDEaz sends your GitHub token and your repository content to
GitHub (`github.com`, `api.github.com`). Projects live in git on your device; you do not need a
GitHub account to use IDEaz.

### Cloudflare Worker (Deploy only)

When you press Deploy, IDEaz sends your GitHub token, the repository name and the pushed commit
SHA to the maintainer's build Worker (`workflows.hereliesaz.workers.dev`, hosted on Cloudflare) so
it can build and publish the site. Do not deploy if you are not comfortable with that.

### Preview content

Your project's own pages run in an embedded preview. Anything your project's code requests from
the network (fonts, scripts, APIs) is requested from your device by that code, the same as in a
browser.

## What stays on your device

- Projects and their git history.
- API keys and tokens, in an Android Keystore-backed store. They are included in a settings export
  only when you create one, encrypted with a password you choose.
- Chat history, preferences, and the short list of which AI providers are
  temporarily at their limit (used so IDEaz does not retry them too soon).

You can delete all of it by clearing the app's storage or uninstalling IDEaz.

## Logs

IDEaz writes diagnostic lines to the Android log on your device. The console's "Copy Log" action
copies a redacted version to your clipboard so you can share it yourself if you choose. IDEaz never
sends logs for you.

## Outside IDEaz's control

Android, Google Play and your device maker may collect their own diagnostics under their own
policies. IDEaz does not control that and this policy does not cover it.

## Changes and contact

Changes to this policy are made in this file and are visible in the repository history at
<https://github.com/HereLiesAz/IDEaz>. Questions: open an issue there yourself; IDEaz will not
open one for you.
