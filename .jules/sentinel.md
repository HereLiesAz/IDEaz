## 2025-12-19 - [HttpLoggingInterceptor Sensitive Headers]
**Vulnerability:** API keys and Tokens were being logged to logcat because `HttpLoggingInterceptor` was set to `Level.BODY` without redacting sensitive headers like `Authorization` and `X-Goog-Api-Key`.
**Learning:** When using `HttpLoggingInterceptor` for debugging, always ensure that sensitive headers are explicitly redacted using `redactHeader()`, especially if `Level.BODY` or `Level.HEADERS` is used.
**Prevention:** Audit all uses of `HttpLoggingInterceptor` and ensure a `redactHeader()` call exists for any header carrying secrets.

## 2025-05-24 - [Insecure Broadcast]
**Vulnerability:** Sensitive application logs were being broadcast globally via `sendBroadcast(Intent)` without package restriction, allowing malicious apps to intercept them.
**Learning:** `Context.sendBroadcast()` is global by default.
**Prevention:** Always use `intent.setPackage(context.packageName)` for internal broadcasts or use `LocalBroadcastManager` (deprecated but safe) or `SharedFlow`.

## 2025-12-21 - [Zip Slip in Build Service]
**Vulnerability:** `BuildService.startRemoteBuild` extracted artifacts without validating that the destination path was within the target directory, allowing potential file overwrite via path traversal (Zip Slip).
**Learning:** `ZipInputStream` entries can contain `../` sequences. Always validate `entry.name` or the resulting `canonicalPath` / `toPath().normalize()`.
**Prevention:** Use `destinationPath.startsWith(basePath)` check after normalizing both paths when extracting zips.

## 2025-05-25 - [WebView File Access Vulnerability]
**Vulnerability:** WebView configured with `allowUniversalAccessFromFileURLs = true`.
**Learning:** This setting allows JavaScript in a file:// URL to read ANY other file on the device that the app has access to (e.g. shared preferences with API keys), bypassing the Same-Origin Policy.
**Prevention:** Always set `allowUniversalAccessFromFileURLs = false` (default) unless absolutely necessary and scoped carefully.
