# WizeStream Desktop

WizeStream is a multi-platform application for Android, Windows, macOS and Linux. WizeStream
Desktop is its real desktop client for Windows, macOS and Linux, currently distributed as an
explicitly unsigned preview. It is not an Android compatibility wrapper and does not share the
Android user interface.

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
- Native preview packages produced on native Windows, Intel/Apple Silicon macOS, and
  x86_64/aarch64 Linux runners, plus a protected signed-beta release path.

## What the Desktop app can do

- Search and browse supported streaming services.
- Play video and audio inside the application.
- Choose available audio tracks and captions.
- Download video, audio and captions, with pause and resume support.
- Manage subscriptions, playlists, history and Learning Mode notes.
- Pair with trusted WizeStream devices and synchronize selected data over the local network.
- Adjust applicable playback, download, appearance, history, content, device and Learning Mode
  settings using the same familiar sections as the Android app.
- Export and restore versioned Desktop ZIP backups. Subscription-only JSON exports use Android's
  schema, and Desktop can import subscriptions from Android JSON exports or Android full-backup ZIPs.
- Keep user data locally without requiring a WizeStream account.

Synchronization can run manually or automatically while WizeStream Desktop is open. It works only
with devices you deliberately pair and trust. If a trusted device changes address after using a
hotspot, WizeStream can find it again but still verifies the saved device identity before syncing.

Downloads are checked before they are marked complete. When separate video and audio tracks are
needed, WizeStream combines them without lowering their quality.

The current Desktop packages are explicitly unsigned previews. They are built and tested for
Windows x64, macOS x64/arm64 and Linux x64/arm64. Updates are installed manually.

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

Preview packages are unsigned. Production updates are disabled and updater metadata is intentionally
omitted, so preview upgrades are installed manually. Windows signing and macOS
signing/notarization are postponed future release gates, not requirements for architecture CI or
the current preview channel.

See [docs/preview-testing.md](docs/preview-testing.md) for the manual acceptance checklist.
See [docs/releasing.md](docs/releasing.md) for the current unsigned-preview policy and the postponed
future signing, updater-validation and rollback procedures.
Automated unsigned Desktop nightlies are published for all five supported targets in the
[WizeStream Nightly repository](https://github.com/wizdom13/WizeStream_Nightly/releases). See the
[nightly build documentation](../docs/nightly-builds.md) for filenames, tags and verification.

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

## Maintenance status

WizeStream Desktop is integrated into the main project. Ongoing work is regular maintenance:
testing the supported platforms, reviewing preview reports and fixing reproducible problems.
Changes must pass the Android and complete Desktop test matrices before they are merged. A higher
unsigned beta is published only when important fixes change the application. Signed public
releases and production automatic updates remain postponed.
