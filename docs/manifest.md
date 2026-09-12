# Manifest and Distribution Surface

IDEaz has one common Android manifest plus distribution-specific overlays.

| Source | Distribution | Purpose |
|---|---|---|
| `app/src/androidMain/AndroidManifest.xml` | all | Policy-safe common app surface |
| `app/src/debug/AndroidManifest.xml` | GitHub/debug | Installed-AI accessibility + overlay host |
| `app/src/release/AndroidManifest.xml` | GitHub/release | Installed-AI accessibility + overlay host |
| `app/src/play/AndroidManifest.xml` | Play | Intentionally empty overlay; inherits common surface only |

## Common manifest

The common manifest declares:

* `POST_NOTIFICATIONS` — progress/results for long-running work.
* `MainApplication`.
* `MainActivity` — launcher activity, `singleTask`.
* `FileProvider` — non-exported, grant-on-demand provider used when IDEaz explicitly stages and shares the redacted project snapshot or prompt attachments to another app.
* `CrashReportingService` — non-exported, isolated `:crash_reporter` process. It is used only after the user-facing crash-reporting disclosure/setting allows it.

No external-AI accessibility or overlay permission is present in this common manifest.

## GitHub APK manifest additions

`debug` and `release` add the installed-AI host:

* `FOREGROUND_SERVICE`
* `FOREGROUND_SERVICE_SPECIAL_USE`
* `SYSTEM_ALERT_WINDOW`
* package visibility for the supported Gemini packages
* `IdeazAccessibilityService`
* `ExternalAiOverlayService`

### `IdeazAccessibilityService`

* `android:exported="true"` because Android binds accessibility services through the system-managed `BIND_ACCESSIBILITY_SERVICE` contract.
* Uses `@xml/external_ai_accessibility_config`.
* The XML restricts events to the supported Gemini packages with `android:packageNames`.
* The service code additionally ignores events unless an IDEaz external-AI request is actively in flight and the event package matches that request's resolved target package.
* Purpose: submit IDEaz's explicitly prepared prompt into the supported installed AI app and capture the newly completed response. It is not the future arbitrary-app inspection service described by the Android-target roadmap.

### `ExternalAiOverlayService`

* `android:exported="false"`.
* `android:foregroundServiceType="specialUse"` with a special-use description property.
* Purpose: draw non-touchable IDEaz chrome around the live external AI app for the GitHub-only frame presentation modes.
* Requires `SYSTEM_ALERT_WINDOW`; Settings shows that permission only in builds where the service exists.

## Google Play manifest

The `play` build type inherits only the common manifest. It does **not** declare:

* `SYSTEM_ALERT_WINDOW`
* `FOREGROUND_SERVICE_SPECIAL_USE`
* `IdeazAccessibilityService`
* `ExternalAiOverlayService`
* Gemini package-visibility queries used by the GitHub host

The shared Settings UI is gated by `BuildConfig.EXTERNAL_AI_AUTOMATION`, so Play users do not see controls for permissions or services their build does not contain.

## SettingsScreen

The Settings screen contains:

* encrypted settings import/export;
* project backup import/export;
* signing configuration;
* provider/API credentials;
* AI assignments;
* common notification permission;
* GitHub-only external-AI presentation/permission controls when `EXTERNAL_AI_AUTOMATION` is true;
* preferences, theme and log level.

For GitHub builds the external-AI section exposes the persisted window mode (`IDEaz frame`, `Compact frame`, `Freeform window`, `Adjacent / embedded`, or `Fullscreen`) and the overlay/accessibility grants needed by the relevant modes.

## Invisible backend infrastructure

### `MainViewModel`

Coordinates project lifecycle, preview, chat, edit checkpoints/review, git and long-running operations. AI client construction is centralized in `AiAdapterFactory`.

### `SettingsViewModel`

Owns preferences and secure credentials. `AiModel.providerKey` names a provider's API credential; `AiModel.requiredKey` is the distribution-aware setup requirement. Thus Play's Gemini assignment requires an AI Studio key while GitHub's installed-app Gemini route may satisfy setup without one.

### `CrashReportingService`

Non-exported service in a separate process so opted-in fatal/non-fatal issue reporting can survive failure of the main process.

### `FileProvider`

The provider is common because explicit file sharing itself is policy-safe. The installed-Gemini bridge is what consumes it in GitHub builds; Play retains no accessibility automation path merely because the provider exists.

## Security boundary

The distribution split must remain mechanical rather than a runtime promise:

* privileged GitHub-only permissions/services belong in `src/debug` and `src/release`, not `androidMain`;
* the Play build type must keep `EXTERNAL_AI_AUTOMATION=false` and `EXTERNAL_AI_OVERLAY=false`;
* Play publishing must build `bundlePlay`;
* CI must compile both the GitHub and Play variants so either manifest cannot quietly rot.
