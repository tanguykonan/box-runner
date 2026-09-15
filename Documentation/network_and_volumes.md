# Box Runner Networking and Persistent Volumes Guide

## Table of Contents

1. [Overview](#1-overview)
2. [Networking Architecture and Port Binding](#2-networking-architecture-and-port-binding)
3. [Network Modes: Localhost vs LAN Access](#3-network-modes-localhost-vs-lan-access)
4. [Android DNS Resolution Mechanism](#4-android-dns-resolution-mechanism)
5. [Remote Debugging and Port Forwarding](#5-remote-debugging-and-port-forwarding)
6. [Persistent Volumes Architecture](#6-persistent-volumes-architecture)
7. [Transparent VFS Volume Shims](#7-transparent-vfs-volume-shims)
8. [Storage Lifecycle: Sandbox vs Volumes](#8-storage-lifecycle-sandbox-vs-volumes)
9. [Developer Best Practices](#9-developer-best-practices)

---

## 1. Overview

**Box Runner** provides enterprise-grade networking and persistent storage
primitives designed specifically for Android user-space constraints. This guide
covers the operational principles of network port bindings, local loopback
versus LAN exposure, DNS resolution workarounds, atomic symbolic link volume
mounting, and virtual filesystem (VFS) shimming.

---

## 2. Networking Architecture and Port Binding

When deploying web servers, microservices, or API backends (e.g. FastAPI,
Express.js, Flask, NestJS, or Hono) inside Box Runner:

### Unprivileged Port Requirements

- Under the Linux kernel and Android security policies, non-root processes are
  prohibited from binding to privileged ports below 1024 (e.g. 80, 443, 22).
- All Box services must bind to unprivileged ports (`>= 1024`), such as `3000`,
  `8000`, `8080`, or `5000`.

### Automated Environment Injection

When a port is defined in `boxfile.yml` (e.g. `server: { port: 8000 }`), Box
Runner injects the following standard environment variables upon process
spawning:

```bash
PORT=8000
HOST=127.0.0.1
UVICORN_PORT=8000
UVICORN_HOST=127.0.0.1
```

If the service is a background worker or bot (and no explicit `server:` block is
declared in the manifest), Box Runner suppresses port assignments, preventing
unnecessary port allocations.

---

## 3. Network Modes: Localhost vs LAN Access

Box Runner supports two operational network modes:

### Local Access Mode (Localhost Default)

- **Binding Address**: `127.0.0.1` (loopback interface).
- **Access Boundary**: Accessible strictly from the local device (e.g. via the
  on-device mobile browser at `http://localhost:8000`).
- **Security Posture**: Inaccessible to external devices on the same Wi-Fi
  network, providing maximal security against unauthorized access.

### Local Area Network Access Mode (LAN Sharing)

- **Binding Address**: `0.0.0.0` (all active network interfaces).
- **Configuration**: Enabled via the **Local Network Access** toggle in the
  application settings.
- **Access Boundary**: Enables any workstation, phone, or tablet on the same
  Wi-Fi network to connect to your service via your device's LAN IP address:

  ```text
  http://<DEVICE_IP_ADDRESS>:<PORT>
  Example: http://192.168.1.45:8000
  ```

---

## 4. Android DNS Resolution Mechanism

On Android, child processes spawned outside the Android framework by non-root
applications frequently encounter `ENOTFOUND`, `EAI_AGAIN`, or `EAI_FAIL` errors
when attempting to resolve external hostnames via Bionic libc's `getaddrinfo()`.

### The Box Runner Solution

1. **Active Resolver Discovery**: Box Runner inspects active network interfaces
   via Android's `ConnectivityManager`, extracting live DNS servers from the
   active Wi-Fi or cellular connection and passing them via `BOX_DNS_SERVERS`.
2. **Transparent Resolver Injection (`box_dns_fallback.js`)**: For Node.js
   applications, Box Runner automatically injects a DNS interceptor via
   `NODE_OPTIONS="--require /path/to/box_dns_fallback.js"`.
3. **Multi-Tier Fallback**: Intercepts `dns.lookup()` calls and falls back to
   direct asynchronous queries (`dns.resolve4()` and `dns.resolve6()`) against
   both active interface servers and public resolvers (`8.8.8.8`, `1.1.1.1`,
   `8.8.4.4`, `1.0.0.1`).

---

## 5. Remote Debugging and Port Forwarding

You can inspect and interact with services hosted inside Box Runner directly from
your desktop workstation using Android Debug Bridge (ADB):

### Forward Mobile Service to Workstation

To access a mobile web service listening on port 8000 from your development PC's
browser:

```bash
adb forward tcp:8000 tcp:8000
```

Navigate to `http://localhost:8000` in your desktop browser.

### Reverse Forward Workstation Service to Mobile

To allow a service running inside Box Runner on your phone to access a database
(e.g. PostgreSQL or Redis) running on your development PC:

```bash
adb reverse tcp:5432 tcp:5432
```

The mobile workload can connect to the database via `localhost:5432`.

---

## 6. Persistent Volumes Architecture

Box Runner separates ephemeral application code from durable state:

```text
/data/user/0/dev.tanguykonan.boxrunner/files/
├── sandboxes/
│   └── app_<id>/             # Ephemeral sandbox directory
│       ├── main.py
│       └── data/ ────────────► Symbolic link to physical volume
└── volumes/
    └── my_database/          # Persistent physical directory
        └── app.sqlite        # Durable database file
```

### Atomic Symbolic Link Mounting

- **Physical Storage**: Volumes are stored independently in
  `files/volumes/<volume_name>/`.
- **Mount Link**: When an application declares a volume mount (for example
  `my_database:/data`), `SandboxManager` creates an atomic symbolic link using
  `android.system.Os.symlink()` from the target path inside the sandbox directly
  to the physical volume directory.
- **Initial Data Pre-seeding**: If an archive contains default seed data or
  configuration files at the mount point, and the physical volume is currently
  empty, Box Runner copies the default files into the physical volume before
  establishing the symbolic link.
- **Safe Deletion Policy**: When a sandbox is deleted or re-extracted,
  `SandboxManager` explicitly checks `Files.isSymbolicLink()`. It deletes only
  the symbolic link itself, ensuring that physical volume data is never deleted.

### Environment Variable Remapping

Box Runner automatically exports paths to mounted volumes:

```bash
VOLUME_MY_DATABASE=/data/user/0/dev.tanguykonan.boxrunner/files/volumes/my_database
BOX_VOLUME_MY_DATABASE=/data/user/0/dev.tanguykonan.boxrunner/files/volumes/my_database
DATA_DIR=/data/user/0/dev.tanguykonan.boxrunner/files/volumes/my_database
```

Any configuration variable referencing the mount target (for example
`DB_PATH=/data/app.db`) is automatically rewritten to point to the resolved
absolute path.

---

## 7. Transparent VFS Volume Shims

Standard server applications often contain hardcoded paths targeting `/data`
(such as `/data/app.db` or `/data/uploads/`). On Android, `/data` is a
privileged root partition owned by the system, causing unhandled `EACCES`
permission errors.

Box Runner eliminates this friction through transparent Virtual Filesystem (VFS)
redirection:

### Python VFS Redirection (`sitecustomize.py`)

Box Runner automatically writes a custom `sitecustomize.py` script into the
sandbox root. This hook intercepts filesystem calls:

- `builtins.open()`
- `os.open()`, `os.stat()`, `os.lstat()`
- `os.mkdir()`, `os.makedirs()`, `os.rmdir()`
- `os.remove()`, `os.unlink()`, `os.rename()`, `os.replace()`
- `os.scandir()`, `os.listdir()`, `os.chmod()`, `os.access()`

Any file access targeting `/data` (excluding protected Android system
directories like `/data/user` or `/data/app`) is automatically and
transparently redirected to the app's mounted volume directory.

### Node.js VFS Redirection (`box_dns_fallback.js`)

In Node.js workloads, `box_dns_fallback.js` intercepts all synchronous and
asynchronous methods of the `fs` and `fs.promises` modules (`open`, `readFile`,
`writeFile`, `stat`, `mkdir`, `unlink`, `rmdir`, `access`, etc.), seamlessly
redirecting `/data` paths to the mounted persistent volume.

---

## 8. Storage Lifecycle: Sandbox vs Volumes

Understanding the distinction between sandbox storage and persistent volumes is
essential for reliable deployments:

### Sandbox Storage (`files/sandboxes/app_<id>/`)

- **Scope**: Exclusive to a single application deployment.
- **Content**: Application code, bundled libraries (`node_modules/`,
  `_box_lib/`), runtime configuration files, and temporary caches.
- **Lifecycle**: Ephemeral. When an application package is updated or
  re-extracted, the sandbox directory is wiped and replaced.

### Persistent Volumes (`files/volumes/<volume_name>/`)

- **Scope**: Decoupled from application packages; reusable across multiple
  deployments.
- **Content**: SQLite databases, user-uploaded media, durable key-value stores,
  and long-term logs.
- **Lifecycle**: Persistent. Survives application restarts, package updates, and
  re-extractions. Retained until explicitly deleted by the user from the Volumes
  tab.

---

## 9. Developer Best Practices

Follow these engineering recommendations when building packages for Box Mobile:

### 1. Structure Database Paths Responsibly

Always reference environment variables or relative paths rather than assuming a
root filesystem layout:

- **Python Example**:

  ```python
  import os

  # Respect DATA_DIR or fallback to a relative path
  storage_dir = os.environ.get("DATA_DIR", "./data")
  os.makedirs(storage_dir, exist_ok=True)
  db_path = os.path.join(storage_dir, "application.sqlite")
  ```

- **Node.js Example**:

  ```javascript
  const path = require('path');
  const fs = require('fs');

  const storageDir = process.env.DATA_DIR || path.join(__dirname, 'data');
  if (!fs.existsSync(storageDir)) {
    fs.mkdirSync(storageDir, { recursive: true });
  }
  const dbPath = path.join(storageDir, 'application.sqlite');
  ```

### 2. Optimize SQLite for Mobile Flash Storage

When utilizing SQLite inside a persistent volume on Android, configure Write-Ahead
Logging (WAL) mode to optimize write performance and mitigate lock contention:

```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA busy_timeout = 5000;
```

### 3. Graceful Port Binding

In your application entrypoint, always bind to the host and port passed via
environment variables:

```javascript
const port = parseInt(process.env.PORT, 10) || 3000;
const host = process.env.HOST || '127.0.0.1';

server.listen(port, host, () => {
  console.log(`Server listening on http://${host}:${port}`);
});
```
