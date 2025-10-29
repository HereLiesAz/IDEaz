# Cortex IDE: Development Workflow

This document outlines the standardized development workflow for contributors working on the **Cortex IDE application itself**. This is distinct from the automated workflow the AI uses to build user applications.

## 1. Branching Model: GitFlow
We will use the **GitFlow** branching model to manage the development of the Cortex IDE.

**Main Branches:**
-   `main`: Contains production-ready, stable code for the Cortex IDE.
-   `develop`: The primary development branch for integrating new features.

**Supporting Branches:**
-   **Feature Branches (`feature/<feature-name>`):** For developing new features for the IDE (e.g., `feature/improve-overlay-selection`). Created from `develop`.
-   **Release Branches (`release/vX.X.X`):** For preparing a new public release of the Cortex IDE app. Created from `develop`.
-   **Hotfix Branches (`hotfix/<issue-name>`):** For fixing critical bugs in the production version of the Cortex IDE app. Created from `main`.

## 2. Code Contribution Workflow
1.  **Create an Issue:** Before starting work on a new feature or bug fix for the Cortex IDE, create a detailed issue.
2.  **Create a Feature Branch:** Branch from `develop`.
3.  **Implement Changes:** Make your code changes on the feature branch.
4.  **Run Tests:** Run the full suite of unit, integration, and E2E tests locally to ensure no regressions have been introduced in the IDE.
5.  **Open a Pull Request (PR):** Open a PR to merge your feature branch into `develop`. The PR must be reviewed and approved by at least one other team member.

## 3. The AI's Internal Workflow (For Reference)
It is important not to confuse our development workflow with the one the AI uses. The Jules agent, orchestrated by the on-device Cortex Service, uses a much simpler, automated workflow internally for each user app:

1.  **AI is Triggered:** A user's prompt initiates the process.
2.  **AI Creates a Branch:** The Jules agent creates a new branch in the user's "Invisible Repository."
3.  **AI Commits Code:** The agent makes the requested code change and commits it.
4.  **AI Creates a PR:** The agent creates a pull request.
5.  **Cortex Service Merges:** The on-device service automatically validates and merges the PR.
6.  **Cortex Service Pulls and Compiles:** The service pulls the merged code from the `main` branch of the user's repository and triggers a build.
7.  **Loop or Relaunch:** If the build fails, the loop repeats from step 2. If it succeeds, the app is relaunched.
