<p style="text-align: center;">
  <img
    src="https://boxhub.paxiz.org/favicon.ico"
    alt="Box Runner Logo"
    width="128"
  />
</p>

<h1 style="text-align: center;">Box Runner for Android</h1>

<p style="text-align: center;">
  Ultra-lightweight native container runtime engineered for the Android OS.
  <br />
  Run Python, Node.js, web microservices directly on Bionic libc without root.
</p>

[![Build Debug APK](https://github.com/tanguykonan/box-runner/actions/workflows/build-debug-apk.yml/badge.svg?branch=master)](https://github.com/tanguykonan/box-runner/actions/workflows/build-debug-apk.yml)
[![Run Unit Tests](https://github.com/tanguykonan/box-runner/actions/workflows/run-unit-tests.yml/badge.svg?branch=master)](https://github.com/tanguykonan/box-runner/actions/workflows/run-unit-tests.yml)
[![Lint Documentation](https://github.com/tanguykonan/box-runner/actions/workflows/lint-documentation.yml/badge.svg?branch=master)](https://github.com/tanguykonan/box-runner/actions/workflows/lint-documentation.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Overview

Box Runner (Box Mobile) provides a dedicated execution runtime for `.box`
software packages on Android devices. Rather than relying on heavyweight
virtual machines (QEMU) or dynamic translation layers (PRoot) that impose
severe performance penalties, Box Runner executes workloads natively as
Android Bionic ELF processes inside an isolated Linux sandbox.

### Core Value Highlights

- **Native Execution**: Zero CPU translation overhead, delivering instant
  process startup (< 100 ms), bare-metal processing performance.
- **Minimal Footprint**: Operates with an active memory baseline of 15 MB to
  35 MB per running workload.
- **Unprivileged Operation**: Executes entirely within standard Android user
  space on Android 7.0 (API 24) or newer, requiring no root access.
- **Continuous Background Operation**: Employs an Android Foreground Service
  with partial CPU `WakeLock` to prevent system termination during background
  tasks.
- **Hermetic Storage**: Provides persistent data volumes that decouple
  application state from package lifecycles.

---

## Documentation Index

Detailed engineering documentation is organized into dedicated technical
guides. Refer to the table below to navigate the documentation:

| Guide                                                                      | Focus Area            | Target Audience     | Key Topics Covered                                                                              |
|:---------------------------------------------------------------------------|:----------------------|:--------------------|:------------------------------------------------------------------------------------------------|
| [**Technical Architecture**](Documentation/architecture.md)                | System Architecture   | System Engineers    | Native Bionic execution, Termux asset layout, process supervisor, VFS hooks, security model     |
| [**Local Development Guide**](Documentation/development.md)                | Development Workflow  | Contributors        | Android Studio setup, Gradle compilation, ADB workflows, Logcat inspection, test execution      |
| [**Package Compatibility**](Documentation/box_compatibility.md)            | Package Specification | Package Maintainers | Cross-platform `.box` specs, `protect: false`, Python/Node.js support, native C/C++ wheels      |
| [**Networking and Storage Volumes**](Documentation/network_and_volumes.md) | Network, Storage      | Operators           | Port binding (`0.0.0.0`), LAN exposure, DNS fallback resolver, symlink volumes, data durability |

### Governance

- [**Contribution Guidelines**](.github/CONTRIBUTING.md): Workflow
  instructions, coding standards, pull request requirements.
- [**Security Policy**](SECURITY.md): Supported versions, vulnerability
  disclosure procedures.
- [**Code of Conduct**](CODE_OF_CONDUCT.md): Community participation
  standards.

---

## Quick Start

### 1. Install Prebuilt APK

Download the latest verified release APK directly from GitHub:

- **[GitHub Releases](https://github.com/tanguykonan/box-runner/releases)**

### 2. Build from Source

Prerequisites: JDK 17, Android SDK 35, ARM64-v8a test device or emulator.

```bash
# Clone the repository
git clone https://github.com/tanguykonan/box-runner.git
cd box-runner

# Compile the debug APK
./gradlew assembleDebug

# Deploy to connected device via ADB
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

For complete IDE configuration, troubleshooting instructions, refer to the
[Local Development Guide](Documentation/development.md).

---

## Packaging Workloads

Applications deployed to Box Runner must be packaged using the `box-cli` utility
with bytecode protection disabled:

```bash
# Package application for mobile execution
box pack --no-protect
```

The resulting `.box` archive can be imported directly into Box Runner.
Consult the [Package Compatibility Guide](Documentation/box_compatibility.md)
for manifest syntax, runtime specifications, framework compatibility rules.

---

## Related Projects

- **[Box CLI](https://github.com/tanguykonan/box-cli)**: Command-line utility
  to build, test, package `.box` workloads.
- **[BoxHub](https://boxhub.paxiz.org)**: Central package registry to
  discover, distribute containerized applications.

---

## Contributing

Contributions to Box Runner are welcome. Before submitting a pull request,
please review our [Contribution Guidelines](.github/CONTRIBUTING.md). Ensure
all quality gates pass:

```bash
# Execute unit tests
./gradlew test

# Run Android Lint
./gradlew lintDebug

# Validate documentation formatting
npx markdownlint-cli -c .markdownlint.json "Documentation/*.md" ".github/*.md" "*.md"
```

---

## Security

Security reports are taken seriously. If you discover a vulnerability, please
follow the procedure outlined in our [Security Policy](SECURITY.md).

---

## License

Box Runner is licensed under the
[GNU General Public License version 3 (GPLv3)](LICENSE).
