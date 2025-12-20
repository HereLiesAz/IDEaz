## 2025-12-19 - [HttpLoggingInterceptor Sensitive Headers]
**Vulnerability:** API keys and Tokens were being logged to logcat because `HttpLoggingInterceptor` was set to `Level.BODY` without redacting sensitive headers like `Authorization` and `X-Goog-Api-Key`.
**Learning:** When using `HttpLoggingInterceptor` for debugging, always ensure that sensitive headers are explicitly redacted using `redactHeader()`, especially if `Level.BODY` or `Level.HEADERS` is used.
**Prevention:** Audit all uses of `HttpLoggingInterceptor` and ensure a `redactHeader()` call exists for any header carrying secrets.

## 2025-05-24 - [Insecure Broadcast]
**Vulnerability:** Sensitive application logs were being broadcast globally via `sendBroadcast(Intent)` without package restriction, allowing malicious apps to intercept them.
**Learning:** `Context.sendBroadcast()` is global by default.
**Prevention:** Always use `intent.setPackage(context.packageName)` for internal broadcasts or use `LocalBroadcastManager` (deprecated but safe) or `SharedFlow`.
