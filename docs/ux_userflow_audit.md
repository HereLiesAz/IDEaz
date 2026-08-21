# UX User-flow Production-readiness Audit

**Audit date:** 2026-08-14<br>
**Scope:** Every user-visible flow reachable from `MainActivity`, including project onboarding, PWA preview and selection, conversational editing, build/deploy, Git, files, settings, credentials, permissions, update/install, recovery, and the deferred Android-target path.<br>
**Method:** Static walkthrough of the Compose navigation graph, rail actions, dialogs, state/delegate calls, manifest permissions, and existing automated tests. This is a code-backed heuristic assessment, not device usability research. No claim below is based only on older documentation.

## 1. Verdict

**Status: not production-ready.** The PWA happy path is credible and incomplete target loops are now gated, but the app still requests policy-sensitive permissions too broadly, lacks durable task recovery and consistent operation feedback, and has almost no automated UI-flow coverage. Shipping this as production software would turn users into unpaid chaos engineers.

| Dimension | Score | Production bar | Assessment |
|---|---:|---:|---|
| Discoverability and onboarding | 2/5 | 4/5 | Setup is visible, but there is no guided readiness checklist, resumable onboarding, or clear product-mode choice. |
| Task completion | 3/5 | 4/5 | The PWA path exists and incomplete target loops are now release-gated rather than presented as usable. |
| Feedback and system status | 2/5 | 4/5 | Logs and progress exist, but many operations lack scoped success/failure, retry, cancellation, or final state. |
| Error prevention and recovery | 2/5 | 4/5 | Some destructive actions confirm; Git, initialization, import, and long-running work remain inconsistent. |
| Accessibility | 2/5 | 4/5 | Some semantics exist; decorative icons, text-only click targets, dynamic announcements, focus, and screen-reader flows are not systematically covered. |
| Privacy, trust, and permissions | 1/5 | 5/5 | Accessibility, overlay, package visibility, installation, and all-files access need purpose-scoped disclosure and minimization. |
| Responsive/device behavior | 2/5 | 4/5 | Landscape is passed to AzNavRail, but dense screens use fixed heights and button rows without compact-width strategy. |
| Test confidence | 1/5 | 4/5 | Logic tests are broad; the only instrumentation test is the generated example and no end-to-end UX flow is automated. |

**Overall: 1.9/5.** This is the arithmetic mean of the eight current dimension scores (15 ÷ 8 = 1.875, rounded to one decimal). Production remains blocked until all P0 items and the release evidence in §6 are complete.

### Remediation status

