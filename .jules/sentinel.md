## 2025-12-19 - [HttpLoggingInterceptor Sensitive Headers]
**Vulnerability:** API keys and Tokens were being logged to logcat because `HttpLoggingInterceptor` was set to `Level.BODY` without redacting sensitive headers like `Authorization` and `X-Goog-Api-Key`.
**Learning:** When using `HttpLoggingInterceptor` for debugging, always ensure that sensitive headers are explicitly redacted using `redactHeader()`, especially if `Level.BODY` or `Level.HEADERS` is used.
**Prevention:** Audit all uses of `HttpLoggingInterceptor` and ensure a `redactHeader()` call exists for any header carrying secrets.
