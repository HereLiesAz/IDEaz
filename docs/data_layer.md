# Cortex IDE: Data Layer

## Overview
This document describes the data persistence strategy for the Cortex IDE Android client. The data layer is responsible for storing and managing all local data, including user settings, application metadata, and potentially cached project information. The architecture follows modern Android best practices to ensure data is managed in a robust, scalable, and performant way.

## Architecture
The data layer is a key component of the overall Model-View-ViewModel (MVVM) architecture of the Cortex IDE client. Data access will be abstracted through the **Repository pattern**. UI components (Views) will interact with ViewModels, which in turn will request data from Repositories. These Repositories will be the single source of truth for all data, and they will be responsible for fetching data from the appropriate data source, whether that's a local database or a remote API.

This separation of concerns makes the application more modular, testable, and easier to maintain.

## Local Storage Technologies
The Cortex IDE client will utilize two primary technologies for local data storage, chosen to fit different use cases.

### 1. Room Persistence Library
**Use Case:** User settings, application metadata, and other structured relational data.

**Description:**
Room is the recommended persistence library for Android. It provides an abstraction layer over SQLite to allow for more robust database access while harnessing the full power of SQLite. It will be used for:
-   Storing user preferences (e.g., theme, font size).
-   Managing application metadata (e.g., list of recently opened projects).
-   Caching simple, structured data that needs to be persisted across app sessions.

**Implementation:**
-   **Entities:** Plain Kotlin data classes will be used to define the database tables.
-   **DAOs (Data Access Objects):** Interfaces will define the database operations (queries, inserts, updates, deletes) using annotations. Room will generate the implementation at compile time.
-   **Database:** A central `RoomDatabase` class will tie the entities and DAOs together.

### 2. Direct SQLite Implementation
**Use Case:** Complex project indexing and other performance-critical database operations.

**Description:**
While Room is excellent for most use cases, there may be scenarios where direct access to the underlying SQLite database is necessary for maximum performance and control. The primary candidate for this is the local project indexing required for features like code completion or symbol navigation, which might involve complex queries or bulk data operations that are more efficiently handled with raw SQL.

**Implementation:**
-   If needed, a custom `SQLiteOpenHelper` will be implemented to manage the schema and versioning of this specialized database.
-   This will be used for tasks that are too complex or performance-sensitive for Room's abstraction, such as building and querying an index of a project's codebase. The decision to use direct SQLite will be made on a case-by-case basis after profiling and identifying performance bottlenecks with the Room implementation.
