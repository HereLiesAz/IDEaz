# Jules Integration (API & CLI)

The IDEaz application integrates with the Jules AI Coding Agent using a hybrid approach. While the `Jules Tools CLI` is packaged with the application, the primary interaction mechanism on Android is now the `Jules API` client (`JulesApiClient`) due to execution stability issues with the CLI binary on some devices.

## Jules API Client (`JulesApiClient`)

The `JulesApiClient` is a Retrofit-based Kotlin implementation of the Jules API. It mirrors the structure of the official TypeScript SDK (`jules-api-node`).

### Key Features
*   **Singleton Architecture**: Uses a thread-safe, lazy-initialized Singleton pattern for the `Retrofit` client to ensure optimal performance and resource usage.
*   **Testability**: Supports configuring the `baseUrl` (observable via `@VisibleForTesting`), allowing comprehensive unit testing with `MockWebServer`.
*   **Session Management**: Creates sessions with context (prompt, source repository) via `createSession`.
*   **Activity Polling**: Provides access to `listActivities` and `sendMessage`, enabling polling-based asynchronous workflows to retrieve plans, patches, and messages.
*   **Source Listing**: Fetches available repositories from the user's account via `listSources`.
*   **Robustness**: Handles API authentication via `AuthInterceptor`, automatic retries via `RetryInterceptor`, and standard HTTP error handling.

### Usage in App
*   **MainViewModel**: Uses `JulesApiClient` to:
    *   Fetch the list of owned sources for the Project Screen.
    *   Create new sessions for contextual and contextless prompts.
    *   Poll for "Patch" activities to automatically apply code changes.
*   **Integration**: The app constructs valid `SourceContext` strings (e.g., `sources/github/{user}/{repo}`) to ensure correct API routing.

---

## Jules Tools CLI (Legacy / Reference)

*Note: The CLI integration (`JulesCliClient`) is preserved in the codebase but is currently bypassed in favor of the API client for core workflows.*

Jules Tools is a lightweight command-line interface (CLI) for interacting with Jules, Google’s autonomous AI coding agent. It allows you to manage coding sessions, inspect progress, and integrate Jules into your existing development workflows and scripts directly from your terminal.

Think of Jules Tools as both a command surface and a dashboard for your coding agent, designed to keep you in your flow without needing to switch to a web browser.

## Installation

[Section titled “Installation”](#installation)

To get started, install the tool globally using npm or pnpm.

```
npm install -g @google/jules
```

Once installed, the jules command will be available in your terminal.

### Authentication

[Section titled “Authentication”](#authentication)

Before you can use the tool, you must authenticate with your Google account.

### Login

[Section titled “Login”](#login)

```
jules login
```

This command will open a browser window to guide you through the Google authentication process.

### Logout

[Section titled “Logout”](#logout)

To log out from your account:

```
jules logout
```

## Usage

[Section titled “Usage”](#usage)

The CLI is built around commands and subcommands. You can get help for any command by using the -h or —help flag.

```
# Get general helpjules help
# Get help for a specific command (e.g., remote)jules remote --help
```

### Global Flags

[Section titled “Global Flags”](#global-flags)

*   `-h`, `--help`: Displays help information for jules or a specific command.

*   `--theme <string>`: Sets the theme for the terminal user interface (TUI). Options are `dark` (default) or `light`.


Example: `jules --theme light`

### Available Commands

[Section titled “Available Commands”](#available-commands)

`version`

Shows the currently installed version of the Jules Tools CLI.

```
jules version
```

`remote`

The `remote` command is the primary way to interact with Jules sessions running in the cloud. It has several subcommands.

`remote list` Lists your connected repositories or active sessions.

*   `--repo`: Flag to list all repositories connected to Jules.

*   `--session`: Flag to list all your remote sessions.


_Examples:_

```
# List all connected repositoriesjules remote list --repo
# List all active and past sessionsjules remote list --session
```

`remote new`

Creates a new remote session to delegate a task to Jules.

Jules can automatically infer the repository from your current working directory, so you can often omit the `--repo` flag.

*   `--repo <repo_name>`: Specifies the repository for the session (e.g., torvalds/linux or . for the current directory’s repo).

*   `--session "<prompt>"`: A string describing the task for Jules to perform.

*   `--parallel <number>`: Starts multiple parallel sessions to work on the same task.


_Example:_

```
# Start a new session to write unit tests in the 'torvalds/linux' repojules remote new --repo torvalds/linux --session "write unit tests"
```

`remote pull`

Pulls the results (e.g., code changes) from a completed session.

*   `--session <session_id>`: The ID of the session you want to pull.

_Example:_

```
# Pull the results for session ID 123456jules remote pull --session 123456
```

`completion`

Generates an autocompletion script for your shell (e.g., bash, zsh) to enable tab completion for jules commands.

```
# Generate completion script for bashjules completion bash
```

## Interactive Dashboard (TUI)

[Section titled “Interactive Dashboard (TUI)”](#interactive-dashboard-tui)

For a more interactive, visual experience, you can launch the Terminal User Interface (TUI) by running the jules command without any arguments.

```
jules
```

The TUI provides a dashboard view of your sessions, a side-by-side diff viewer for reviewing changes, and guided flows for creating new ones, similar to the web UI.


# Practical Examples & Scripting

Jules Tools is designed to be scriptable and can be composed with other command-line tools.

Below are some examples of Jules Tools in action:

**1\. Create sessions from a TODO.md file:**

Assign each line item from a local TODO.md file as a new session in the current repository.

```
cat TODO.md | while IFS= read -r line; do  jules remote new --repo . --session "$line"done
```

**2\. Create a session from a GitHub Issue:**

Pipe the title of the first GitHub issue assigned to you directly into a new Jules session. (Requires the gh and jq CLIs).

```
gh issue list --assignee @me --limit 1 --json title \  | jq -r '.[0].title' \  | jules remote new --repo .
```

**3\. Use Gemini to analyze and assign the hardest issue to Jules:** Use the Gemini CLI to analyze your assigned GitHub issues, identify the most tedious one, and pipe its title to Jules.

```
gemini -p "find the most tedious issue, print it verbatim\n$(gh issue list --assignee @me)" \  | jules remote new --repo .
```
