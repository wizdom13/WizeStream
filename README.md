<p align="center"><a href="https://github.com/wizdom13/WizeStream"><img src="assets/wizestream_logo.png" width="150" alt="WizeStream icon"></a></p>

<h1 align="center">WizeStream</h1>

<p align="center"><b>A Material 3-focused streaming app based on NewPipe.</b></p>

<p align="center">
  <a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="80" alt="Get it on IzzyOnDroid"></a>
  <a href="https://github.com/wizdom13/WizeStream/releases"><img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub"></a>
  <br>
  <a href="https://github-store.org/app?repo=wizdom13/WizeStream"><img src="https://raw.githubusercontent.com/kurikomi-labs/komi-store/main/media-resources/ghs_download_badge.png" height="58" alt="Get it on GitHub Store"></a>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/WizeStream/actions"><img src="https://github.com/wizdom13/WizeStream/actions/workflows/ci.yml/badge.svg?branch=pipe" alt="Build status"></a>
  <a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/org.wisso.newpipematerial&amp;label=IzzyOnDroid" alt="Latest version on IzzyOnDroid"></a>
  <a href="https://shields.rbtlog.dev/org.wisso.newpipematerial"><img src="https://shields.rbtlog.dev/simple/org.wisso.newpipematerial" alt="Reproducible build status"></a>
</p>

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
  <a href="#donate">Donate</a> •
  <a href="#license">License</a>
</p>

<p align="center">Translations are welcome, but only native-speaker reviewed translations will be added.</p>

---

## Important project notice

WizeStream is an independently maintained Android streaming app based on NewPipe, focused on Material 3 design, app theming, playback enhancements, and product polish.

It is **not affiliated with, sponsored by, or endorsed by** the official NewPipe project, TeamNewPipe, or NewPipe e.V.

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

WizeStream keeps the lightweight, privacy-friendly NewPipe experience while modernizing the interface and adding playback-focused features.

Project highlights:

- Material 3-inspired app surfaces, dialogs, settings, tabs, and navigation
- Dynamic Material You color support where available
- Manual theme color presets
- Bottom navigation with a configurable default main tab
- SponsorBlock integration
- YouTube dislike count support
- Swipe seek, fullscreen swipe, and hold-to-speed-up player gestures
- Swipe down from the video player to return to the miniplayer
- Optional keep-video-visible mode while scrolling the details page
- Secure peer-to-peer synchronization between trusted WizeStream devices
- Automatic background synchronization on Wi-Fi or Ethernet
- Search filters and sorting, channel video sorting, and podcast channel tabs
- Per-channel playback profiles
- Multi-audio track selection with original, dubbed, and descriptive labels
- Import/export compatibility with supported NewPipe backup data

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

WizeStream supports these services through its pinned
[`WizeStreamExtractor`](https://github.com/wizdom13/WizeStreamExtractor) submodule:

- YouTube and YouTube Music
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

YouTube playback and downloads include support for SABR streams. Service support depends on the
pinned extractor revision.

---

## Features

Core features include:

- Watch videos and live streams
- Background playback
- Popup player
- Local playlists
- Subscriptions without signing in to a platform account
- Service-specific subscriptions, channel groups, and **What's New** feeds with independent refresh
  state, including separate scopes for YouTube and YouTube Music
- Search and browse supported services
- View video details, related videos, and comments where supported
- Download video, audio, and captions where supported
- Import and export app data for migration and backup

WizeStream additions include:

- Material 3 theme roles across more app surfaces
- Bottom navigation for five or fewer main tabs, with a scrollable tab layout for larger tab sets
- Configurable default main tab
- Dynamic and manual theme color support
- SponsorBlock and dislike-count support
- YouTube SABR playback and downloading
- Independent Subscriptions and **What's New** content for each service, including separate YouTube
  and YouTube Music scopes
- Enhanced player gestures
- Optional pinned video while scrolling
- Secure peer-to-peer synchronization for subscriptions, feed groups, playlists, watch and search
  history, playback progress, selected settings, channel playback profiles, and completed-download
  metadata
- Manual synchronization and automatic background synchronization on Wi-Fi or Ethernet
- A **Download on this device** action when synchronized download metadata has no matching local
  media file
- Search filters and sorting
- Latest, popular, and oldest sorting for channel videos
- Podcast tabs on supported channels
- Per-channel playback profiles
- Multi-audio track selection with original, dubbed, descriptive, and secondary track labels
- Playback-speed retention for live streams
- Independent release signing

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

### Release APK

<a href="https://github.com/wizdom13/WizeStream/releases"><img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" height="80" alt="Get it on GitHub"></a>

Install WizeStream from this repository's GitHub releases or signed build artifacts when available.

Releases: https://github.com/wizdom13/WizeStream/releases

WizeStream uses a different application ID from official NewPipe, so both apps can be installed side by side:

```text
WizeStream:        org.wisso.newpipematerial
Debug build:       org.wisso.newpipematerial.debug
```

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

WizeStream uses a pinned `WizeStreamExtractor` Git submodule. Clone the repository together with its pinned submodule:

```bash
git clone --recurse-submodules https://github.com/wizdom13/WizeStream.git
cd WizeStream
```

For an existing checkout or after switching commits or tags:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

Requirements:

- Git with submodule support
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

Do not use `git submodule update --remote` for release builds. Every release must use the extractor commit recorded by the app commit or tag.

See [BUILDING.md](BUILDING.md) for complete build, signing, submodule, and reproducible-release instructions.

The debug APK uses the app label **WizeStream Debug** and package `org.wisso.newpipematerial.debug`.

---

## Release signing

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

A published APK must be built from the exact commit referenced by its release tag, including the pinned extractor submodule commit.

---

## Development status

WizeStream is under active development for Material 3 polish, playback improvements, and release readiness.

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

High-risk areas receive dedicated QA before broad behavior changes.

---

## Contributing

Contributions are welcome, especially focused Material 3 polish, bug fixes, QA findings, documentation, and release-readiness work.

Please keep changes focused and testable. For UI work, include before-and-after screenshots where possible and verify Light, Dark, Black, Follow system, and at least one manual theme color preset.

### WizeStreamExtractor submodule

WizeStream builds against the [`wizdom13/WizeStreamExtractor`](https://github.com/wizdom13/WizeStreamExtractor) fork through the pinned submodule at `external/WizeStreamExtractor`.

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

The recorded submodule commit is part of the source definition. Do not replace it with the latest branch tip when preparing a release or reproducible build.

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

- WizeStream-specific design, identity, release, and feature issues belong in this repository.
- Upstream extractor or service breakages may also need to be checked against official NewPipe.

---

## Donate

To support upstream NewPipe, see its official donation page:

https://newpipe.net/donate

Upstream donations go to the upstream NewPipe project, not automatically to WizeStream.

---

## License

WizeStream is free software based on NewPipe and is distributed under the GNU General Public License version 3 or later.

See the repository license files and in-app license screen for full license and third-party notice details.
