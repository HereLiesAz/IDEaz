# Cortex IDE: Authentication & API Key Management

This document outlines the authentication strategy for the Cortex IDE application and the management of the user's Jules API key.

## 1. User Authentication for Cortex IDE
**Goal:** To provide a seamless and secure login experience for users of the Cortex IDE app itself.

The authentication for the main app will follow standard, user-friendly web and mobile practices.

-   **Primary Method: Social Sign-On:** Users will sign in using trusted third-party providers like Google. This avoids the need for traditional password management.
-   **Authentication Flow:** The app will use a standard OAuth 2.0 flow to authenticate the user and receive a JWT to maintain the session.

## 2. API Key Management: The "Bring Your Own Key" (BYOK) Model
**Goal:** To ensure all calls to the Jules API are authenticated without exposing a secret key in the app or requiring a backend server.

User authentication is completely separate from API authentication. To use the AI's code generation capabilities, the user must provide their own Jules API key.

-   **User-Provided Key:** The user is responsible for obtaining their own API key from the Jules platform.
-   **Input and Storage:** The user will enter this key into a dedicated "Settings" screen within the Cortex IDE app. The app will then save this key securely on the device using Android's **EncryptedSharedPreferences**.
-   **Usage:** When the on-device Cortex Service makes a call to the Jules API, it will retrieve the securely stored key and use it to authenticate the request.
-   **Security:** This model shifts the responsibility for API costs and access to the user. The app's primary security responsibility is to ensure the key is stored on the device as securely as possible and is only ever sent directly to the Jules API over HTTPS. It is never transmitted elsewhere.
