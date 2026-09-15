# Box Mobile Package Compatibility Guide

## Table of Contents

1. [Overview](#1-overview)
2. [Manifest Specification (`boxfile.yml`)](#2-manifest-specification-boxfileyml)
3. [Bytecode Protection: `protect: false` vs `protect: true`](#3-bytecode-protection-protect-false-vs-protect-true)
4. [Supported Runtimes and Frameworks](#4-supported-runtimes-and-frameworks)
5. [Native Modules and Binary Dependencies](#5-native-modules-and-binary-dependencies)
6. [Android Platform Constraints and Shims](#6-android-platform-constraints-and-shims)
7. [Packaging Checklist for Mobile](#7-packaging-checklist-for-mobile)
8. [Troubleshooting Common Issues](#8-troubleshooting-common-issues)

---

## 1. Overview

**Box Mobile** (Box Runner) allows developers to execute `.box` packages
autonomously on Android devices without relying on virtual machines or Docker
daemons. Workloads execute directly on the Android Linux kernel using embedded
execution engines compiled for Android's Bionic C library:

- **Node.js**: v20 LTS / v23 / v26 compiled for Android ARM64 (`arm64-v8a`) and
  ARM32 (`armeabi-v7a`).
- **Python**: v3.11 / v3.14 modular runtimes with pre-linked dynamic libraries
  (`OpenSSL 3`, `libffi`, `SQLite 3`, `zlib`).

To ensure that an application runs reliably across desktop and mobile platforms,
packages must follow cross-architecture packaging rules detailed in this guide.

---

## 2. Manifest Specification (`boxfile.yml`)

The `boxfile.yml` manifest placed at the root of a package defines the runtime,
entrypoint, network configuration, and volume mappings.

### Canonical Example

```yaml
name: my-service
version: 1.0.0
runtime: python # or 'nodejs'

entrypoint: main.py
workdir: .

server:
  port: 8000

volumes:
  - app_data:/data

env_schema: env.example
```

### Supported Fields

- `name`: Unique identifier for the service.
- `runtime`: Declares the execution engine (`python` or `nodejs`). Can also be
  declared as an object: `runtime: { type: python, version: "3.11" }`.
- `entrypoint`: Relative path to the executable script or command line (for
  example `main.py`, `src/index.js`, or `node dist/server.js`).
- `workdir`: Working directory relative to sandbox root (defaults to `.`).
- `server`: Port configuration. Can be specified inline (`server: 3000`) or as a
  block containing `port` and optional parameters.
- `volumes`: List of volume mount specs in `name:target_path` format.
- `env_schema`: Path to the environment template (for example `env.example`),
  used by Box Mobile to discover required configuration variables and default
  values.

---

## 3. Bytecode Protection: `protect: false` vs `protect: true`

The single most critical compatibility factor when building packages with the
`box-cli` utility is bytecode protection.

> **Key Rule**: For packages to run on mobile, they MUST be built with
> `protect: false` (or with the `protect` option omitted).

```yaml
# boxfile.yml
name: my-app
runtime: python
entrypoint: main.py
protect: false # Required for cross-platform mobile compatibility
```

### Why `protect: true` Fails Across Architectures

When `box pack --protect` or `protect: true` is executed, the `box-cli` builder
compiles source code into binary bytecode on the development host:

#### A. Node.js V8 CachedData (`.jsc`)

- During protection, `box-cli` compiles JavaScript into binary V8 bytecode using
  the `vm.Script({ cachedData })` API.
- V8 bytecode is strictly bound to CPU instruction sets (x86_64 vs ARM64) and
  the host's internal V8 memory layout.
- When an Android ARM64 device attempts to execute x86_64 bytecode, the V8
  engine rejects the cache (`cachedDataRejected = true`) or crashes
  unexpectedly.

#### B. Python Bytecode Magic Numbers (`.pyc`)

- During protection, Python source code is compiled into `.pyc` files and `.py`
  sources are discarded.
- Python embeds a 4-byte magic number header corresponding to the exact Python
  minor version (e.g. `3531` for Python 3.12, `3571` for Python 3.14).
- If the packaging host runs a different Python minor version than the target
  device, the interpreter halts with:
  `ImportError: Bad magic number in .pyc file`.

#### The Solution: Plain Source (`protect: false`)

When `protect: false` is used:

- Original source code (`.js`, `.ts`, `.py`) is archived intact.
- The mobile device compiles source code on-the-fly using its native ARM64 CPU.
- **Outcome**: 100% cross-architecture portability across Windows, macOS, Linux,
  and Android.

---

## 4. Supported Runtimes and Frameworks

When packaged with `protect: false`, the following software categories are
tested and supported:

### Python Ecosystem

- **Web and APIs**: FastAPI, Flask, Starlette, Django, Bottle, Uvicorn.
  Box Mobile automatically binds `$PORT`, `$HOST`, `$UVICORN_PORT`, and
  `$UVICORN_HOST`.
- **Chatbots**: `discord.py`, `python-telegram-bot`, `aiogram`, `telebot`.
  Full TLS support, persistent WebSockets, and PEP 594 compatibility shims.
- **Automation and Scraping**: BeautifulSoup, Scrapy, Requests, HTTPX.
  Pure Python HTTP client engines work reliably out of the box.
- **Databases**: SQLite, SQLAlchemy, Peewee, Motor (async).
  Pre-linked Bionic SQLite native dynamic libraries are pre-installed.

### Node.js and TypeScript Ecosystem

- **Web and APIs**: Express, Fastify, NestJS, Koa, Hono.
  Full HTTP/1.1 and HTTP/2 protocol support.
- **Next.js**: Standalone deployments (`output: 'standalone'`).
  Automatic `next.config.ts` to `next.config.mjs` conversion bypasses missing
  ARM64 SWC binary requirements.
- **Chatbots**: `discord.js`, `telegraf`, `grammy`, `baileys`.
  Injected DNS fallback shim seamlessly handles network connections.
- **Databases**: Prisma, Knex, Kysely, TypeORM, Mongoose.
  Compatible using pure JavaScript, WebAssembly, or ARM64 query engines.

---

## 5. Native Modules and Binary Dependencies

Applications frequently depend on native binary extensions (`.node` in Node.js,
`.so` or `.pyd` in Python). Binary extensions compiled on Windows or macOS cannot
execute under Android's Linux kernel and Bionic C library.

### Guidelines for Node.js

- **Prefer Pure-JavaScript Libraries**:
  - Use `bcryptjs` instead of native `bcrypt`.
  - Use JavaScript or WebAssembly implementations for image processing instead
    of native `sharp` or `canvas` unless precompiled for Android ARM64.
- **Bundled Precompiled Engines**:
  - The Android runtime natively provides `OpenSSL`, `zlib`, and `libuv`.
  - Pure JavaScript database drivers (such as `pg`, `mysql2`, or `sqlite-wasm`)
    run without compilation steps.

### Guidelines for Python

- **Built-in Bionic Modules**:
  The embedded Python engine includes precompiled, Bionic-linked shared objects
  for standard library components:
  - `_sqlite3.so` (SQLite engine)
  - `_ssl.so` and `_hashlib.so` (OpenSSL 3 cryptographic operations)
  - `_ctypes.so` (Foreign Function Interface)
  - `_lzma.so` and `_bz2.so` (Compression utilities)
- **Automatic C-Extension Shims**:
  Box Runner automatically disables optional C accelerators for asynchronous
  networking libraries, ensuring reliable execution:

  ```bash
  MULTIDICT_NO_EXTENSIONS=1
  YARL_NO_EXTENSIONS=1
  FROZENLIST_NO_EXTENSIONS=1
  PROPCACHE_NO_EXTENSIONS=1
  ```

- **PEP 594 Legacy Compatibility**:
  Modern Python runtimes deprecated modules such as `audioop`. Box Runner
  provides transparent compatibility shims for libraries requiring these APIs.

---

## 6. Android Platform Constraints and Shims

Android imposes security and filesystem restrictions distinct from conventional
Linux distributions. Box Mobile bridges these transparently:

### Root `/data` Directory Shimming

On Android, `/data` is a privileged system partition. Scripts writing directly
to `/data/db.sqlite` or `/data/uploads` encounter `EACCES: permission denied`.

- **Box Runner VFS Redirection**:
  - Python: Injects `sitecustomize.py` to transparently rewrite file access
    calls (`open()`, `os.mkdir()`, etc.) targeting `/data` into the app's
    assigned sandbox storage.
  - Node.js: Injects filesystem hooks intercepting `fs` and `fs.promises` APIs.

### Network Port Restrictions

- Non-root Android processes cannot bind to privileged ports (`< 1024`, such as
  port `80` or `443`).
- Always configure servers to bind to ports `1024` or higher (e.g. `3000`,
  `8000`, or `8080`).
- Box Mobile injects the designated port into the runtime environment via
  `$PORT` and `$UVICORN_PORT`.

### Android DNS Resolution Shim

- Spawning child processes under Android Bionic can cause native `getaddrinfo()`
  queries to fail (`ENOTFOUND`).
- Box Mobile automatically supplies `box_dns_fallback.js` via `NODE_OPTIONS`,
  forwarding DNS queries to active connection resolvers and public fallback
  nameservers (`8.8.8.8`, `1.1.1.1`).

---

## 7. Packaging Checklist for Mobile

Before building your package for mobile deployment, verify the following steps:

1. [ ] **Manifest Defined**: `boxfile.yml` exists at the root of the project.
2. [ ] **Bytecode Protection Disabled**: Confirm `protect: false` is set or
   omitted.
3. [ ] **Port Variable Respected**: Server listens on `process.env.PORT` or
   `os.getenv("PORT")`.
4. [ ] **Pure Dependencies**: Native binaries compiled for Windows/macOS are
   replaced with pure JS/Python or WASM alternatives.
5. [ ] **Environment Template**: `env.example` lists all required API tokens and
   default values.
6. [ ] **Dependencies Packaged**: Run `box pack` to bundle dependencies into
   `_box_lib/` (Python) or `node_modules/` (Node.js).

---

## 8. Troubleshooting Common Issues

### Bad Magic Number in `.pyc`

- **Root Cause**: The package was compiled with `protect: true` using a Python
  version different from the target device runtime.
- **Resolution**: Set `protect: false` in `boxfile.yml` and rebuild the package
  with `box pack`.

### V8 Bytecode Rejected (`cachedDataRejected`)

- **Root Cause**: The package was compiled with `protect: true` on an x86_64
  desktop CPU.
- **Resolution**: Set `protect: false` in `boxfile.yml` and rebuild the package
  with `box pack`.

### Port Permission Denied (`EACCES: permission denied, bind`)

- **Root Cause**: The server attempted to bind to a privileged system port
  below 1024 (e.g., port 80 or 443).
- **Resolution**: Reconfigure the service port to `1024` or higher (e.g., `8000`
  or `3000`).

### Native Module Missing (`Cannot find module ... .node`)

- **Root Cause**: The package depends on a native C++ addon compiled for
  Windows or macOS.
- **Resolution**: Replace the dependency with a pure JavaScript or WebAssembly
  alternative (e.g., use `bcryptjs` instead of `bcrypt`).

### SSL Verification Failure (`CERTIFICATE_VERIFY_FAILED`)

- **Root Cause**: Outdated or missing certificate authority bundle on device.
- **Resolution**: Box Mobile configures CA bundles automatically; ensure the
  device system date and time are accurate.

### Hostname Resolution Failure (`ENOTFOUND` / `EAI_AGAIN`)

- **Root Cause**: Android Bionic DNS restrictions affecting non-root child
  processes.
- **Resolution**: Handled automatically by the injected `box_dns_fallback.js`
  shim and active interface DNS lookup.
