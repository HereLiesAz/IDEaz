---
layout: default
title: IDEaz - Privacy
description: What data leaves your device when you use IDEaz.
---

<h1>Privacy</h1>
<p><em>Effective 2026-10-23</em></p>

<p>
    <strong>IDEaz sends no automatic error, crash or build-log reports.</strong> There is no crash
    reporter, no analytics, no advertising and no IDEaz account. The only data that leaves your device
    is what you send to the AI providers you use and to the GitHub and Cloudflare services you
    configure.
</p>

<h2>What you send, and to whom</h2>
<ul>
    <li><strong>AI providers.</strong> Your prompt, the conversation, the element you tapped, attachments
        and a redacted, size-limited project snapshot go to the provider assigned in Settings: Gemini,
        Anthropic, OpenAI, DeepSeek, Groq, Cerebras, Hugging Face or Mistral with your own key, or a
        keyless free gateway (Kilo Gateway, LLM7, OVHcloud) that may log prompts.</li>
    <li><strong>Automatic provider switching.</strong> If a provider's quota or rate limit is used up,
        IDEaz resends the same request to the next one (keyless first, then any provider you saved a key
        for), once per provider, and tells you in the chat. A prompt can therefore reach a provider other
        than the one you picked.</li>
    <li><strong>The Aive (optional).</strong> The redacted snapshot and conversation. Between devices it
        travels through a Cloudflare relay as encrypted data.</li>
    <li><strong>GitHub (optional).</strong> Your token and repository content when you clone, publish or
        deploy.</li>
    <li><strong>Cloudflare Worker (Deploy only).</strong> Your GitHub token, the repository name and the
        commit SHA go to the maintainer's build Worker so it can build and publish the site.</li>
</ul>

<h2>What stays on your device</h2>
<p>
    Projects, git history, API keys and tokens (in an Android Keystore-backed store), chat history and
    preferences. Logs stay in the Android log; "Copy Log" copies a redacted version to your clipboard so
    you can share it yourself. IDEaz never sends logs for you.
</p>

<p>
    Full policy:
    <a href="https://github.com/HereLiesAz/IDEaz/blob/master/PRIVACY.md">PRIVACY.md</a>.
</p>
