# Conceptual Blueprint for "Cortex IDE": An Intent-Driven, On-Device IDE for Android

## **Executive Vision: A "Post-Code" Paradigm for Android**

The Cortex IDE represents a fundamental reimagining of the software development workflow for a mobile-first, AI-native world. It is not a traditional Integrated Development Environment (IDE) but an **intent-driven creation engine**. The user does not write code; they interact with their live, running Android application through a visual overlay, expressing their desired changes in natural language.

The core mission of Cortex IDE is to transition the user's role from a developer to a **high-level director**. An autonomous AI agent, powered by the Jules API, handles the entire software development lifecycle—code generation, modification, compilation, and debugging—in the background, managed entirely by a service running on the user's Android device. The source code is an invisible implementation detail, managed via a Git repository that the user never sees.

## **System Architecture: The Automated On-Device Loop**

The architecture is designed to create a seamless, magical experience where the user's intent is translated directly into a functional, updated application.

### **1. The Core Components**

*   **The User's App (The Live View):** This is the actual, compiled, and running Android application that the user is building. It is the primary and only interface for the user.
*   **The Cortex Overlay (The Interactive Canvas):** A simple, transparent Android service that runs on top of the User's App. When activated, it captures the screen, allows the user to select an area, and captures their text prompt. It does **not** require Accessibility Service permissions.
*   **The Cortex Service (The On-Device Orchestrator):** A persistent background service on the Android device that manages the entire development loop. It is the heart of the IDE.
*   **The Invisible Repository:** A private Git repository that stores the source code for the User's App. This is the ultimate source of truth, managed entirely by the Cortex Service and the Jules API.

### **2. The Intent-to-Application Workflow**

The development process is a continuous, automated loop orchestrated by the Cortex Service:

1.  **Intent Capture:** The user activates the Cortex Overlay, selects a part of their live app (e.g., a button), and types an instruction: "Make this button red."
2.  **Contextualization:** The Cortex Service takes a screenshot of the user's app, highlights the area the user selected, and packages this image with the user's text prompt.
3.  **AI Task (Jules API Call):** The Cortex Service makes a direct, multi-modal call to the Jules API. The payload includes the **screenshot image** and a prompt like: "Here is a screenshot of the app. The user selected the highlighted area and said: '[user's prompt]'. Please identify the relevant source code and perform the change." It uses a **user-provided API key** for this, following the "Bring Your Own Key" (BYOK) model.
4.  **AI Action:** The Jules agent receives the request, checks out the source code from the Invisible Repository in its own ephemeral environment, makes the necessary code changes, and commits them to a new branch.
5.  **Automated Git Pull:** The Cortex Service, running on the user's device, detects the new commit and automatically performs a `git pull` to sync the local source code.
6.  **On-Device Compilation:** The Cortex Service triggers an on-device Gradle build to compile the updated source code into a new Android application package (APK).
7.  **Automated Relaunch:** Once the compilation is successful, the Cortex Service automatically installs and relaunches the User's App. The user sees their application restart and now reflects the change (the button is red).

### **3. The Automated Debugging Loop**

A critical feature of the Cortex Service is its ability to handle compilation errors autonomously.

1.  **Compilation Fails:** If the on-device Gradle build fails after a `git pull`, the Cortex Service intercepts the build log containing the error messages.
2.  **Automated AI Debugging:** The service immediately triggers a new Jules API call. The prompt includes the original user intent, the code the AI just wrote, and the full compilation error log, with a new instruction: "The code you just wrote failed to compile. Here is the build log. Please fix the error, commit the changes, and try again."
3.  **AI Corrects Itself:** The Jules agent receives the error, debugs its own code, commits a fix to the repository.
4.  **The Loop Repeats:** The Cortex Service pulls the new commit, re-compiles, and continues this loop until the application compiles successfully and can be relaunched. The user is simply shown a status like "Jules is debugging an issue..." and is never exposed to a technical error.

## **Implementation Roadmap: On-Device Architecture**

### **Phase 1: The Core On-Device Service**
*   **Task 1:** Build the foundational Android background service (the "Cortex Service").
*   **Task 2:** Integrate JGit and implement the automated `git pull` functionality.
*   **Task 3:** Integrate an on-device Gradle build system, allowing the service to compile an Android project from source.
*   **Task 4:** Implement the logic to automatically install and relaunch the compiled application.
*   **Task 5:** Create a settings screen for the user to input and securely store their Jules API key using EncryptedSharedPreferences.

### **Phase 2: The Visual Overlay & AI Integration**
*   **Task 6:** Build the "Cortex Overlay" as a transparent Android service that can be activated over any running application.
*   **Task 7:** Implement the UI for the overlay, allowing a user to draw a selection box and enter a text prompt.
*   **Task 8:** **(R&D Spike)** Develop a method to map the user's visual selection to a component in the source code. This is a critical and complex task that may involve screen analysis and component tree introspection.
*   **Task 9:** Make the first direct call to the Jules API, sending the user's prompt and context.
*   **Task 10:** Implement the logic to receive the AI's response and trigger the Git-Compile-Relaunch loop.

### **Phase 3: Automated Debugging & Polish**
*   **Task 11:** Implement the automated debugging loop: capture compile errors and send them back to the Jules API.
*   **Task 12:** Build a user-facing UI that provides simple, non-technical status updates on the AI's progress (e.g., "Jules is making changes," "Jules is debugging...").
*   **Task 13:** Refine the entire user experience, ensuring smooth transitions and clear communication.

### **Phase 4: Production Hardening**
*   **Task 14:** Conduct a thorough security audit, especially around the handling of the user's API key.
*   **Task 15:** Perform extensive QA testing on a wide range of Android devices.
*   **Task 16:** Prepare the application for Google Play Store submission, with clear documentation for the BYOK model.
