<p align="center"><a href="https://github.com/wizdom13/WizeStream"><img src="assets/wizestream_logo_round.svg" width="150" alt="WizeStream icon"></a></p>

<h1 align="center">WizeStream</h1>

<p align="center"><b>An independent, privacy-friendly, multi-platform streaming application for Android, Windows, macOS and Linux.</b></p>

WizeStream combines its established Android application with a real Desktop client built for
Windows, macOS and Linux. Desktop is integrated into the `pipe` branch and available as an
**explicitly unsigned preview**; it is not an Android compatibility wrapper. See the
[Desktop implementation guide](desktop/README.md) and
[Desktop preview testing guide](desktop/docs/preview-testing.md).

| Platform | Availability | Packages |
| --- | --- | --- |
| Android | Signed application releases | APK / IzzyOnDroid |
| Windows x64 | Unsigned Desktop preview | Installer and portable `.exe` |
| macOS x64 and arm64 | Unsigned Desktop preview | `.dmg` and `.zip` |
| Linux x64 and arm64 | Unsigned Desktop preview | `.AppImage` and `.deb` |

<p align="center">
  <a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="80" alt="Get it on IzzyOnDroid"></a>
  <a href="https://github.com/wizdom13/WizeStream/releases"><img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub"></a>
  <br>
  <a href="https://github-store.org/app?repo=wizdom13/WizeStream"><img src="https://raw.githubusercontent.com/kurikomi-labs/komi-store/main/media-resources/ghs_download_badge.png" height="58" alt="Get it on GitHub Store"></a>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/WizeStream/actions"><img src="https://github.com/wizdom13/WizeStream/actions/workflows/ci.yml/badge.svg?branch=pipe" alt="Build status"></a>
  <a href="https://github.com/wizdom13/WizeStream/actions/workflows/desktop-ci.yml"><img src="https://github.com/wizdom13/WizeStream/actions/workflows/desktop-ci.yml/badge.svg?branch=pipe" alt="Desktop build status"></a>
  <a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/org.wisso.newpipematerial&amp;label=IzzyOnDroid" alt="Latest version on IzzyOnDroid"></a>
  <a href="https://shields.rbtlog.dev/org.wisso.newpipematerial"><img src="https://shields.rbtlog.dev/simple/org.wisso.newpipematerial" alt="Reproducible build status"></a>
</p>

<hr>

<div align="center">
  <h3>🧪 Help test WizeFiles Beta</h3>
  <p>
    We’re looking for testers for <strong>WizeFiles</strong>, a new Android file manager
    from the developer of WizeStream.
  </p>
  <p>
    <a href="https://github.com/wizdom13/WizeFiles-Beta">
      <strong>View the beta, download it, and share your feedback →</strong>
    </a>
  </p>
</div>

<hr>

<p align="center">
  <a href="#important-project-notice">Project notice</a> •
  <a href="#what-is-wizestream">About</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#supported-services">Services</a> •
  <a href="#features">Features</a> •
  <a href="#device-synchronization">Device sync</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Build</a> •
  <a href="#release-signing">Signing</a> •
  <a href="#development-status">Development</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#upstream-newpipe">Upstream</a> •
  <a href="#license">License</a>
</p>

<p align="center">Translations are welcome, but only native-speaker reviewed translations will be added.</p>

---

## Important project notice

WizeStream is an independent, NewPipe-based, multi-platform streaming application for Android,
Windows, macOS and Linux. It combines privacy-friendly playback, useful local-first features,
support for multiple streaming services, an expressive Android interface, and a dedicated Desktop
experience.

WizeStream maintains its own application and integrated extractor changes. It is **not affiliated
with, sponsored by, or endorsed by** the official NewPipe project, TeamNewPipe, or NewPipe e.V.

WizeStream preserves the NewPipe libre software license, upstream credits, and third-party license notices.

- [Frequently asked questions](docs/faq.md)
- [Privacy policy](PRIVACY.md)

### Rebranding from NewPipe Material

This project was previously distributed as **NewPipe Material**. In July 2026, NewPipe e.V. asked the project to adopt a unique name that does not use the registered NewPipe word mark, in accordance with its trademark policy. The project was therefore renamed to **WizeStream**.