- **P0.1 mitigated:** PWA is now the only selectable production target. Other recognized types remain detectable for metadata integrity but cannot be created or initialized and are not mounted into an unfinished App View. Existing out-of-scope projects receive an explicit unavailable-release message and remain unmodified. The complete Android loop remains Phase 2 work.
- **P0.2 partially mitigated:** `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`, and `PACKAGE_USAGE_STATS` are removed entirely — every gated call site was already a Storage Access Framework picker that needed none of them, verified individually rather than assumed. `QUERY_ALL_PACKAGES` is replaced with a scoped `<queries>` manifest block naming only the two packages ever actually looked up. Dead code removed (`PermissionUtils.kt`, an unused `checkOverlayPermissions()`), and the remaining Settings permission rows (overlay, notifications, install-unknown-apps, accessibility) now carry rationale subtitles. Not closed: overlay, accessibility, and `FOREGROUND_SERVICE_MEDIA_PROJECTION` declarations remain in the manifest — confirmed unreachable under the shipped PWA-only scope, but kept as active Phase 2 scaffolding pending a dedicated cleanup pass rather than removed.
- **P0.3 closed:** the initialization/CI-regeneration functions (`ensureWorkflow`, `ensureSetupScript`, `ensureAgentsSetupMd`, `ensureVersioning`, `injectCrashReporting`) now return the exact project-relative paths they wrote instead of a bare `Boolean`, and `GitManager.addPaths()` stages precisely those paths instead of `git add .`. The one bare manual trigger (`GitScreen.kt`'s "Regenerate CI Files" button) now shows a confirmation dialog with repository, branch, and the exact affected paths before pushing. The four automatic call sites (deploy, repo selection, save & initialize, fork) were deliberately left without their own confirmation, since they already fire from an explicit user action.
- **P0.4 closed, pending live-provider verification:** the checkpoint/review/approve/undo flow the on-device path already had now covers all three cloud adapters too (Gemini, Anthropic, and every OpenAI-compatible provider). The approval type moved from local-only naming to a `source: String` field identifying which provider is under review. The overlay's contextual-prompt path (`AIDelegate.startContextualAITask`) auto-approves rather than crashing, since it has no review UI of its own to route through. None of the cloud-adapter orchestration has been exercised against a live API or physical device in this environment — only traced and unit-tested at the shared-primitive level.
- **P0.5 foundation only, delegate migration not started:** a new `OperationState`/`OperationController` primitive (`models/OperationState.kt`, `utils/OperationController.kt`) provides the sealed-interface Queued/Running/Succeeded/Failed/Cancelled state machine this finding calls for, unit-tested in isolation. It is not yet wired into any of the eight candidate call sites (clone, create/init, AI, build, deploy, download, update, Git) — each still tracks its own state with the prior ad-hoc `Job?`/`Boolean`/log-string mix this finding exists to replace. Migrating them is still open work.

## 2. Flow inventory and assessment

| ID | User goal and route | Entry → success | Current assessment | Readiness |
|---|---|---|---|---|
| F01 | First launch | Launcher → Project/Setup | A Gemini bridge dialog may appear, but no product tour, privacy primer, project-type decision, or persistent setup checklist exists. | Blocked |
| F02 | Configure AI/GitHub | Project requirement dialog or Rail/Settings → save credentials/provider assignment | Settings contains the controls, but it is a long undifferentiated page. Save uses transient toasts; credential validity is not confirmed in place. | High risk |
| F03 | Grant permissions | Action-time requirement → Android settings → return | Storage prompting is partly lazy, but return-state guidance is weak and permissions are not presented as a purpose/minimum-access sequence. | Blocked |
| F04 | Create project | Setup/Create → fields → Create & Save | Mandatory prompt and GitHub token gates exist. App/user/branch/package validation, duplicate-name handling, rollback, and actionable failure recovery are not evident in the UI. | High risk |
| F05 | Clone/fork project | Clone → repo or URL → Setup | Repo browsing and refresh exist. URL validation, progress cancellation, offline/empty/error distinctions, and retry are absent from the surface. | High risk |
| F06 | Load local/external project | Load → project/directory → Setup | Empty state and delete confirmation exist. All-files access is requested before the system document picker despite SAF being designed for scoped access; import errors have no durable recovery surface. | Blocked |
| F07 | Initialize project | Setup → Save & Initialize | The action can force-update and push CI files. There is no preflight summary, changed-file preview, explicit destructive-network confirmation, cancellation, or partial-success recovery. | Blocked |
| F08 | Open PWA preview | Rail/Build or initialized project → WebProjectHost | Build opens console and validates the web target. Missing/invalid entry points need an actionable empty/error screen rather than log archaeology. | High risk |
| F09 | Interact/select element | Rail mode toggle → tap/drag → contextual prompt | Core mechanics exist. Toggle labels and checked state are ambiguous, selection has no tutorial/escape affordance documented in the UI, and no accessibility alternative is proven. | High risk |
| F10 | Prompt AI with context | Selection or Prompt → send → response/apply/reload | Conversation, attachments, loading, and contextual overlay exist. There is no visible cancel, retry, edit/resend, provider identity derived from the active provider, diff approval, undo, or failed-tool recovery. | Blocked |
| F11 | Prompt without context | Console AI tab → Contextless Prompt | Same recovery gaps as F10; “Contextless” is implementation jargon, not a user goal. Draft persistence across navigation/process death is absent. | High risk |
| F12 | Build | Rail/Build → progress/log → artifact/preview | A global progress dialog is non-dismissible and offers neither cancel nor backgrounding. Success/failure actions are scattered between dialogs, toasts, logs, and state. | Blocked |
| F13 | Deploy PWA | Rail/Deploy → remote host | One-tap deploy has no confirmation/target/branch summary, no credential preflight, and no durable success receipt or rollback guidance. | Blocked |
| F14 | Git commit/sync | Rail/Git → commit/fetch/pull/push/stash/switch | Basic operations and dirty-branch warning exist. Buttons are enabled without readiness state; risky pull/push/unstash and CI regeneration lack previews, conflict recovery, and operation-specific progress. | Blocked |
| F15 | Browse/edit files | Rail/Files → directory → file → edit/save | Create/rename/delete validation and confirmations exist. Back navigation, unsaved-change protection, binary/large-file handling, search, save state, and error recovery need device validation and UI tests. | High risk |
| F16 | Backup/import settings | Settings → encrypted export/import | Password dialogs exist. There is no strength guidance, confirm-password step, explicit contents summary, overwrite preview, recovery for wrong/corrupt files, or post-import restart/reload explanation. | High risk |
| F17 | Configure signing | Settings → keystore import/save/reset | Controls exist, but destructive reset and invalid-keystore states need stronger confirmation, validation, and recovery. Secrets must never be echoed or logged. | Blocked |
| F18 | Manage local models | Settings → On-device Models → download/select/delete | Availability and downloads exist. Storage/network preflight, pause/resume, checksum/integrity state, model-size consequences, and recoverable partial downloads require product evidence. | High risk |
| F19 | Update IDEaz/install APK | Settings or artifact dialog → download/pick → Android installer → relaunch | Version comparison and downgrade warning exist. Unknown-sources denial, installer cancellation, signature mismatch, download integrity, post-install return, and launch failure need a coherent state machine. | Blocked |
| F20 | Recover from failures | Any operation → logs/toast/dialog/reporting | Infrastructure reporting exists, but user-facing errors do not share a stable model with plain-language cause, retained details, retry, copy/export, and support route. | Blocked |
| F21 | Android target loop | Project type Android → App View/build/install/select | Android is recognized for existing-repository integrity but release-gated from creation, initialization, and App View until Phase 2 completes the loop. | Mitigated |
| F22 | Resume after interruption | Rotate, background, kill, reconnect → continue task | Local Compose state and transient toasts dominate several flows. Evidence of process-death restoration, durable queues, idempotency, and interrupted-operation reconciliation is absent. | Blocked |

## 3. Prioritized findings

### P0 — release blockers

1. **Hide or complete the Android target path — mitigated.** PWA is now the sole selectable production target; Android is blocked from creation and initialization and never mounted into the unfinished App View. Keep this gate until Phase 2 passes its end-to-end acceptance tests.
2. **Minimize privileged permissions and add just-in-time disclosure — partially mitigated.** `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`, and `PACKAGE_USAGE_STATS` are removed, and `QUERY_ALL_PACKAGES` is replaced with a scoped `<queries>` block naming only the two packages actually looked up; the remaining permission rows in Settings now carry rationale subtitles. Still open: overlay, accessibility, package installation, media projection, and special-use foreground service declarations remain in the manifest — each still needs its own current user-facing purpose, narrow trigger, denial path, and documented necessity, or removal if not required by the shipped PWA scope.
3. **Make initialization and CI regeneration reviewable — closed.** The one bare manual trigger now shows repository, branch, exact affected paths, and an explicit confirmation before force-pushing generated files, computed from a preview function rather than assumed; the underlying regeneration functions report exactly what they wrote and stage only that. The four call sites that fire automatically from an already-explicit user action were left without a second confirmation by design.
4. **Put AI edits behind inspect/approve/undo — closed, pending live-provider verification.** Proposed file changes now go behind a checkpoint/review/approve/undo flow — extended this session from the on-device path to all three cloud adapters (Gemini, Anthropic, OpenAI-compatible) — with cancel, reject, and restore-to-pre-edit snapshot. Not yet exercised against a live API or physical device in this environment.
5. **Unify long-running operation state — foundation only.** A shared, unit-tested `OperationState`/`OperationController` primitive now exists with the queued/running/succeeded/failed/cancelled model this finding calls for, but it has not been wired into any of clone, create, initialize, AI, build, deploy, download, update, or Git yet — each still carries its own ad-hoc `Job?`/`Boolean`/log-string state, with none of the progress, cancellation, retry, or process-restoration semantics this finding requires. Migrating the eight call sites onto it remains open work.
6. **Add production error UX.** Replace toast/log-only dead ends with an actionable error surface: plain-language summary, affected task, next step, retry, copy sanitized details, and report/support action. Never lose the failure when a screen changes.
7. **Establish automated critical-flow coverage.** Add Compose/instrumentation journeys for first run, denied permissions, create/clone/load, PWA select/prompt/apply/undo, build success/failure, Git conflicts, file unsaved changes, credential import failure, update failure, rotation, and process recreation.
8. **Define release accessibility gates.** Verify TalkBack traversal, keyboard/switch access, focus restoration after dialogs/navigation, 48dp targets, headings/labels, dynamic announcements, contrast, font scale to 200%, and reduced-motion behavior on supported API/device classes.

### P1 — required before general availability

1. Replace the monolithic Settings page with searchable grouped destinations and a “Setup status” summary.
2. Validate project/repository URL, app name, branch, package name, token scopes, and provider credentials before beginning work; keep errors next to the field.
3. Add explicit empty/loading/error/offline states to repo lists, project lists, branches, history, logs, preview, sessions, and model catalog.
4. Add unsaved-draft and unsaved-file guards; persist prompt drafts and navigation-relevant task state across process death.
5. Make Git actions state-aware. Disable impossible actions, display current branch/ahead/behind/conflict state, preview affected commits/files, and guide conflict resolution.
6. Make deploy/build outcomes traceable: target, branch, commit SHA, start/end time, artifact/version, destination URL, and “view/copy/retry” actions.
7. Replace fixed-height nested Git lists and rigid button rows with adaptive layouts tested at compact width, landscape, split screen, large text, and IME-visible states.
8. Use provider-neutral chat labels. The chat currently labels every model response “Gemini” even though multiple providers can be assigned.
9. Explain destructive and privacy-sensitive actions in user language, not implementation names such as “Contextless,” “Regenerate CI Files,” or raw service names.
10. Add analytics that measure funnel completion and failure categories without collecting prompt, code, credential, file-path, or accessibility content.

### P2 — hardening and polish

1. Add first-run sample project and a replayable selection tutorial.
2. Add recent projects, recent tasks, searchable logs, and clear notification deep links.
3. Support comparison/rollback history for AI edit rounds.
4. Add localization and RTL verification; do not ship user-facing strings embedded only in Kotlin.
5. Run moderated usability sessions with novice and expert cohorts, followed by accessibility testing with disabled users.

## 4. Cross-flow interaction standards

Every production action must satisfy this contract:

1. **Before:** explain consequence; validate prerequisites; identify target; confirm destructive/remote effects.
2. **During:** show named task, determinate progress when measurable, current step, elapsed state, background behavior, and cancel policy.
3. **Success:** state exactly what changed and where; expose the primary next action and a durable receipt.
4. **Failure:** retain input; state cause without blame; expose retry/recovery; preserve sanitized diagnostics.
5. **Interruption:** restore or reconcile state after rotation, process death, connectivity loss, provider timeout, and app upgrade.
6. **Accessibility:** move focus predictably, announce state changes, support non-touch operation, preserve semantic order, and never encode status by color alone.
7. **Privacy:** request the least privilege at the latest responsible moment and explain what is read, written, transmitted, retained, and revocable.

## 5. Recommended delivery sequence

1. **Scope the release:** ship PWA-only or complete Android; remove/gate every unreachable promise.
2. **Trust pass:** permission minimization, disclosure, credentials, signing, data handling, and store-policy review.
3. **Task-state foundation:** durable operation model and standardized progress/result/error components.
4. **Core loop safety:** AI diff approval/undo, initialization preview/rollback, deploy/build receipts.
5. **Recovery pass:** offline, denial, conflict, timeout, cancellation, process death, and update/install failures.
6. **Accessibility/adaptive pass:** semantics, focus, announcements, large text, compact/landscape, keyboard/IME.
7. **Evidence pass:** automated journeys, physical-device matrix, usability research, accessibility audit, dogfood telemetry review.

## 6. Production release evidence

Release approval requires artifacts, not optimism:

- All P0 findings closed with linked acceptance tests.
- `./gradlew build` passes from a clean checkout.
- Compose/instrumentation suite passes on the minimum SDK, target SDK, current Android, phone, tablet/large screen, and at least one low-memory device profile.
- Critical flows pass with network offline/slow/flapping, API 401/403/429/5xx, Git conflicts, disk full, permission denial/revocation, process death, and interrupted install/update.
- TalkBack and switch/keyboard audit passes; font scale 200% and display-size extremes retain all primary actions.
- Security/privacy review approves permission inventory, backup behavior, credential storage/export, logs, crash reports, WebView bridge, downloads, APK verification, and external intents.
- Google Play policy review approves or eliminates restricted permissions before store submission.
- No Sev-0/Sev-1 defects; every accepted lower-severity defect has owner, rationale, and expiry.
- Funnel metrics meet targets defined before beta: onboarding completion, project activation, first successful prompt, edit acceptance/undo, build/deploy completion, crash-free users, and task-recovery success.

## 7. Audit limitations

This audit did not claim pixel-perfect, TalkBack, performance, network, or physical-device behavior because static code cannot prove those properties. The next audit must execute the journeys in §6, capture screen/video evidence, include accessibility tooling plus manual assistive-technology review, and record defect IDs against this flow inventory.
