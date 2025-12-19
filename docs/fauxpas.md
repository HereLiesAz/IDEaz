# Faux Pas (Anti-Patterns)

## 1. The "Hidden Error"
**Bad:** `catch (e: Exception) { Log.e("Tag", "Error") }`
**Good:** Report the error to the user (`viewModel.updateMessage`) or the collection service (`ErrorCollector`). Silent failures make debugging impossible.

## 2. The "Main Thread IO"
**Bad:** Reading files or network on the Main Thread.
**Consequence:** ANR (App Not Responding).
**Fix:** Use `Dispatchers.IO` or `withContext(Dispatchers.IO)`.

## 3. The "Hardcoded Path"
**Bad:** `File("/sdcard/Download/MyProject")`
**Consequence:** Permission denied on Android 11+.
**Fix:** `context.filesDir.resolve("MyProject")`.

## 4. The "Infinite Loop"
**Bad:** Polling `listActivities` without a delay or exit condition.
**Consequence:** Battery drain, API quota exhaustion.
**Fix:** Add `delay(3000)` and a `maxAttempts` counter.

## 5. The "Context Leak"
**Bad:** Passing `Activity` context to a Singleton or long-lived ViewModel.
**Consequence:** Memory leak.
**Fix:** Use `Application` context or unbind properly in `onCleared()`.

## 6. The "Unverified Commit"
**Bad:** Committing code without running a build.
**Consequence:** Broken build for the user.
**Fix:** Always run `./gradlew :app:assembleDebug`.