The rebranding changes the public app name, visual identity, repository name, and distribution metadata. It does **not** change the app's technical identity or update path: existing installations continue to use the same application ID, and can receive normal updates signed with the same release key. User data and settings are not reset by the name change.

WizeStream remains based on NewPipe and continues to preserve upstream attribution, licensing, and third-party notices. The rename is intended to clearly distinguish this independently maintained project from official NewPipe while respecting the upstream project's trademark policy. See [issue #34](https://github.com/wizdom13/WizeStream/issues/34) for the original request.

### Independent versioning

WizeStream has its own release cycle and follows semantic versioning:

- `MAJOR.MINOR.PATCH`, such as `1.0.0`, `1.1.0`, and `1.2.0`
- Git tags use the corresponding `vMAJOR.MINOR.PATCH` form
- WizeStream version numbers do not contain or follow NewPipe version numbers

NewPipe remains an upstream source and is credited as required, but its version is development
metadata rather than part of WizeStream's public version identity. The currently tracked upstream
baseline is recorded in [UPSTREAM.md](UPSTREAM.md).

---

## What is WizeStream?

WizeStream keeps the lightweight, privacy-friendly NewPipe experience while developing its own
interface, playback, discovery, synchronization, and service-support features. Material 3
Expressive design remains an important project goal, but it is one part of a broader independent
application rather than the project's only purpose. Android and Desktop use platform-appropriate
interfaces while sharing WizeStream's integrated extractor sources and compatible synchronization
protocol.

Project highlights:

- Android, Windows, macOS and Linux support in one open-source project
- Material 3-inspired design with Material You colors, manual color presets, and customizable navigation
- Account-free playback, subscriptions, feeds, playlists, downloads, and history across supported services
- Dedicated YouTube Music and YouTube Shorts destinations, advanced search filters, and channel sorting
- Main, background, and popup playback with advanced gestures, a sleep timer, multi-audio selection,
  and per-channel playback profiles
- SponsorBlock, YouTube dislike counts, and clear handling for members-only videos
- Optional Learning Mode with timestamped notes, playlist progress, study statistics, and a dashboard
- Secure peer-to-peer synchronization between trusted WizeStream devices, manually or automatically
  over Wi-Fi or Ethernet
- Local search across subscriptions, playlists, feeds, history, and Downloads
- Import/export compatibility with supported NewPipe backup data and a verified in-app update flow

Sensitive areas such as playback, downloads, background playback, popup playback, and extractor logic are changed only through focused and tested work.

---

## Screenshots

### Phone

<p align="center">
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Phone screenshot 1"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Phone screenshot 2"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Phone screenshot 3"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Phone screenshot 4"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Phone screenshot 5"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Phone screenshot 6"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Phone screenshot 7"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Phone screenshot 8"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Phone screenshot 9"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="160" alt="Phone screenshot 10"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width="405" alt="Phone screenshot 11"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="405" alt="Phone screenshot 12"></a>
</p>

### Tablet

<p align="center">
  <a href="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Tablet screenshot 1"></a>
  <a href="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Tablet screenshot 2"></a>
</p>

---

## Supported services

WizeStream supports these services through extractor source integrated directly into the app:

- YouTube and YouTube Music
- Bilibili
- Niconico
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

YouTube playback and downloads use conventional progressive, DASH, and HLS stream URLs exposed by
the bundled extractor source.

### Integrated extractor lineage and responsibility

The bundled extractor is derived from NewPipeExtractor and later incorporated PipePipeExtractor
and WizeStream-specific changes. It is maintained as source inside WizeStream and is therefore
neither the unmodified official NewPipeExtractor nor an external PipePipeExtractor dependency.

Extractor work is part of WizeStream's current scope because service compatibility and playback
fixes can require coordinated application, player, and extractor changes.
Problems encountered in WizeStream—including service and extractor failures—should be reported to
WizeStream first. Comparing a problem with official NewPipe can help determine whether the cause is
shared upstream, but WizeStream remains responsible for defects caused by its bundled changes.

---

## Features

