# Cortex IDE: AI Agent Production Checklist

This document provides a granular, step-by-step checklist for an AI agent to follow to take the Cortex IDE project from its current state (documentation and concept) to a production-ready application. This checklist is derived directly from the "Granular Implementation Roadmap" in the project blueprint.

---

## **Phase 1: Minimum Viable Product (MVP) - Core Backend API and Foundational Android IDE Shell**

### Backend Development (Weeks 1-4)
- [x] **Task 1.1:** Initialize a new Python project using the FastAPI framework.
- [x] **Task 1.2:** Define the basic project structure and dependencies (`requirements.txt`).
- [x] **Task 1.3:** Using Pydantic models, define the JSON schemas for `POST /v1/agent/execute` and `POST /v/completion/inline`.
- [x] **Task 1.4:** Implement a "mock" AI service. This service should receive requests and return hardcoded, static JSON responses that mimic the structure of a real AI response.
- [x] **Task 1.5:** Write a `Dockerfile` to containerize the FastAPI application.
- [ ] **Task 1.6:** Deploy the containerized mock backend to Google Cloud Run using CPU resources only.
- [ ] **Task 1.7:** Verify the deployment and ensure the mock endpoints are reachable.

### Android Client Development (Weeks 1-4)
- [ ] **Task 1.8:** Create a new Android Studio project.
- [ ] **Task 1.9:** Configure the project with Kotlin, Jetpack Compose, and the necessary Gradle dependencies.
- [ ] **Task 1.10:** Build the basic UI shell with Jetpack Compose:
    - [ ] Create a main screen.
    - [ ] Add a simple `TextField` to act as the code editor.
    - [ ] Add a `Box` or other Composable to serve as the visual preview panel.
    - [ ] Add a basic file explorer Composable.
- [ ] **Task 1.11:** Implement a basic "tap-to-prompt" mechanism: a tap on the visual preview panel should display a static prompt overlay.
- [ ] **Task 1.12:** Integrate a networking library (Ktor or OkHttp).
- [ ] **Task 1.13:** Implement the service layer to make API calls from the prompt overlay to the deployed mock backend.
- [ ] **Task 1.14:** **Technical Spike:** Integrate the JGit library and address any compatibility issues with the Android runtime.
- [ ] **Task 1.15:** Implement the `git clone` functionality, allowing a user to clone a remote repository into the app's local storage.

---

## **Phase 2: AI Integration & RAG Pipeline**

### Backend Development (Weeks 5-10)
- [ ] **Task 2.1:** Select and download an initial open-source code generation LLM (e.g., a Qwen or DeepSeek model).
- [ ] **Task 2.2:** Select and configure an inference server (e.g., Text Generation Inference - TGI).
- [ ] **Task 2.3:** Update the `Dockerfile` to run both the FastAPI app and the TGI server.
- [ ] **Task 2.4:** Deploy the new container to Google Cloud Run, configuring the service to use a GPU instance.
- [ ] **Task 2.5:** Choose and integrate a vector database solution (e.g., FAISS).
- [ ] **Task 2.6:** Implement the `/v1/project/index` endpoint:
    - [ ] It should accept a project upload.
    - [ ] It should perform code-aware chunking on the source files.
    - [ ] It should generate embeddings for the chunks.
    - [ ] It should populate the vector database.
- [ ] **Task 2.7:** Integrate the RAG retrieval step into the `/v1/agent/execute` endpoint.
- [ ] **Task 2.8:** Replace the mock AI service logic with actual HTTP requests to the live TGI server.

### Android Client Development (Weeks 5-10)
- [ ] **Task 2.9:** Update the client's networking layer to point to the live, GPU-powered backend.
- [ ] **Task 2.10:** Implement logic to handle the Server-Sent Event (SSE) stream from the `/v1/agent/execute` endpoint, updating the UI in real-time.
- [ ] **Task 2.11:** Implement the client-side logic to parse code modifications from the backend and apply them to the local files.
- [ ] **Task 2.12:** Build the Agent Log screen Composable to render messages, code blocks, and diffs.

---

## **Phase 3: Agentic & Visual Capabilities**

### Backend Development (Weeks 11-16)
- [ ] **Task 3.1:** Implement the stateful Agent Orchestrator using a data store like Redis to manage multi-step agent tasks.
- [ ] **Task 3.2:** Refine the RAG pipeline with more advanced strategies (e.g., re-ranking, expanding indexed data).
- [ ] **Task 3.3:** Begin experiments with supervised fine-tuning on the selected base LLM using a high-quality dataset of Kotlin/Android code.

### Android Client Development (Weeks 11-16)
- [ ] **Task 3.4:** Implement the logic to map a tap's (x, y) coordinates on the Visual Previewer to a specific Composable element.
- [ ] **Task 3.5:** Enhance the Contextual Prompt Overlay to appear adjacent to the selected component.
- [ ] **Task 3.6:** Implement the full, interactive Jetpack Compose Visual Previewer panel.
- [ ] **Task 3.7:** Build out the complete Git integration UI: commit history, diff viewer, and controls for push, pull, and branching.
- [ ] **Task 3.8:** Develop the remaining UI screens: Project Creation, Settings, User Authentication, and the Integrated Terminal.

---

## **Phase 4: Production Hardening & Launch**

### Operations and Infrastructure (Weeks 17-20)
- [ ] **Task 4.1:** Integrate comprehensive logging, monitoring, and alerting into the backend service (e.g., Google Cloud Monitoring).
- [ ] **Task 4.2:** Conduct a thorough security review of the backend API and the client application.
- [ ] **Task 4.3:** Profile and optimize the backend container to reduce cold start times and memory usage.
- [ ] **Task 4.4:** Configure a minimum instance count of 1 on the GPU-enabled Cloud Run service to keep it warm.
- [ ] **Task 4.5:** Set up a full CI/CD pipeline (e.g., using GitHub Actions) to automate testing and deployment.

### Client Application (Weeks 17-20)
- [ ] **Task 4.6:** Perform rigorous Quality Assurance (QA) testing on a wide range of physical Android devices and OS versions.
- [ ] **Task 4.7:** Profile the Android application to identify and resolve performance bottlenecks in the code editor and file system operations.
- [ ] **Task 4.8:** Prepare the application for submission to the Google Play Store:
    - [ ] Create store listing assets.
    - [ ] Write a comprehensive privacy policy.
    - [ ] Implement user authentication.
    - [ ] Ensure compliance with all Google Play policies.
- [ ] **Task 4.9:** Launch!
