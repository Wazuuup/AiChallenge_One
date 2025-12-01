# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform project with three main modules:

- **composeApp**: Compose Multiplatform web frontend (supports both Wasm and JS targets)
- **server**: Ktor backend server (JVM)
- **shared**: Common code shared across all platforms (JVM, JS, WasmJS)

The project uses Gradle with Kotlin DSL and follows a multiplatform architecture where platform-specific code is
organized in source sets (`commonMain`, `jsMain`, `jvmMain`, `wasmJsMain`).

## Build Commands

On Windows (use `.\gradlew.bat`), on macOS/Linux (use `./gradlew`):

**Run the Ktor server:**

```
.\gradlew.bat :server:run
```

**Run the web app (Wasm target - faster, modern browsers):**

```
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

**Run the web app (JS target - slower, older browsers):**

```
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

**Run tests:**

```
.\gradlew.bat test
```

**Build all modules:**

```
.\gradlew.bat build
```

**Run tests for specific module:**

```
.\gradlew.bat :server:test
.\gradlew.bat :composeApp:test
.\gradlew.bat :shared:test
```

## Architecture

### Module Dependencies

- `composeApp` depends on `shared`
- `server` depends on `shared`
- `shared` is the foundation module with no internal dependencies

### Source Set Structure

Each multiplatform module (`composeApp`, `shared`) has platform-specific source sets:

- `commonMain`: Platform-agnostic code
- `jsMain`: JavaScript-specific implementations
- `jvmMain`: JVM-specific implementations (server-side)
- `wasmJsMain`: WebAssembly-specific implementations

When adding platform-specific functionality, place code in the appropriate source set. Common interfaces should be in
`commonMain` with `expect` declarations, and platform implementations in respective source sets with `actual`
declarations.

### Server Configuration

The Ktor server runs on port 8080 (defined in `shared/src/commonMain/kotlin/ru/sber/cb/aichallenge_one/Constants.kt`).
The server application entry point is in `server/src/main/kotlin/ru/sber/cb/aichallenge_one/Application.kt`.

### Web Frontend

The web frontend uses Compose Multiplatform and supports two compilation targets:

- **WasmJS**: Modern, faster execution (recommended for development)
- **JS**: Broader browser compatibility

The main app entry point is `composeApp/src/webMain/kotlin/ru/sber/cb/aichallenge_one/App.kt`.

## Key Dependencies

- Kotlin: 2.2.20
- Compose Multiplatform: 1.9.1
- Ktor: 3.3.1
- AndroidX Lifecycle: 2.9.5

Dependencies are centralized in `gradle/libs.versions.toml` using version catalogs.
