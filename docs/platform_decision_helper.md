# Decision Helper: Web Design vs. React Native

This document outlines the trade-offs between starting with **Web Design** versus **React Native** for the next phase of IDEaz.

## Option A: Web Design (HTML/CSS/JS)

### Pros
*   **Simplicity:** No complex compilation step is strictly required. Changes can be applied by simply reloading the `WebView`.
*   **"Post-Code" Feasibility:** modifying the DOM (Document Object Model) at runtime is a mature and standard practice. Injecting a script to select elements and change styles is very straightforward.
*   **Instant Feedback:** The iteration cycle is nearly zero-latency.
*   **Foundation:** Building the "Select Element -> AI Modify -> Reload" loop for Web is the easiest proof-of-concept for the non-Android platforms.

### Cons
*   **Scope Deviation:** It is not strictly "Mobile App Development" in the native sense.
*   **Tooling Differences:** Debugging involves Web Inspectors rather than Logcat/Android tools.

### Verdict
**Start here if** you want to quickly validate the "Post-Code" UX on a non-Android platform without getting bogged down in build tool complexity.

---

## Option B: React Native

### Pros
*   **Alignment:** It fits the core mission of "Mobile IDE". The result is a real Android app.
*   **Shared Concepts:** Like Android, it has a view hierarchy that can be inspected.
*   **High Value:** Enabling React Native support significantly increases the IDE's utility for app developers.

### Cons
*   **Complexity:** Running the Metro bundler (or equivalent) on-device is non-trivial. You need a JavaScript engine (Hermes/JSC) and a way to package the bundle.
*   **Bridge Overhead:** Inspecting UI elements requires crossing the Native <-> JS bridge to map a visual element back to its React component code.

### Verdict
**Start here if** you are ready to tackle a complex build engineering challenge to provide a high-value mobile development feature.

## Recommendation

**Recommendation: Start with Web Design.**

**Reasoning:** The "Post-Code" editing model is the unique selling point of IDEaz. Implementing it for Web is technically lower risk and will allow us to refine the UX (selection, AI prompting, applying changes) before tackling the heavy build engineering required for React Native. Once the "Web Inspection & Edit" loop is robust, applying those lessons to React Native's more complex environment will be easier.
