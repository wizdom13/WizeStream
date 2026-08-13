# WizeStream Desktop

WizeStream Desktop is an experimental, real desktop client for Windows, macOS and Linux. It is
not an Android compatibility wrapper and does not share the Android user interface.

The desktop architecture contains:

- Electron with a sandboxed renderer, context isolation and a narrow typed preload API.
- React with a Material 3-inspired adaptive interface.
- The same integrated WizeStreamExtractor Java sources used by the Android application.
- A versioned JSON-RPC JVM backend transported over standard input/output (no listening API port).
- SQLite stores for subscriptions, playlists, watch/search history, Learning Mode notes, portable
  settings and synchronization state.
- The existing WizeStream v1 signed pairing messages and authenticated libp2p transport.
- An inline libmpv renderer on supported native packages, with a shell-free mpv process fallback.
- Resumable video, audio and caption downloads stored in a fixed WizeStream Downloads directory.
- Unsigned native preview packages produced on native Windows, Intel/Apple Silicon macOS, and
  x86_64/aarch64 Linux runners.

## Current scope

Phase 1 established the application shell, extractor-backed search, stream resolution, external
mpv playback, persistent desktop identity, encrypted pairing and native preview packages.

Phase 2 enables manual, category-selectable synchronization with Android devices for:

- subscriptions and local/remote playlists;
- watch history, playback progress and search history;
- Learning Mode notes;
- feed groups, home tabs, channel playback profiles and filters;
- portable settings and completed-download metadata.

The desktop compiles the same v1 synchronization models, validation, engines and libp2p protocol
bindings used by Android. JDBC adapters use immutable change journals, per-origin revision clocks,
per-peer acknowledgements, Lamport conflict resolution and tombstones. Structured records without
a desktop editing surface are retained losslessly so round trips do not discard Android data.

Phase 3 adds native desktop library editors for subscriptions, local playlists and their items,
watch and search history, and Learning Mode notes. Successful searches and playback starts create
history events, and local edits are reconciled into the same Phase 2 journals before the next
device synchronization.

Phase 4 added semantic original/dubbed/descriptive audio labels, caption selection, embedded libmpv
rendering, and resumable video, audio and caption downloads.

The Windows packaging workflow pins the Shinchiro libmpv development archive and verifies its
SHA-256 digest before compiling or staging native code.

Phase 5 adds opt-in automatic synchronization while WizeStream Desktop is running. The JVM
backend persists a user-selected interval, categories and explicit trusted-device allowlist,
checks for a private local IPv4 network, and reuses the Phase 2 engines without changing their
journals or conflict rules. Runs are serialized with manual synchronization, recover after an
overdue application restart, and use per-device 5-minute-to-6-hour exponential retry backoff.
Search history remains excluded until explicitly selected. Recent manual, automatic, skipped and
failed attempts are shown on the Devices screen without recording synchronized payloads, pairing
codes, private keys or media URLs.

Automatic synchronization is not an operating-system daemon: closing WizeStream stops its JVM
backend and scheduler. The next launch performs a jittered catch-up when a persisted run is overdue.

Phase 6 packages an Electron-ABI-matched embedded libmpv renderer on Windows x64, Linux x64/arm64,
and macOS x64/arm64. Windows and macOS prefer shared textures; Linux uses the software/WebGL
pipeline. Selected external audio and captions now remain inside the embedded player and can be
switched through narrow typed operations. The shell-free external mpv controller remains an
explicit recovery option on every platform.

Adaptive video-only downloads require an audio selection and are represented as one recoverable
job. WizeStream downloads both components, refreshes expired URLs only when the saved stream
fingerprint resolves unambiguously, and uses packaged checksum-verified FFmpeg/FFprobe tools to
stream-copy and validate MP4, WebM or Matroska output before an atomic final rename. It never
transcodes, silently changes quality/language, logs signed media URLs, or records completion before
the final file is valid. Legacy Phase 4 download state migrates to schema version 2 without deleting
partial files.

When a trusted device's saved IP address is stale, desktop scans the local IPv4 subnet only on the
previously trusted sync port and then authenticates the discovered endpoint against the saved
libp2p PeerID. Discovery never establishes trust by itself.

## Requirements

- Node.js 24
- JDK 21
- mpv on `PATH`, or `WIZESTREAM_MPV_PATH` pointing to the executable, only for the optional external
  recovery player

Production packages include a trimmed Java runtime. End users do not need to install Java.

## Develop

From `desktop/`:

```bash
npm ci
npm run dev
```

The first run builds the JVM backend and its bundled runtime before Electron starts.

## Validate

```bash
npm ci --ignore-scripts
../gradlew -p backend test installDist runtimeImage
npm run typecheck
npm run build
```

The production build also executes a packaged-startup verifier. It checks that Electron can load
the sandboxed CommonJS preload, that the restricted bridge is exposed, that renderer assets exist,
and that a visible recovery screen is bundled if bridge initialization ever fails.

## Package

Run the package task on the target operating system:

```bash
npm run dist
```

Preview packages are unsigned. Windows signing and macOS signing/notarization are release gates,
not requirements for architecture CI.

## Security boundary

- `nodeIntegration` is disabled.
- Context isolation and the Chromium renderer sandbox are enabled.
- The preload bridge exposes only allow-listed backend and player operations.
- The sandboxed preload bundles the narrow libmpv IPC bridge; native modules are never exposed
  directly to the renderer.
- IPC payloads are schema validated in the Electron main process.
- Navigation, popups and permission requests are denied by default.
- The JVM backend communicates only over inherited standard input/output.
- mpv is launched without a shell and accepts only HTTP(S) media URLs.
- Pairing invitations retain WizeStream's protocol version, peer identity signature checks,
  expiration, one-time token and libp2p authenticated transport.

## Next milestone

1. Add signed releases and automatic updates after preview stabilization.
2. Complete accessibility, platform UX, migration and security review gates for the preview release.