Feature availability differs between the Android and Desktop interfaces. Android provides the full
mobile feature set documented below. The Desktop preview provides native Desktop browsing,
playback, downloads, libraries, Learning Mode notes and trusted-device synchronization; see the
[Desktop implementation guide](desktop/README.md) for its exact current scope.

### Streaming and discovery

- Watch videos, live streams, and audio without signing in to a platform account
- Browse all [supported services](#supported-services), with dedicated YouTube Music and YouTube
  Shorts destinations
- Search with service-provided content, date, duration, feature, and sorting filters
- Filter channel, feed, and playlist lists by unwatched, partially watched, live, or Shorts content
- Browse video details, related content, comments, playlists, and channel tabs where supported
- Sort channel videos by latest, popular, or oldest, and use podcast tabs on supported channels
- See channel avatars directly in stream lists, open channels from their identity areas, and view
  view or subscriber counts where the service provides them
- View YouTube dislike counts where available
- Identify membership-restricted videos with a **Members only** badge, receive a clear explanation
  instead of an unplayable native request, or hide those videos from content lists

### Playback

- Main, background, popup, external-player, and Kodi playback
- Playback queues, repeat and shuffle controls, chapters, captions, and seek-bar thumbnail previews
- Select available video resolutions and formats, including higher adaptive qualities when exposed by
  the service
- Select multi-audio tracks with original, dubbed, descriptive, and secondary labels, with preferences
  for original or descriptive audio
- Save playback speed, quality, and caption choices in per-channel playback profiles
- Retain playback speed for live streams
- Use swipe seeking, fullscreen volume and brightness swipes, hold-to-speed-up, an optional two-finger
  playback-speed gesture, and swipe down to the miniplayer
- Optionally keep the video visible while scrolling its details page
- Set a sleep timer using presets, a custom duration, the end of the current video, or the end of the
  queue, with optional fade-out
- Skip or mark SponsorBlock categories with per-category behavior, colors, notifications, seek-bar
  segments, and a manual skip button

### Library, subscriptions, and downloads

- Subscribe to channels without a platform account and organize subscriptions into channel groups
- Keep independent **Subscriptions** and **What's New** scopes and refresh state for each service,
  including separate YouTube and YouTube Music scopes
- Create local playlists, bookmark remote playlists, and sort remote playlist contents
- Swipe a video out of a local playlist with an **Undo** action
- Search locally within subscriptions, playlists, feeds, watch history, and Downloads, then carry a
  query into the selected service's online search
- Store watch history, search history, and playback progress locally under user-controlled settings
- Download video, audio, and captions where supported, with resumable downloads and queue controls
- Import and export compatible app data for migration and backup

### Learning Mode

Learning Mode is optional and disabled by default. When enabled, it adds:

- Timestamped notes linked to exact positions in non-live videos
- Completion progress and learning controls for local playlists, including mark-all-watched and reset
- A learning dashboard for active and completed playlists, recently annotated videos, and continue
  learning shortcuts
- Study-time statistics, current and longest streaks, and a 28-day activity calendar
- A setting to include or exclude background listening from study statistics

### Interface and customization

- Material 3-inspired app surfaces, dialogs, settings, tabs, and navigation
- Dynamic Material You colors where available, plus manual theme color presets
- A customizable home screen and configurable default main tab
- Bottom navigation for up to five main sections and a scrollable tab layout for larger tab sets
- Bottom-navigation labels that can be always visible, active only, or hidden
- Phone, tablet, landscape, and Android TV layouts

### Device synchronization

- Encrypted peer-to-peer pairing between trusted WizeStream devices using a one-time QR code
- Manual synchronization and automatic background synchronization over Wi-Fi or Ethernet
- Synchronization of subscriptions, feed groups, local and remote playlists, watch history, playback
  progress, home tabs, content filters, channel playback profiles, allowlisted settings, optional
  search history, and completed-download metadata
- A **Download on this device** action when synchronized download metadata has no matching local file

See [Device synchronization](#device-synchronization) for behavior, limitations, and setup.

### Updates and releases

- Android supports manual and optional background checks for signed WizeStream releases, with
  changelog preview, APK download progress, installation handoff, and update validation.
- Desktop preview packages are downloaded and updated manually. Production automatic updates are
  disabled, and Windows/macOS packages are currently unsigned.
- Independent semantic versioning, signed Android releases, and separately installable Android
  nightly builds

---

## Device synchronization

WizeStream can synchronize supported app data directly between trusted WizeStream devices without
requiring a platform account or a WizeStream cloud account.

Open **Settings > Device synchronization** on both devices. One device displays a one-time QR code
and the other scans it. The pairing code expires after five minutes and can be used only once.
After pairing, use **Sync now** for an immediate exchange or leave **Automatic background
synchronization** enabled. Background synchronization runs approximately once per hour when the
device has sufficient battery and can reach a trusted device on the same Wi-Fi or Ethernet network.

Supported synchronized data includes:

- Subscriptions and feed groups
- Local and remote playlists
- Watch history and playback progress
- Home tabs, content-filter selections, channel playback profiles, and allowlisted settings
- Search history when explicitly enabled on both devices
- Completed-download metadata

Synchronization uses an encrypted peer-to-peer connection between paired devices. Downloaded media
files are not transferred. If another device reports a completed download but its media file is
missing locally, the Downloads screen marks it **Not local** and offers **Download on this device**
using the original source. Existing local and pending copies are deduplicated.

See [Device synchronization](docs/device-synchronization.md) for pairing instructions, synchronized
data details, background behavior, and privacy notes.

---

## Installation

### IzzyOnDroid

<a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="80" alt="Get it at IzzyOnDroid"></a>

Install WizeStream through an F-Droid-compatible client from the IzzyOnDroid repository.

### Android release APK

<a href="https://github.com/wizdom13/WizeStream/releases"><img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub"></a>

Install WizeStream from this repository's GitHub releases or signed build artifacts when available.

Releases: https://github.com/wizdom13/WizeStream/releases

WizeStream uses a different application ID from official NewPipe, so both apps can be installed side by side:

```text
WizeStream:        org.wisso.newpipematerial
Debug build:       org.wisso.newpipematerial.debug
```

### Desktop unsigned preview

Download Windows, macOS or Linux packages from the
[`desktop_v0.6.0-beta`](https://github.com/wizdom13/WizeStream/releases/tag/desktop_v0.6.0-beta)
release. Verify the supplied `SHA256SUMS` before opening a package.

The Desktop preview is explicitly unsigned. Windows may show an unknown-publisher or SmartScreen
warning, and macOS Gatekeeper may require deliberate approval. Production automatic updates are
disabled, so Desktop preview upgrades are installed manually. See the
[Desktop preview testing guide](desktop/docs/preview-testing.md) for package and verification
details.

### GitHub Store

<a href="https://github-store.org/app?repo=wizdom13/WizeStream"><img src="https://raw.githubusercontent.com/kurikomi-labs/komi-store/main/media-resources/ghs_download_badge.png" height="58" alt="Get it on GitHub Store"></a>

Install WizeStream through GitHub Store.

### Migrating data

WizeStream does not automatically share app data with official NewPipe.

To migrate:

1. Open official NewPipe.
2. Export your database from Settings > Backup and Restore.
3. Install WizeStream.
4. Import the exported database from Settings > Backup and Restore.

Always keep a backup before importing data between builds.

### Nightly builds

Automated nightly builds of the latest `pipe` commit are available here:

https://github.com/wizdom13/WizeStream_Nightly/releases

Nightly builds are unstable testing versions and use the separate application ID
`org.wisso.newpipematerial.nightly`, so they can be installed alongside the stable app.

See [Nightly build documentation](docs/nightly-builds.md) for details.

### Google Play warning

Do not publish WizeStream or forks of NewPipe to Google Play without first reviewing all applicable upstream, platform, and trademark requirements.

---

## Building from source

WizeStream includes its extractor source directly in the Android `app` module and reuses those
sources in the Desktop backend:

```bash
git clone https://github.com/wizdom13/WizeStream.git
cd WizeStream
```

Requirements:

- Git
- JDK 21
- Android SDK with the required platform and build tools
- Accepted Android SDK licenses

Build the debug APK and run JVM checks:

```bash
scripts/build.sh debug
```

Other build modes:

```bash
scripts/build.sh release
scripts/build.sh connected
scripts/build.sh checkstyle
```

See [BUILDING.md](BUILDING.md) for complete build, signing, and reproducible-release instructions.

The debug APK uses the app label **WizeStream Debug** and package `org.wisso.newpipematerial.debug`.

To develop the Desktop application, install Node.js 24 and JDK 21, then run from `desktop/`:

```bash
npm ci
npm run dev
```

Desktop packages must be built on their target operating system with `npm run dist`. See the
[Desktop implementation guide](desktop/README.md) for architecture, validation and packaging
details.

---

## Release signing

The environment variables below sign Android releases. Desktop Windows signing and macOS
signing/notarization are postponed; the current Desktop preview remains explicitly unsigned. See
[Desktop release operations](desktop/docs/releasing.md) for that policy.

Configure release signing with the WizeStream environment-variable names:

```text
WIZESTREAM_RELEASE_STORE_FILE
WIZESTREAM_RELEASE_STORE_PASSWORD
WIZESTREAM_RELEASE_KEY_ALIAS
WIZESTREAM_RELEASE_KEY_PASSWORD
```

The legacy `NEWPIPE_MATERIAL_RELEASE_*` names remain accepted as fallbacks for existing environments.

When all four values are present, build the signed release APK with:

```bash
scripts/build.sh release
```

The release APK is generated under `app/build/outputs/apk/release/`. Verify it with:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

A published APK must be built from the exact commit referenced by its release tag, including the
extractor source stored in that commit.

---

## Development status

WizeStream is under active maintenance as an independent, multi-platform NewPipe-based
application. The Desktop application is integrated into the main project and future work is normal
maintenance and improvement. Ongoing work focuses on Android and Desktop reliability, user
feedback, local-first features, service compatibility, security and release readiness.

Completed or in-progress areas include:

- Public WizeStream identity
- Debug and release identity separation
- Material 3 theme colors
- Dynamic and manual theme color handling
- Bottom navigation and main-tab polish
- About-screen attribution
- Player gestures and pinned-video behavior
- Secure device pairing and peer-to-peer synchronization
- Automatic background synchronization and download-metadata recovery
- Search filters, channel sorting, podcast tabs, and channel playback profiles
- Multi-audio selection and clearer audio-track labels
- Dialog, snackbar, settings, video-detail, and download UI polish
- Release-signing workflow support
- Integrated Windows, macOS and Linux Desktop application with five-target CI
- Explicitly unsigned Desktop preview distribution with checksums and artifact attestations

High-risk areas receive dedicated QA before broad behavior changes.

---

## Contributing

Contributions are welcome, especially focused Material 3 polish, playback and service fixes,
local-first improvements, QA findings, documentation, and release-readiness work.

Please keep changes focused and testable. For UI work, include before-and-after screenshots where possible and verify Light, Dark, Black, Follow system, and at least one manual theme color preset.

Special thanks to [@FabianOvrWrt](https://github.com/FabianOvrWrt) (Fabián PS) for designing and contributing WizeStream's logo graphics in [PR #97](https://github.com/wizdom13/WizeStream/pull/97).

### Integrated extractor source

Extractor and timeago-parser sources are stored directly under `app/src/main/java`, with protocol
definitions under `app/src/main/proto` and tests under `app/src/test/java`. They are compiled and
tested as part of the application module, so app and service changes are committed and built
together in one repository.

---

## Upstream NewPipe

WizeStream is based on NewPipe.

WizeStream does not mirror NewPipe's release numbers. See [UPSTREAM.md](UPSTREAM.md) for the
upstream baseline currently tracked by this repository.

Upstream resources:

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Please report issues carefully:

- Report all problems observed in WizeStream—including integrated extractor and service failures—in
  this repository first.
- Maintainers may ask whether the problem is reproducible in official NewPipe to distinguish a
  shared upstream issue from WizeStream-specific behavior.
- Report a problem to an upstream project only when it is reproducible there and follows that
  project's reporting requirements.

---

## License

WizeStream is free software based on NewPipe and is distributed under the GNU General Public License version 3 or later.

See the repository license files and in-app license screen for full license and third-party notice details.
