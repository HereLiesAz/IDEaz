# Phase 0 Follow-ups

Two regressions / debts surfaced during Phase 0 that are out of scope for this
phase but must be resolved in Phase 1 or Phase 2 before the affected paths are
exercised. Recorded here so they don't get lost.

---

## 1. Phase 2 — `RepoDelegate.uploadProjectSecrets` regression

> **STATUS: RESOLVED.** Resolution option 2 (pure-JVM NaCl) was taken:
> `utils/GithubSecretBox` implements libsodium-compatible `crypto_box_seal` with
> BouncyCastle primitives (X25519 + HSalsa20 + XSalsa20-Poly1305 + Blake2b nonce)
> — no native binding, no `.so`. `uploadProjectSecrets` again fetches the repo
> Actions public key, seals each secret, and PUTs it via `createSecret`. bcprov
> is now an explicit `implementation` dependency; release enables R8
> (`isMinifyEnabled = true`) to strip its unused remainder (debug ~91 MB →
> release ~19 MB). Correctness is pinned by `GithubSecretBoxTest` (HSalsa20 known
> answer vs NaCl `core1.c`, secretbox tamper rejection, full seal→open round
> trip). Acceptance note: the JVM round trip passes; an end-to-end push to a real
> repo's secret store still warrants one on-device smoke test. The original
> write-up is kept for history.

**Where:** `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/RepoDelegate.kt:344-348`

**What happened:** Task 5 of Phase 0 deleted the `lazysodium-android` dependency
(and its JNA + `hiddenapibypass` companions) because lazysodium existed only to
support the unimplemented Ed25519 Zipline-manifest-signing TODO. But
`RepoDelegate.uploadProjectSecrets` was *also* using lazysodium — for sealed-box
encryption of secret values when uploading them via the GitHub Actions secrets
REST API. That function was reduced to a no-op log to keep the build green.

GitHub mandates libsodium sealed-box encryption for any value sent to the
`PUT /repos/{owner}/{repo}/actions/secrets/{name}` endpoint. There is no plain
HTTP path that bypasses it.

**Impact:** None today (Phase 0 doesn't dispatch Actions builds with device-side
secrets). Phase 2 *cannot* dispatch a build that needs secrets without this
path working. Examples that will trip on it: signing-keystore secrets, AI
provider keys consumed by CI, anything pushed via `BuildDelegate` plumbing.

**Resolution options to evaluate when Phase 2 starts:**

1. **Reintroduce a sodium binding.** Lazysodium-android is the historical
   choice; it works but reintroduces JNA + an `.so` and re-creates the
   stripping problem we just escaped. Pure-JVM sodium options are smaller.
2. **Use a pure-JVM NaCl implementation.** A tiny dedicated implementation of
   `crypto_box_seal` (the only operation we need) is under 200 lines and has no
   native dependencies. Lowest blast radius.
3. **Use GitHub's encrypted-secrets REST API differently.** There isn't a
   different way; libsodium sealed-box is what GitHub mandates for sealing
   secret values. Mentioned only to acknowledge it's not an out.

**Acceptance for resolution:** `uploadProjectSecrets` writes a real value to a
test repo's secret store, which a hand-pushed workflow successfully reads back.

---

## 2. Phase 1 — `WebViewAssetLoader` migration (security + deprecation)

> **STATUS: RESOLVED.** The migration landed: `WebProjectHost` serves content via
> `WebViewAssetLoader` at `https://appassets.androidplatform.net/`, sets
> `allowFileAccess = false` / `allowContentAccess = false`, and no longer references
> `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` (the
> `@Suppress("DEPRECATION")` is gone). `SourceContextHelper` enforces project-directory
> containment on the `__source:` JS-bridge tag. Item 6 below (source-map regeneration)
> remains future work. The original write-up is kept for history.

**Where:** `app/src/main/kotlin/com/hereliesaz/ideaz/ui/web/WebProjectHost.kt:55-63`,
plus `IdeazAccessibilityService.kt:150-154`.

**What happened:** Task 10 of Phase 0 was a deprecation cleanup. The
`WebSettings.allowFileAccessFromFileURLs` and
`WebSettings.allowUniversalAccessFromFileURLs` flags in `WebProjectHost` were
*kept* — `@Suppress("DEPRECATION")` was applied — because the WebView still
loads project content over `file://` URIs, and removing the flags would break
the current loader. The design's plan is to migrate to `WebViewAssetLoader`
serving from a virtual `https://appassets.androidplatform.net/` host (Phase 1A),
at which point the flags become both unnecessary and unsafe.

**Impact (security, not just deprecation cleanup):** while `file://` loads are
in use, those two flags create a real cross-origin file-read / universal-XSS
exposure. Any hostile content reaching the WebView (a malicious PWA, an
attacker-controlled fragment, etc.) can read arbitrary files the app process
can read. This is *not* hypothetical — these flags exist specifically to allow
that, and Android's documentation explicitly warns against them.

**Phase 1 resolution:** as part of Phase 1A (Render):
1. Land `WebViewAssetLoader` mounted at `https://appassets.androidplatform.net/`.
2. Switch all PWA loads to the virtual https origin.
3. Delete `webSettings.allowFileAccessFromFileURLs = true` and
   `webSettings.allowUniversalAccessFromFileURLs = true`.
4. Drop the `@Suppress("DEPRECATION")` annotation that gated them.
5. Verify a hand-test: a PWA that previously loaded continues to load; a fetch
   to a `file://` resource is now blocked.
6. **Rebuild source-map generation** as part of the new Web build path. Phase 0
   removed the on-device toolchain (Task 6), which deleted
   `GenerateSourceMap.kt`. The orphaned `onSourceMapUpdated` callback chain,
   `OverlayDelegate.sourceMap` field, `SourceMapParser`, and `SourceMapEntry`
   model were all removed in the post-deletion cleanup. Web inspect-on-tap
   currently resolves context only from the `__source:filename:line__` DOM tag
   emitted by the runtime; the Android-resource-id lookup path is gone.
   Phase 1A should re-emit a source map (or equivalent metadata) from whatever
   bundler the new Web build path settles on, and re-wire it into
   `SourceContextHelper` so element-tap inspection can resolve back to source
   for non-DOM elements as well.

Track this in the Phase 1A milestone as a hard requirement, not a polish item.

### Companion cleanup

**Where:** `app/src/main/kotlin/com/hereliesaz/ideaz/services/IdeazAccessibilityService.kt:150-154`

`IdeazAccessibilityService` still has an `AccessibilityNodeInfo.recycle()` call
gated for `Build.VERSION.SDK_INT < 33`, with `@Suppress("DEPRECATION")`. Since
`minSdk = 30` and node recycling has been auto-managed since API 30, the
condition can never be true on a device IDEaz runs on. The branch is dead
code.

**Phase 1 resolution:** delete the entire SDK-gated branch (smaller surface
than a real refactor). One line of cleanup; do it alongside the
`WebViewAssetLoader` migration commit so the deprecation file is fully
clean afterward.
