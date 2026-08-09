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
- A shell-free mpv process adapter for extracted HTTP(S) media URLs.
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

When a trusted device's saved IP address is stale, desktop scans the local IPv4 subnet only on the
previously trusted sync port and then authenticates the discovered endpoint against the saved
libp2p PeerID. Discovery never establishes trust by itself.

## Requirements

- Node.js 24
- JDK 21
- mpv on `PATH`, or `WIZESTREAM_MPV_PATH` pointing to the executable

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
- IPC payloads are schema validated in the Electron main process.
- Navigation, popups and permission requests are denied by default.
- The JVM backend communicates only over inherited standard input/output.
- mpv is launched without a shell and accepts only HTTP(S) media URLs.
- Pairing invitations retain WizeStream's protocol version, peer identity signature checks,
  expiration, one-time token and libp2p authenticated transport.

## Next milestone

1. Add native desktop library editors for subscriptions, playlists, history and Learning Mode.
2. Add downloads, captions, multi-audio selection and embedded libmpv rendering.
3. Add scheduled background synchronization with user-controlled category policy.
4. Add signed releases and automatic updates after preview stabilization.
