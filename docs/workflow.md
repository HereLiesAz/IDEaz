# Cortex IDE: Development Workflow

This document outlines the standardized development workflow for the Cortex IDE project. Adhering to this workflow is essential for maintaining a high-quality, stable, and collaborative codebase.

## 1. Branching Model: GitFlow
We will use the **GitFlow** branching model, a widely adopted and robust strategy for managing a project with scheduled releases.

**Main Branches:**
-   `main`: This branch always contains production-ready code. Direct commits to `main` are strictly forbidden. Merges to `main` only happen from `release` branches.
-   `develop`: This is the primary development branch. It represents the latest delivered development changes for the next release. All feature branches are created from `develop` and merged back into it.

**Supporting Branches:**
-   **Feature Branches (`feature/<feature-name>`):**
    -   Created from: `develop`
    -   Merged back into: `develop`
    -   Naming convention: `feature/add-git-clone-button`
    -   Purpose: For developing new features. Each feature should be developed in its own branch.

-   **Release Branches (`release/vX.X.X`):**
    -   Created from: `develop`
    -   Merged back into: `main` and `develop`
    -   Naming convention: `release/v1.0.0`
    -   Purpose: To prepare for a new production release. This branch is for final bug fixes, documentation generation, and other release-oriented tasks. No new features are added here.

-   **Hotfix Branches (`hotfix/<issue-name>`):**
    -   Created from: `main`
    -   Merged back into: `main` and `develop`
    -   Naming convention: `hotfix/fix-login-crash`
    -   Purpose: To quickly patch a critical bug in the production version (`main`).

## 2. Code Contribution Workflow
1.  **Create an Issue:** Before starting work, create a detailed issue in the project's issue tracker that describes the feature or bug.
2.  **Create a Feature Branch:** From the `develop` branch, create a new feature branch:
    `git checkout -b feature/your-feature-name develop`
3.  **Implement Changes:** Make your code changes on the feature branch. Commit your work early and often with clear, descriptive commit messages.
4.  **Ensure Tests Pass:** Run all relevant unit and integration tests locally to ensure your changes have not introduced any regressions. Add new tests for your new code.
5.  **Open a Pull Request (PR):** Push your feature branch to the remote repository and open a Pull Request to merge your branch into `develop`.
    -   The PR description should link to the issue it resolves.
    -   The PR should be small and focused on a single issue.

## 3. Code Review Process
-   **Require at Least One Approval:** All PRs must be reviewed and approved by at least one other team member before they can be merged.
-   **Constructive Feedback:** Reviewers should provide clear, constructive, and respectful feedback. The goal is to improve the code, not to criticize the author.
-   **Address All Comments:** The author of the PR is responsible for addressing all comments and feedback before the PR is merged.

## 4. Continuous Integration / Continuous Deployment (CI/CD)
-   **Automated Checks:** When a PR is opened, a CI/CD pipeline (e.g., using GitHub Actions) will be automatically triggered. This pipeline will:
    1.  **Build the Project:** Compile both the Android client and the backend service.
    2.  **Run All Tests:** Execute the full suite of unit and integration tests.
    3.  **Perform Static Analysis:** Run linters (like `ktlint` for Kotlin and `ruff` for Python) to enforce code style.
-   **Merge Block:** A PR cannot be merged if any of the CI checks fail. This ensures that the `develop` and `main` branches are always stable and in a deployable state.
-   **Automated Deployment:** Merging a `release` branch into `main` will trigger an automated deployment of the backend service to Google Cloud Run and a build of the release-ready Android APK.
