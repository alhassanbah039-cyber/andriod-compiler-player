# Android Compiler Player (Sandbox Application)

An offline native Android application acting as an "App-in-an-App" Compilation Sandbox. The application accepts raw multi-file source payloads, safely validates syntax and security constraints, compiles code on-device, and dynamically executes it.

---

## 🏛️ Architecture Overview

The system is designed with four decoupled, modular layers:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI Layer                     │
│  (SandboxDashboardScreen, ViewModel, Live IDE Terminal Console) │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│               Module 1: Multi-Project Storage & Router          │
│    (ProjectPayloadParser, ProjectLifecycleManager, Models)     │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│             Module 2: Syntax & Security Validator               │
│   (SyntaxBracketValidator, DependencyBlocklistScanner)          │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│            Module 3: Dual-Compiler Execution Pathway            │
│  ┌─────────────────────────────┐   ┌──────────────────────────┐ │
│  │     Pipeline A: Java/JVM    │   │  Pipeline B: C++ Native  │ │
│  │ (ECJ Bytecode -> D8 -> Dex) │   │ (Clang/LLVM -> .so Dyn)  │ │
│  └─────────────────────────────┘   └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Key Modules & Features

### 1. Multi-Project Storage & Topology Router (Kotlin)
- **`ProjectPayloadParser`**: Parses input stream formats with metadata tokens (`[META: LANGUAGE=...]`) and file tags (`[FILE: path/filename.ext]`).
- **`ProjectLifecycleManager`**: Persists file trees into isolated subdirectories under internal app storage:
  `${context.filesDir}/projects/[Sanitized_Project_Name]`
- Standard project hierarchy generation: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, and source folders.

### 2. Syntax & No-Dependency Validator (Kotlin)
- **`SyntaxBracketValidator`**: Pre-compilation scanner verifying balanced curly braces `{}` and XML tags `<>`, safely skipping string literals and comments while rejecting unclosed strings.
- **`DependencyBlocklistScanner`**: Strict blocklist rejecting unapproved 3rd-party dependencies and protecting device security (`SecurityRuleBrokenException`).

### 3. Dual-Compiler Execution Pathway (Kotlin & Native C++)
- **Pipeline A (Java / JVM)**:
  - Invokes embedded Eclipse Compiler for Java (`ecj.jar`) to emit bytecode `.class` binaries.
  - Passes bytecode into the Android D8 compiler (`d8.jar`) targeting Dalvik Executable format (`classes.dex`).
  - Executes loaded classes dynamically using `DexClassLoader`.
- **Pipeline B (C++ / Native)**:
  - Extracts and sets execution permissions on local ARM64 Clang/LLVM toolchain assets.
  - Compiles native source files into dynamic shared objects (`libDynamic.so`).
  - Dynamically links and loads native routines into memory via `System.load()`.

### 4. Jetpack Compose UI Dashboard
- **`SandboxDashboardScreen`**: Reactive Compose dashboard with project directory lists, multi-file payload input stage, and an interactive IDE terminal console emulator.
- **`SandboxViewModel`**: Asynchronous state management dispatching file operations and compiler engines onto background Coroutine IO threads.

---

## 🚀 Building & Running

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34 / NDK r25+
- JDK 17

### Gradle Commands
```bash
# Run validation and unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug
```
