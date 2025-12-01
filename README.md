# GigaChat Web Application

This is a Kotlin Multiplatform web chat application that integrates with GigaChat API. The application features a
web-based chat interface built with Compose Multiplatform and a Ktor backend server.

## Features

- Web-based chat interface with message history
- Integration with GigaChat API using OAuth 2.0
- Real-time message exchange between user and GigaChat bot
- MVVM architecture with state management
- Dependency injection with Koin
- CORS support for cross-origin requests

## Project Structure

* [/composeApp](./composeApp/src) - Compose Multiplatform web frontend
    - Chat UI components
    - ChatViewModel for state management
    - API client for server communication

* [/server](./server/src/main/kotlin) - Ktor backend server
    - GigaChat API client with OAuth 2.0 authentication
    - Chat service for processing messages
    - REST API endpoints
    - Koin dependency injection configuration

* [/shared](./shared/src) - Common code shared between frontend and backend
    - Data models (ChatMessage, ChatResponse, etc.)
    - Shared constants

## Prerequisites

Before running the application, you need to set up GigaChat API credentials:

1. Obtain GigaChat API credentials from Sber
2. Create a `.env` file (or copy from `.env.example`):
   ```
   GIGACHAT_CLIENT_ID=your_client_id
   GIGACHAT_CLIENT_SECRET=your_client_secret
   ```
3. Create a `run-server.bat` file (or copy from `run-server.bat.example`) with your credentials

**Note:** The `.env` file is not automatically loaded by JVM. You must use the `run-server.bat` script to run the server
with environment variables, or set them manually in your system.

## How to Run

### Step 1: Build and Run Server

First, start the Ktor backend server:

**Windows (Recommended):**

Use the provided batch script that sets environment variables:

```shell
run-server.bat
```

Or set environment variables manually:

```shell
set GIGACHAT_CLIENT_ID=your_client_id
set GIGACHAT_CLIENT_SECRET=your_client_secret
.\gradlew.bat :server:run
```

**macOS/Linux:**

```shell
export GIGACHAT_CLIENT_ID=your_client_id
export GIGACHAT_CLIENT_SECRET=your_client_secret
./gradlew :server:run
```

The server will start on `http://localhost:8080`

### Step 2: Build and Run Web Application

In a new terminal, start the web application:

**For Wasm target (recommended - faster, modern browsers):**

Windows:

```shell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

macOS/Linux:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**For JS target (broader browser compatibility):**

Windows:

```shell
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

macOS/Linux:

```shell
./gradlew :composeApp:jsBrowserDevelopmentRun
```

The web application will automatically open in your browser.

## Usage

1. Once both the server and web application are running, open your browser to the web application URL (typically
   `http://localhost:8080` or as shown in the terminal)
2. Type your message in the input field at the bottom of the chat interface
3. Click "Отправить" (Send) or press Enter to send your message
4. Wait for GigaChat to process your request and display the response
5. Continue the conversation as needed

## Technologies Used

- **Kotlin**: 2.2.20
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.1
- **Koin**: 4.0.1 (Dependency Injection)
- **Kotlinx Serialization**: 1.8.0
- **Logback**: 1.5.20

## Architecture

The application follows the MVVM (Model-View-ViewModel) architecture:

- **Model**: Data models in the shared module
- **View**: Composable UI components in composeApp
- **ViewModel**: ChatViewModel managing UI state and business logic

Backend uses a layered architecture:

- **Controller** (ChatRouting): REST API endpoints
- **Service** (ChatService): Business logic
- **Client** (GigaChatApiClient): External API integration

## API Endpoints

- `GET /` - Health check endpoint
- `POST /api/send-message` - Send a message to GigaChat
    - Request body: `{"text": "your message"}`
    - Response: `{"text": "response text", "status": "SUCCESS|ERROR"}`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)