# Changelog

Dates are when the change landed. Version numbers are assigned by the release pipeline.

## 2026-10-23

### Privacy

- **Removed all automatic error reporting.** IDEaz now sends no crash reports, error logs or build
  logs anywhere. Deleted `CrashHandler`, `CrashReportingService` (and its `:crash_reporter` manifest
  entry), `ErrorCollector`, `GithubIssueReporter`, the build-failure reporting in `MainViewModel`, the
  first-run "Report crashes" dialog, and the "Report IDE errors" Settings toggle. The "Auto-send build
  failures" Settings toggle went with `handleBuildFailure`, the only code that read it. Leftover
  preference values are deleted on first launch.
- The only data leaving the device is what you send to the AI providers and the GitHub / Cloudflare
  services you configure. New [`PRIVACY.md`](PRIVACY.md), a website privacy page, and
  [`docs/play-data-safety.md`](docs/play-data-safety.md); `docs/privacy-license-telemetry-review.md`
  and the error-handling docs now match the code.

### AI providers

- **Automatic provider switching.** When a provider reports an exhausted quota or rate limit (HTTP 429,
  `insufficient_quota` / `quota_exceeded`, or a `Retry-After` over 60 seconds), IDEaz marks it as cooling
  down until its reset time and resends the same request to the next provider: keyless ones first
  (Kilo, LLM7, OVHcloud), then any provider you saved a key for. Each provider is tried at most once per
  request. The chat shows one line when it switches ("X hit its daily limit. Switched to Y.") and, when
  everything is exhausted, one plain line with the earliest reset time in hours and minutes. The raw
  provider error is behind a Details toggle instead of printed in the chat.
- A short `Retry-After` (60 seconds or less) keeps the existing retry policy.
- `RetryInterceptor` no longer sleeps through a `Retry-After` longer than 60 seconds.
