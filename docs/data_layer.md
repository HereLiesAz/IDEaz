# Cortex IDE: Data Layer & Source of Truth

This document describes the dual data layer architecture of the Cortex IDE project. It's crucial to distinguish between the data layer for the **user's application** and the internal data storage for the **Cortex IDE app itself**.

---

## 1. The User's Application: The "Invisible Repository"
**The ultimate source of truth for the application a user builds is a dedicated, private Git repository.**

In the Cortex IDE paradigm, the user does not directly interact with a database or data models. They express intent in natural language (e.g., "I need to track customers with a name and email"), and the Jules AI agent is responsible for generating all the necessary code to represent and manage that data.

This code, including database schemas, migrations, and API logic, is committed to the "Invisible Repository." This Git-native approach means the user's application benefits from a robust data management strategy by default:

-   **Versioned Schema:** Every change to the data model is a versioned commit, providing a complete history.
-   **Atomic Changes:** Data model changes are committed along with the UI and logic changes that depend on them, ensuring the application is always in a consistent state.
-   **Rollbacks:** Reverting to a previous data model is as simple as reverting a commit, a task handled by the AI.

---

## 2. The Cortex IDE App: Internal Data Storage
**The Cortex IDE app itself uses local, on-device storage for its own operational data.**

The IDE needs to store settings and sensitive information to function correctly. This data is stored locally on the user's Android device.

-   **Primary Use Cases:**
    -   Storing the user's personal Jules API key.
    -   Saving user preferences and app settings.
    -   Caching metadata about the user's project.

-   **Technologies:**
    -   **`EncryptedSharedPreferences`:** This is the **mandatory** technology for storing the user's Jules API key. It encrypts the key at rest, providing a critical layer of security.
    -   **Room Persistence Library:** For other structured data like user preferences and project metadata, the standard Room library (an abstraction over SQLite) will be used.
