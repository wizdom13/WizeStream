# WizeStream Desktop

WizeStream Desktop is an experimental, real desktop client for Windows, macOS and Linux. It is
not an Android compatibility wrapper and does not share the Android user interface.

The Phase 1 architecture contains:

- Electron with a sandboxed renderer, context isolation and a narrow typed preload API.
- React with a Material 3-inspired adaptive interface.
- The same integrated WizeStreamExtractor Java sources used by the Android application.
- A versioned JSON-RPC JVM backend transported over standard input/output (no listening API port).
- SQLite tables for subscriptions, playlists, history, Learning Mode notes and synchronization state.
- The existing WizeStream v1 signed pairing messages and authenticated libp2p transport.
- A shell-free mpv process adapter for extracted HTTP(S) media URLs.
- Unsigned native preview packages produced on native Windows, Intel/Apple Silicon macOS, and
  x86_64/aarch64 Linux runners.

## Current scope

This branch is the architecture proof of concept. Search, stream resolution, mpv playback,
persistent desktop identity and encrypted device pairing are implemented. The database tables and
navigation surfaces for the remaining features are present.

Subscription, playlist, history, Learning Mode and settings payload synchronization remains
disabled until desktop SQLite adapters pass shared compatibility fixtures against the Android Room
implementation. The backend reports `dataSyncEnabled: false` so the UI and automation cannot imply
that data transfer is already safe.

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

1. Extract shared synchronization models into an Android/JVM `sync-core` module.
2. Implement and fixture-test SQLite subscription, playlist, history, Learning Mode and portable
   settings stores.
3. Add manual category synchronization, then automatic LAN discovery.
4. Add downloads, captions, multi-audio selection and embedded libmpv rendering.
5. Add signed releases and automatic updates after preview stabilization.
