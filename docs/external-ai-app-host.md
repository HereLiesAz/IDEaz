# External AI App Host

IDEaz ships two Android distributions from one source tree.

| Distribution | Gradle build type | External AI app automation | Overlay/freeform host |
|---|---|---:|---:|
| GitHub | `release` | Yes | Yes |
| Google Play | `play` | No | No |

`debug` behaves like the GitHub build.

## GitHub build

When Gemini is the assigned provider, IDEaz first tries the installed Gemini app.
It shares a redacted `project.txt`, drives the prompt field through the explicitly
enabled IDEaz accessibility service, captures the response, and applies any
returned unified diff through the normal IDEaz checkpoint/review/approval gate.
If an AI Studio key is configured, the Gemini API is the automatic fallback.

Presentation is handled by `ExternalAiWindowHost`:

- `OVERLAY_SHELL` — four IDEaz overlay panels frame the real app while leaving its live center surface untouched and touchable.
- `FREEFORM` — requests a bounded Android app window with `ActivityOptions.setLaunchBounds()`.
- `BUBBLE` — compact IDEaz shell around the real app; Android does not expose a public API for one app to forcibly bubble another app.
- `EMBEDDED` — best-effort adjacent/bounded launch. True cross-app Activity Embedding still requires the target app to opt in.
- `FULLSCREEN` — plain external app launch.

The preferred mode is stored under `external_ai_window_mode`; overlay shell is the default.

## Play build

`bundlePlay` produces the Play AAB. Its manifest does not register the external-AI
accessibility service or overlay service and does not request `SYSTEM_ALERT_WINDOW`
or the special-use foreground-service permission. `BuildConfig.EXTERNAL_AI_AUTOMATION`
and `BuildConfig.EXTERNAL_AI_OVERLAY` are both compile-time false.

The Play publishing workflow builds `bundlePlay`; the GitHub release pipeline stays
on the existing `release` build type.
