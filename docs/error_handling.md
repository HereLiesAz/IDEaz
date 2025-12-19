# Error Handling & Crash Reporting

## Overview
IDEaz implements a robust, automatic error handling workflow for both the IDE itself and the user projects it creates. This ensures that failures are immediately reported to the AI Agent (Jules) with full context, enabling rapid self-repair.

## Mechanisms

### 1. Fatal Crashes
- **Component:** `CrashHandler` (JVM UncaughtExceptionHandler).
- **Behavior:** Intercepts fatal exceptions.
- **Action:** Starts `CrashReportingService` in a separate process (`:crash_reporter`).
- **Reporting:**
    - Creates a new Jules Session (Source: `HereLiesAz/IDEaz` or Project Repo).
    - Prompt: "CRASH REPORT from {User}: {StackTrace}".
    - Includes **Mandatory Instruction** for code quality.

### 2. Non-Fatal Errors
- **Component:** `ErrorCollector` (Singleton).
- **Behavior:** Collects exceptions from ViewModels, Coroutines, and other logic.
- **Deduplication:** Repeats of the same error are capped at 3 times.
- **Batching:** Errors are flushed and reported when the user navigates between screens (`IdeNavHost`).
- **Filtering:** "Noise" errors (Cancellation, etc.) are ignored.

### 3. User Project Injection
- **Mechanism:** `ProjectInitializer` and `RepoDelegate`.
- **Trigger:** Project Load, Clone, or Creation.
- **Action:** Injects `CrashReporter.kt` into the user's project source tree (`utils/`).
- **Configuration:** API Keys and Repository info are "baked in" to the source file to minimize runtime dependencies.
- **Integration:** Requires manual or heuristic hook into `MainActivity.onCreate` (currently automated via `ProjectInitializer` stub).

## Mandatory Instruction
All error reports include this instruction to the Agent:
> "You are required to get a perfect code review, no blocks or nitpicks allowed in it. Then you must get a passing build with tests. Once you have it, you must take the time to go get all of the documentation up to date before committing."
