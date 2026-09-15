# Box Runner Local Development and Build Guide

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites and Toolchain](#2-prerequisites-and-toolchain)
3. [Local Environment Setup](#3-local-environment-setup)
4. [Local Build Workflows](#4-local-build-workflows)
5. [Device Deployment and Testing](#5-device-deployment-and-testing)
6. [Codebase Orientation](#6-codebase-orientation)
7. [Pre-Submission Verification](#7-pre-submission-verification)

---

## 1. Overview

This guide is designed for developers and open-source contributors who want to
set up their local environment, compile **Box Runner** (Box Mobile) from source,
test features on physical hardware, and submit code contributions.

> **Note on Releases**: Official production releases and APK signing are
> handled automatically via GitHub Actions CI/CD pipelines. As a local
> developer or contributor, you only need to work with local debug builds.

---

## 2. Prerequisites and Toolchain

Ensure your workstation has the following tools installed:

### Java Development Kit (JDK)

- **Version**: JDK 17 (LTS) or JDK 21 (LTS).
- The JetBrains Runtime (`jbr`) bundled with Android Studio is strongly
  recommended.
- Set `JAVA_HOME` to point to this JDK directory.

### Android Studio and SDK

- **IDE**: Android Studio Iguana (2023.2.1), Koala (2024.1.1), Ladybug
  (2024.2.1), or newer.
- **SDK Platform**: Android SDK Platform 35 / 36 (`compileSdk = 36`).
- **Build Tools**: Build-Tools 35.0.0 or newer.
- **Minimum SDK (`minSdk`)**: API 24 (Android 7.0 Nougat).
- **Target SDK (`targetSdk`)**: API 28 (Android 9.0 Pie). This configuration is
  a deliberate engineering requirement to allow spawned child processes to
  execute Bionic ELF binaries directly from app-internal storage.

### Testing Hardware

- **Physical Device**: An ARM64 physical Android phone (`arm64-v8a`) with USB
  debugging enabled.
- **Emulators**: Standard x86_64 PC emulators cannot execute native ARM64
  binaries. Use an ARM-based emulator (such as an Apple Silicon Mac running an
  ARM64 system image) or a physical test phone.

---

## 3. Local Environment Setup

### Step 1: Clone the Repository

Clone the project to your development machine:

```bash
git clone https://github.com/tanguykonan/box-runner.git
cd box-runner
```

### Step 2: Configure `local.properties`

Create a `local.properties` file in the project root pointing to your Android
SDK:

- **Windows**:

  ```properties
  sdk.dir=C:\\Users\\<Username>\\AppData\\Local\\Android\\Sdk
  ```

- **macOS**:

  ```properties
  sdk.dir=/Users/<Username>/Library/Android/sdk
  ```

- **Linux**:

  ```properties
  sdk.dir=/home/<Username>/Android/Sdk
  ```

### Step 3: Verify Java Version

Ensure that Java 17 or higher is active in your terminal:

```bash
java -version
```

If needed, set `JAVA_HOME` explicitly in your shell:

- **Windows (PowerShell)**:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```

- **Linux / macOS**:

  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```

---

## 4. Local Build Workflows

You can develop and compile Box Runner using Android Studio or the command-line
Gradle wrapper (`gradlew` on UNIX or `gradlew.bat` on Windows).

### Method A: Android Studio (Recommended)

1. Open Android Studio and choose **Open an Existing Project**.
2. Select the cloned `box-runner` repository root.
3. Wait for the Gradle project synchronization to complete.
4. Select your connected physical device in the device dropdown.
5. Click **Run 'app'** (or press `Shift + F10`) to compile, install, and launch
   the debug build.

### Method B: Command-Line (Gradle CLI)

#### Fast Incremental Compilation Check

To quickly verify that your Kotlin code, Jetpack Compose layouts, and Hilt wiring
compile cleanly without assembling the full APK:

```bash
./gradlew compileDebugSources
```

#### Build Local Debug APK

To compile an unminified debug APK suitable for local installation:

```bash
./gradlew assembleDebug
```

The resulting package is placed at:
`app/build/outputs/apk/debug/app-debug.apk`

#### Clean Build Cache

If you encounter unexpected Gradle daemon or build cache issues:

```bash
./gradlew clean --no-build-cache
./gradlew --stop
```

---

## 5. Device Deployment and Testing

### Install Debug APK via ADB

With your device connected via USB and USB Debugging active:

```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

### Stream Runtime Engine Logs (Logcat)

To monitor execution engines, process spawning, and sandbox provisioning in real
time:

```bash
adb logcat -v time -s BoxProcessManager BoxRunnerService SandboxManager
```

### Forward Web Ports for Local Testing

When testing web applications running inside Box Runner (e.g., FastAPI or
Express on port 8000), forward the port from your phone to your development PC:

```bash
adb forward tcp:8000 tcp:8000
```

Open `http://localhost:8000` in your computer's browser to test the mobile app.

### Inspect Sandbox Storage on Device

On debug builds, inspect the internal sandbox directories directly:

```bash
adb shell run-as com.box.android ls -la files/sandboxes/
```

Check active child processes spawned by Box Runner:

```bash
adb shell "ps -A | grep -E 'python|node'"
```

---

## 6. Codebase Orientation

The codebase follows modern Android architecture under
`app/src/main/java/com/box/android/`:

```text
com/box/android/
├── MainActivity.kt               # Single-activity host for Compose
├── core/
│   ├── permission/               # Notification and system permissions
│   ├── process/                  # Subprocess spawning and log streaming
│   │   ├── BoxProcessManager.kt  # ProcessBuilder orchestrator and shims
│   │   └── AppLogEntry.kt        # Reactive log entry data model
│   ├── runtime/                  # Native Bionic ELF runtime manager
│   │   ├── RuntimeManager.kt     # Unpacker and validator for engines
│   │   └── RuntimeInfo.kt        # Metadata model for installed runtimes
│   ├── sandbox/                  # Isolation, TAR extraction, volumes
│   │   └── SandboxManager.kt     # Zstandard unpacker and symlink manager
│   ├── service/                  # Android persistent background service
│   │   └── BoxRunnerService.kt   # ForegroundService and WakeLock handler
│   └── system/                   # Hardware telemetry metrics
│       └── SystemStatsManager.kt # /proc reader for CPU and RSS memory
├── data/
│   ├── auth/                     # Authentication repository
│   ├── box/                      # App and Volume domain models
│   └── local/                    # SharedPreferences and settings
├── feature/                      # Jetpack Compose screen implementations
│   ├── home/                     # Service list and status cards
│   ├── service/details/          # App details, environment sheet, console
│   ├── volume/                   # Persistent volume management
│   ├── resources/                # Hardware resource monitoring screen
│   ├── settings/                 # LAN access and system preferences
│   └── profile/                  # Account and version information
└── navigation/                   # Compose navigation graph routing
```

---

## 7. Pre-Submission Verification

Before opening a pull request, run the following verification checks locally:

### 1. Run Unit Tests

Verify that all JVM unit tests pass:

```bash
./gradlew test
```

### 2. Run Android Lint

Check for code quality and resource issues:

```bash
./gradlew lintDebug
```

### 3. Verify Documentation Standards

If your changes include modifications to files in `Documentation/`, confirm that:

- All content is written in English.
- No emojis are used in the text or headers.
- All lines are wrapped to 80 characters or fewer (MD013).
- Every code fence declares an explicit language identifier.
- The documentation passes the project markdown linter:

```bash
npx markdownlint-cli "Documentation/*.md"
```
