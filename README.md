<h1 style="text-align:center"><b>[</b>oo<b>]</b> <br>IDEaz</h1>

This isn't no-code. This is not vibe coding. And this sure as hell ain't straight-up coding.
This is what every emulator, visual preview, drag and drop WYSIWYG environment was leading up to.

### Development that feels like it's just you and your IDEaz — The Post-Code IDE.

**Philosophy**

IDEaz adopts a "Post-Code" philosophy. The primary workflow is visual: interact
with your running app, tap what you want to change, and prompt the AI to make it
happen. The IDE handles the code, the git operations, and the reload.

```
Open project → preview it → tap an element → describe the change
             → AI edits the source → review the diff → approve → reload
```

A **File Explorer** and **Code Editor** are included for debugging, verification,
and manual intervention when the AI gets stuck. They are tools, not the
workspace.

**How tapping an element finds the right file**

This is the hard part of the whole idea, and it falls out of the preview
pipeline. IDEaz has no bundler on-device, so it transpiles your project's source
in the browser with Babel — and does so with `jsx-source` enabled, which stamps
every element with the file and line that produced it. Tap a button, and the AI
is handed `src/App.jsx:42` instead of a CSS selector and a guess.

**Targets**

IDEaz is a Kotlin/Compose Multiplatform app:

* **Android** — the phone IDE. The original idea, and still the point.
* **Desktop (JVM)** — the same app on a laptop, via `./gradlew :app:run`. This is
  not a second product. It exists so the app can actually be run and tested
  without a handset.

**Editable projects**

Any web project with an entry point IDEaz can mount and transpile. The only
question asked of a directory is "can we preview this?" — an `index.html` in one
of a few conventional places, or a `package.json`. There is no project-type
picker, because there is no second kind of project.

React is the shape the pipeline is built for, and the bundled starter is a
React/Vite app: `jsx-source` is what makes a tap resolve to a file and line.
Plain HTML previews fine and falls back to a `data-ideaz-source` attribute, then
to a selector — the AI is told which it is getting.

**AI**

Eight hosted providers sit behind three adapter families — Gemini, Claude, and
one OpenAI-compatible client covering OpenAI, DeepSeek, Groq, Cerebras, Hugging
Face and Mistral. Model ids are pinned and overridable in Settings.

The **Google Play build** is API-only and uses BYO provider keys. The **GitHub
APK** can additionally use a supported installed Gemini app after the user
explicitly enables IDEaz's package-scoped accessibility bridge; no AI Studio key
is required for that path, though a saved key becomes its automatic API fallback.

Every mutation still goes through the same contract: the AI writes behind a
checkpoint, you see what changed, and nothing reaches the preview until you
approve it.

**Git**

Every project is a git repository and git is the source of truth. A new project
is `git init`-ed and gets an initial commit before it has any remote.

You do **not** need a GitHub account to start. Create a project, scaffold it,
edit it with the AI, and commit locally. **Deploy** is the action that asks for a
GitHub token, and it creates the repository for you on first publish.

---

See [`docs/architecture.md`](docs/architecture.md) for how it fits together.
