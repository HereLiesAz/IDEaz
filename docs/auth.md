# IDEaz IDE: Authentication & API Key Management

This document outlines the authentication strategy for the IDEaz IDE application and the management of the user's Jules API key.

## 1. User Authentication for IDEaz IDE
**Goal:** To provide a seamless and secure login experience for users of the IDEaz IDE app itself.

The authentication for the main app will follow standard, user-friendly web and mobile practices.

-   **Primary Method: Social Sign-On:** Users will sign in using trusted third-party providers like Google. This avoids the need for traditional password management.
-   **Authentication Flow:** The app will use a standard OAuth 2.0 flow to authenticate the user and receive a JWT to maintain the session.

## 2. API Key Management: The "Bring Your Own Key" (BYOK) Model
**Goal:** To ensure all calls to the Jules and Gemini APIs are authenticated without exposing a secret key in the app or requiring a backend server.

User authentication is completely separate from API authentication. To use the AI's code generation capabilities, the user must provide their own API keys.

-   **User-Provided Keys:** The user is responsible for obtaining their own API keys from the respective platforms (Jules, Google AI Studio).
-   **Input and Storage:** The user will enter these keys into the "Settings" screen. The app will then save these keys securely on the device using Android's **EncryptedSharedPreferences**.
-   **Usage:** When the `MainViewModel` makes a call to an AI API, it will retrieve the securely stored key for that specific service. For Jules, it passes this to the `AuthInterceptor`. For Gemini, it will pass it to the Gemini client.
-   **Security:** This model shifts the responsibility for API costs and access to the user. The app's primary security responsibility is to ensure the keys are stored on the device as securely as possible and are only ever sent directly to the respective APIs over HTTPS.