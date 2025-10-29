# Cortex IDE: AI Agent Production Checklist (Intent-Driven On-Device Architecture)

This document provides a granular, step-by-step checklist to build the Cortex IDE, following the intent-driven, on-device architecture.

---

## **Phase 1: The Core On-Device Service (Weeks 1-6)**

The goal of this phase is to build the non-UI background service that can autonomously update and compile an Android app from a Git repository.

- [ ] **Task 1.1:** Build the foundational Android background service (the "Cortex Service").
- [ ] **Task 1.2:** Integrate the JGit library into the service.
- [ ] **Task 1.3:** Implement the automated `git pull` functionality, allowing the service to fetch the latest code from a designated "Invisible Repository."
- [ ] **Task 1.4:** Integrate an on-device Gradle wrapper. Implement the logic for the Cortex Service to trigger a `gradle build` command on the pulled source code.
- [ ] **Task 1.5:** Implement the logic for the service to automatically install the newly compiled APK and relaunch the application.
- [ ] **Task 1.6:** Create a basic "Settings" screen in the main Cortex IDE app for the user to input and securely save their Jules API key using EncryptedSharedPreferences.

---

## **Phase 2: The Visual Overlay & AI Integration (Weeks 7-12)**

This phase focuses on building the user-facing interaction layer and making the first AI-driven change.

- [ ] **Task 2.1:** Build the "Cortex Overlay" as a transparent Android service that can be drawn over the running user application.
- [ ] **Task 2.2:** Implement the UI for the overlay, including the ability to draw a selection box and enter a text prompt.
- [ ] **Task 2.3:** **(CRITICAL R&D SPIKE)** Research and develop a proof-of-concept for mapping a user's screen selection (coordinates + screenshot) to a specific component in the application's source code. This is the most technically challenging part of the project.
- [ ] **Task 2.4:** Integrate a networking client (e.g., Ktor) to make direct calls to the Jules API from the Cortex Service.
- [ ] **Task 2.5:** Implement the end-to-end loop for a single, hardcoded change:
    - a. Trigger a fake user prompt from the overlay.
    - b. Send a request to the Jules API.
    - c. Have the Cortex Service pull the resulting commit, re-compile, and relaunch the app.

---

## **Phase 3: Automated Debugging & Polish (Weeks 13-18)**

This phase focuses on making the core loop robust and user-friendly.

- [ ] **Task 3.1:** Implement the automated debugging loop. The Cortex Service must be able to capture the `stderr` from a failed Gradle build.
- [ ] **Task 3.2:** Implement the logic to send the captured compile error log back to the Jules API with a prompt to fix the issue.
- [ ] **Task 3.3:** Build a simple, non-technical UI within the Cortex app to show the user the status of the AI agent (e.g., "Jules is making changes...", "Jules is debugging an issue..."). This UI should read status updates from the Cortex Service.
- [ ] **Task 3.4:** Refine the user experience of the overlay and the app relaunch, ensuring it feels as seamless as possible.

---

## **Phase 4: Production Hardening & Launch (Weeks 19-22)**

This phase is for optimizing and securing the application for the Google Play Store.

- [ ] **Task 4.1:** Conduct a thorough security audit, focusing on the secure storage and use of the user's Jules API key.
- [ ] **Task 4.2:** Perform extensive QA and performance testing on a wide range of physical Android devices to ensure the background service is reliable.
- [ ] **Task 4.3:** Prepare the application for submission to the Google Play Store, including writing a privacy policy that clearly explains the BYOK model and the app's powerful on-device permissions.
- [ ] **Task 4.4:** Launch the first version of the Cortex IDE.
