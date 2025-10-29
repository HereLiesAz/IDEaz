# Cortex IDE: AI Agent Production Checklist (Screenshot-First Architecture)

This document provides a granular, step-by-step checklist to build the Cortex IDE, following the "Screenshot-First," intent-driven, on-device architecture.

---

## **Phase 1: The Core On-Device Service (Weeks 1-5)**

The goal of this phase is to build the non-UI background service that can autonomously update and compile an Android app from a Git repository.

- [ ] **Task 1.1:** Build the foundational Android background service (the "Cortex Service").
- [ ] **Task 1.2:** Integrate the JGit library into the service and implement the automated `git pull` functionality.
- [ ] **Task 1.3:** Integrate an on-device Gradle wrapper and implement the logic for the Cortex Service to trigger a `gradle build`.
- [ ] **Task 1.4:** Implement the logic for the service to automatically install a newly compiled APK and relaunch the application.
- [ ] **Task 1.5:** Create a "Settings" screen for the user to input and securely save their Jules API key using EncryptedSharedPreferences.

---

## **Phase 2: The Visual Overlay & AI Integration (Weeks 6-10)**

This phase focuses on building the user-facing interaction layer and leveraging the multi-modal AI.

- [ ] **Task 2.1:** Build the "Cortex Overlay" as a transparent Android service.
- [ ] **Task 2.2:** Implement the functionality to capture a screenshot of the running application.
- [ ] **Task 2.3:** Implement the UI for the overlay, allowing a user to draw a selection box on the screenshot and enter a text prompt.
- [ ] **Task 2.4:** Integrate a networking client (e.g., Ktor) to make direct, multi-modal calls to the Jules API, sending the annotated screenshot and the text prompt.
- [ ] **Task 2.5:** **(CRITICAL R&D SPIKE)** Test and validate the end-to-end accuracy of the Jules API's multi-modal capabilities. Can it reliably map a component in a screenshot to the correct source code and perform a simple change?
- [ ] **Task 2.6:** Implement the full end-to-end loop: Overlay capture -> Jules API call -> Cortex Service pulls, compiles, and relaunches.

---

## **Phase 3: Automated Debugging & Polish (Weeks 11-15)**

This phase focuses on making the core loop robust and user-friendly.

- [ ] **Task 3.1:** Implement the automated debugging loop by capturing `stderr` from a failed Gradle build.
- [ ] **Task 3.2:** Implement the logic to send the captured compile error log back to the Jules API for correction.
- [ ] **Task 3.3:** Build a simple, non-technical UI to show the user the status of the AI agent.
- [ ] **Task 3.4:** Refine the user experience of the overlay and the app relaunch to feel as seamless as possible.

---

## **Phase 4: Production Hardening & Launch (Weeks 16-20)**

This phase is for optimizing and securing the application for the Google Play Store.

- [ ] **Task 4.1:** Conduct a security audit, focusing on the secure storage of the user's API key.
- [ ] **Task 4.2:** Perform extensive QA and performance testing on a wide range of Android devices.
- [ ] **Task 4.3:** Prepare the application for submission to the Google Play Store, including writing a privacy policy that clearly explains the BYOK model and the screenshot-based interaction.
- [ ] **Task 4.4:** Launch the first version of the Cortex IDE.
