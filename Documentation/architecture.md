# Box Runner Technical Architecture

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Layered System Design](#2-layered-system-design)
3. [Hermetic Sandbox Model](#3-hermetic-sandbox-model)
4. [Self-Contained Execution Engines](#4-self-contained-execution-engines)
5. [Cross-Platform Compatibility Shims](#5-cross-platform-compatibility-shims)
6. [Process Lifecycle and Persistence](#6-process-lifecycle-and-persistence)
7. [Networking and Port Binding](#7-networking-and-port-binding)
8. [Security Boundary and Isolation](#8-security-boundary-and-isolation)
9. [Archive Format and Extraction Pipeline](#9-archive-format-and-extraction-pipeline)
10. [Telemetry and Resource Monitoring](#10-telemetry-and-resource-monitoring)

---

## 1. Architecture Overview

**Box Runner** is an ultra-lightweight, self-contained container runtime
engineered specifically for the Android operating system. Traditional container
platforms like Docker rely on Linux kernel cgroups, namespace isolation, and a
privileged background daemon (`dockerd`), none of which are accessible inside
standard unrooted Android application sandboxes.

Emulation alternatives such as QEMU or Termux PRoot incur heavy CPU translation
penalties (3x-10x overhead) and consume excessive memory.

Box Runner circumvents these limitations by executing processes **natively on
the Android Linux kernel** within the application's assigned Linux UID/GID
sandbox boundary.

```text
┌─────────────────────────────────────────────────────────────┐
│                 User Interface (Android UI)                 │
│         Jetpack Compose • Material 3 • Navigation           │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Flows
┌──────────────────────────────▼──────────────────────────────┐
│                    Core Engine Layer                        │
│  ┌───────────────────────┐        ┌───────────────────────┐  │
│  │    SandboxManager     │◄──────►│   BoxProcessManager   │  │
│  │ (TarZstd / Volumes)   │        │ (ProcessBuilder / Env)│  │
│  └───────────────────────┘        └───────────┬───────────┘  │
│  ┌───────────────────────┐                    │ Spawns        │
│  │    RuntimeManager     │                    ▼              │
│  │ (Bionic ELF Assets)   │        ┌───────────────────────┐  │
│  └───────────────────────┘        │  Native Child Process │  │
│  ┌───────────────────────┐        │ (Python / Node.js ELF)│  │
│  │   BoxRunnerService    │        └───────────────────────┘  │
│  │(Foreground + WakeLock)│                                   │
│  └───────────────────────┘                                   │
└─────────────────────────────────────────────────────────────┘
```

### Architectural Principles

- **Root-less and Daemon-less**: Runs entirely within standard Android user
  space without requiring root access, custom ROMs, or background daemons.
- **Zero-Overhead Native Execution**: Workloads run as native ELF binaries
  compiled against Android Bionic libc, achieving bare-metal CPU performance.
- **Ultra-Low Memory Footprint**: Idle memory consumption remains between 15 MB
  and 35 MB per active service, compared to 400 MB+ for virtualized systems.
- **Instantaneous Spawning**: Subprocess startup latency is under 100 ms via
  direct POSIX `fork()`/`execve()` primitives wrapped by Java `ProcessBuilder`.

---

## 2. Layered System Design

The Box Mobile application follows modern Android architectural guidelines with
strict separation of concerns:

### Presentation Layer

- **Framework**: Modern declarative UI built with Jetpack Compose and Material
  Design 3.
- **State Management**: Model-View-ViewModel (MVVM) pattern utilizing Kotlin
  Coroutines, `StateFlow`, and Unidirectional Data Flow (UDF).
- **Live Terminal Console**: Subprocess standard output and standard error
  streams are buffered in a thread-safe circular list (retaining the most recent
  400 entries) and streamed reactively to the user interface.

### Domain and Data Layer

- **Repositories**: `BoxRepository`, `AuthRepository`, and
  `UserPreferencesRepository`.
- **Metadata Persistence**: Structured JSON persistence files
  (`apps_metadata.json` and `volumes_metadata.json`) stored within the private
  internal storage directory (`files/sandboxes/` and `files/volumes/`).

### Core Orchestration Layer

- **`SandboxManager`**: Manages filesystem provisioning, archive extraction,
  symbolic link volume attachment, manifest parsing, and cache cleanup.
- **`BoxProcessManager`**: Configures process environments, builds execution
  commands, spawns child processes, monitors process lifecycles, and streams
  diagnostic logs.
- **`RuntimeManager`**: Unpacks, verifies, and maintains prebuilt Bionic ELF
  interpreters (Python 3.11/3.14, Node.js 20) and shared library dependencies.
- **`SystemStatsManager`**: Queries device-level CPU metrics and process-level
  resident set size (RSS) via `/proc`.

---

## 3. Hermetic Sandbox Model

Each deployed application is isolated within an exclusive, private directory
located inside Android's internal app storage:

```text
/data/user/0/com.box.android/files/sandboxes/app_<timestamp>/
├── boxfile.yml            # Application manifest & metadata
├── main.py / index.js     # Resolved service entrypoint
├── _box_lib/              # Packaged Python libraries (box build)
├── node_modules/          # Packaged Node.js dependencies
├── sitecustomize.py       # Transparent VFS volume hook (Python)
├── box_dns_fallback.js    # DNS resolver & VFS volume hook (Node.js)
├── .env                   # Injected environment configuration
├── .env.local             # Local overrides and port assignments
└── data/                  # Symlink pointing to persistent volume
```

### Persistent Volume Isolation

Box volumes are isolated directories hosted under:

```text
/data/user/0/com.box.android/files/volumes/<volume_name>/
```

- **Symlink Mounting**: When an application specifies a volume mount (for
  example `my_db:/data`), `SandboxManager` creates an atomic symbolic link using
  `android.system.Os.symlink()` from the target path inside the sandbox to the
  physical directory in `files/volumes/`.
- **Pre-seeding Support**: If the sandbox image already contains default
  assets at the target mount point, those assets are copied into the physical
  volume on initial mount before the symlink is established.
- **Safe Traversal and Unlinking**: Recursive deletion operations strictly check
  `Files.isSymbolicLink()` to ensure volume data is never deleted when an
  application sandbox is removed.

---

## 4. Self-Contained Execution Engines

Android does not utilize GNU C Library (`glibc`); it employs Google's
lightweight **Bionic C library**. Consequently, standard Linux binaries cannot
run on Android out of the box.

### Prebuilt Bionic ELF Binaries

Box Runner embeds prebuilt runtime engines compiled explicitly against Android
Bionic:

- **Python**: Modular Python 3.11 / 3.14 builds containing dynamic modules
  (`lib-dynload`), standard library sources, and required shared libraries.
- **Node.js**: Optimized Node.js v20 LTS engine linked against Android libc++
  and OpenSSL.

### Dynamic Linker Shims (`LD_LIBRARY_PATH`)

When launching a workload, `BoxProcessManager` configures the dynamic linker
search path to resolve all dependencies from isolated application directories
before system libraries:

```bash
LD_LIBRARY_PATH=<runtime>/lib:<runtime>/lib/python3.14/lib-dynload:\
<appNativeLibDir>:/system/lib64:/vendor/lib64:/system/lib:/vendor/lib
```

This allows full dynamic linking for:

- `libc++_shared.so` (Android C++ Standard Library)
- `libcrypto.so` and `libssl.so` (OpenSSL / BoringSSL cryptography)
- `libffi.so` (Foreign Function Interface for dynamic bindings)
- `libz.so`, `libsqlite3.so`, and `libandroid-support.so`

---

## 5. Cross-Platform Compatibility Shims

Running unmodified server software on Android introduces unique environmental
challenges. Box Runner resolves these transparently through dynamic shims:

### A. Node.js DNS Resolution Shim (`box_dns_fallback.js`)

On Android, non-root child processes spawned outside the Android framework
frequently encounter `ENOTFOUND`, `EAI_AGAIN`, or `EAI_FAIL` errors when calling
Bionic's native `getaddrinfo()`.

- **Mechanism**: Injected automatically via
  `NODE_OPTIONS="--require /path/to/box_dns_fallback.js"`.
- **Active Interface Resolution**: Queries the active network via Android
  `ConnectivityManager` and passes active DNS servers via `BOX_DNS_SERVERS`.
- **Asynchronous Fallback**: Intercepts `dns.lookup()` and falls back to
  `dns.resolve4()` and `dns.resolve6()` using Google (`8.8.8.8`) and Cloudflare
  (`1.1.1.1`) public resolvers.

### B. Transparent `/data` Volume Virtualization (VFS Hook)

On Android devices, `/data` is a restricted root-level partition owned by the
system. Applications hardcoding paths such as `/data/db.sqlite` or `/data/files/`
fail with `Permission denied`.

- **Python Interception (`sitecustomize.py`)**:
  Hooks `builtins.open`, `os.open`, `os.stat`, `os.mkdir`, `os.makedirs`,
  `os.scandir`, and related file operations, redirecting any path starting with
  `/data` (excluding system paths like `/data/user`) to the sandbox volume.
- **Node.js Interception (`box_dns_fallback.js`)**:
  Transparently wraps synchronous and asynchronous `fs` and `fs.promises`
  methods (`open`, `readFile`, `writeFile`, `stat`, `mkdir`, `unlink`, etc.).

### C. Pure-Python C-Extension Fallback Flags

Certain Python asynchronous networking packages compile optional C acceleration
extensions that may segfault under non-glibc Bionic environments. Box Runner
enforces reliable pure-Python fallbacks:

```bash
MULTIDICT_NO_EXTENSIONS=1
YARL_NO_EXTENSIONS=1
FROZENLIST_NO_EXTENSIONS=1
PROPCACHE_NO_EXTENSIONS=1
```

### D. Next.js TypeScript Config Adaptation

Next.js on ARM64 Android does not offer precompiled `@next/swc-android-arm64`
binaries. When a project contains `next.config.ts`, Next.js invokes SWC solely to
transpile the configuration.

- `SandboxManager.prepareNextJsConfig()` automatically strips TypeScript type
  annotations and imports, converting `next.config.ts` into native ESM
  `next.config.mjs`.
- Sets `__NEXT_NODE_NATIVE_TS_LOADER_ENABLED="true"`.
- Allows Next.js standalone servers to boot instantaneously without requiring
  heavy compilation tools.

### E. Root CA Bundle Provisioning

To prevent SSL/TLS handshake failures (`CERTIFICATE_VERIFY_FAILED`), Box Runner:

1. Locates system and extracted certificates (`cacert.pem`).
2. Configures standard environment indicators:

```bash
SSL_CERT_FILE=/path/to/cacert.pem
REQUESTS_CA_BUNDLE=/path/to/cacert.pem
CURL_CA_BUNDLE=/path/to/cacert.pem
NODE_EXTRA_CA_CERTS=/path/to/cacert.pem
SSL_CERT_DIR=/system/etc/security/cacerts
```

### F. POSIX Filesystem Environment

Standard UNIX scripts rely on conventional environment variables for temporary
storage and home directory resolution:

```bash
TMPDIR=/data/user/0/com.box.android/cache
TEMP=/data/user/0/com.box.android/cache
TMP=/data/user/0/com.box.android/cache
HOME=/data/user/0/com.box.android/files/sandboxes/app_<timestamp>
PWD=<execWorkdir>
SHELL=/system/bin/sh
```

---

## 6. Process Lifecycle and Persistence

Box Runner guarantees continuous background execution for long-running services
such as APIs, background bots, and schedulers:

### Foreground Service Architecture

- **`BoxRunnerService`**: Promotes itself to an Android Foreground Service using
  `ServiceCompat.startForeground()`.
- **System Service Types**: Declares `specialUse` and `dataSync` foreground
  service types under Android 14+ (API level 34).
- **Persistent Notification**: Displays real-time active service counts and
  provides one-tap access back to the dashboard.
- **Sticky Lifecycle**: Returns `START_STICKY` from `onStartCommand` to request
  automatic framework recreation if killed under critical memory pressure.

### CPU Power Management (`WakeLock`)

- Acquires a `PowerManager.PARTIAL_WAKE_LOCK` with tag
  `BoxRunner::ExecutionEngineWakeLock`.
- Prevents the Android Linux kernel from suspending the CPU when the device
  screen turns off.

### Process Teardown and Cleanup

- Graceful termination sends `SIGTERM` via `Process.destroy()`.
- Automatically terminates asynchronous stream reader coroutines.
- Drops references and updates app state to `STOPPED`.

---

## 7. Networking and Port Binding

On Android, network operations inside user applications are governed by Linux
kernel networking rules and Android framework permissions:

### Port Allocation Rules

- **Unprivileged Ports (`>= 1024`)**: Non-root Android processes cannot bind to
  privileged ports (`< 1024`, such as `80` or `443`). All Box services must
  bind to ports `1024` or higher (defaulting to ports like `3000`, `8000`, or
  `8080`).
- **Loopback Binding (`127.0.0.1`)**: By default, web services bind to the
  local loopback interface, allowing access only from the local device or via
  ADB port forwarding.
- **Local Area Network Access (`0.0.0.0`)**: When the user enables LAN access,
  Box Runner configures the process environment with `HOST=0.0.0.0`, exposing
  the service to other devices on the same Wi-Fi network.

### Environment Variable Forwarding

Web and API servers are provided standard networking variables:

```bash
PORT=8000
HOST=127.0.0.1
UVICORN_PORT=8000
UVICORN_HOST=127.0.0.1
```

---

## 8. Security Boundary and Isolation

Box Runner enforces a multi-tier security model rooted in the Android OS:

### UID/GID Process Isolation

- Workloads run strictly under the application's unique Linux User ID (for
  example `u0_a245`).
- Processes cannot read or modify data from other Android applications.
- Processes cannot access root partitions or modify system files.

### Symlink Containment and Anti-Traversal

- When extracting archives, `SandboxManager` sanitizes entry paths to prevent
  directory traversal exploits (such as Tar Slip or Zip Slip).
- Volume mount deletions use strict non-traversal logic: the symbolic link is
  unlinked, leaving the underlying physical volume directory intact.

---

## 9. Archive Format and Extraction Pipeline

Applications packaged with the `box-cli` utility are delivered as `.box`
archives.

```text
my-app.box (Zstandard Archive)
└── GNU TAR Stream
    ├── boxfile.yml           # Declarative manifest
    ├── main.py / index.js    # Entrypoint
    └── node_modules/ / lib/  # Dependencies
```

### Extraction Pipeline

1. **Magic Byte Detection**: Inspects the first 8 bytes of the archive:
   - `0x28 0xB5 0x2F 0xFD`: Zstandard compression (`ZstdInputStream`).
   - `0x1F 0x8B`: Standard Gzip compression (`GZIPInputStream`).
   - `0xFD 0x37 0x7A 0x58 0x5A 0x00`: XZ compression (`XZInputStream`).
2. **Streaming Decompression**: Decompresses directly into Apache Commons
   Compress `TarArchiveInputStream` without writing large intermediate tarballs
   to flash storage.
3. **GNU LongLink Support**: Fully supports GNU `@LongLink` headers to unpack
   deep dependency structures exceeding traditional 100-character TAR path
   limits.
4. **Symlink Preservation**: Restores archive symbolic links directly on the
   target filesystem using `android.system.Os.symlink()`.

---

## 10. Telemetry and Resource Monitoring

Box Runner continuously tracks runtime health metrics:

- **Resident Set Size (RSS)**: Queries `/proc/<pid>/statm` to inspect physical
  memory pages utilized by the child process, multiplying by page size (4096
  bytes) for precise memory metrics.
- **CPU Metrics**: Samples system-wide `/proc/stat` and process-level
  `/proc/<pid>/stat` to calculate usage percentages.
- **Uptime Tracking**: Measures continuous execution duration from process
  inception.
