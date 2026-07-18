<p align="center"><a href="https://github.com/wizdom13/NewPipe_Material"><img src="assets/newpip_material_logo.png" width="150" alt="NewPipe Material icon"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>A Material 3-focused independent fork of NewPipe for Android.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe_Material/actions"><img src="https://github.com/wizdom13/NewPipe_Material/actions/workflows/ci.yml/badge.svg?branch=pipe" alt="Build status"></a>
  <a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/org.wisso.newpipematerial&amp;label=IzzyOnDroid" alt="Latest version on IzzyOnDroid"></a>
  <a href="https://shields.rbtlog.dev/org.wisso.newpipematerial"><img src="https://shields.rbtlog.dev/simple/org.wisso.newpipematerial" alt="Reproducible build status"></a>
</p>

<p align="center">Translations are welcome, but only native-speaker reviewed translations will be added.</p>

---

## Important fork notice

NewPipe Material is an independently maintained fork of NewPipe focused on Material 3 design, app theming, and product polish.

It is **not affiliated with, sponsored by, or endorsed by** the official NewPipe project, TeamNewPipe, or NewPipe e.V.

NewPipe Material is built from NewPipe and keeps the NewPipe libre software license, upstream credits, and third-party license notices.

---

## What is NewPipe Material?

NewPipe Material keeps the core NewPipe experience while modernizing the app identity and user interface.

Current fork goals:

- Material 3-inspired app surfaces, dialogs, settings, tabs, and navigation
- Dynamic Material You color support where available
- Manual Theme color presets such as App default, Neutral, Green, Blue, Purple, Orange, Pink, and Red
- New app identity: **NewPipe Material**
- Separate application ID: `org.wisso.newpipematerial`
- Debug builds install separately as `org.wisso.newpipematerial.debug`
- Preserve NewPipe behavior, import/export compatibility, and supported services

This fork intentionally avoids risky behavior changes in sensitive areas such as playback, downloads, background playback, popup playback, and extractor logic unless they are handled as dedicated, tested changes.

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

NewPipe Material inherits NewPipe support for these services:

- YouTube and YouTube Music
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

Service support depends on the upstream NewPipe and NewPipe Extractor codebase.

---

## Features

NewPipe Material keeps the familiar NewPipe feature set, including:

- Watch videos and live streams
- Background playback
- Popup player
- Local playlists
- Subscriptions without signing in to a platform account
- Channel groups and feeds
- Search and browse supported services
- View video details, related videos, and comments where supported
- Download video, audio, and captions where supported
- Import/export app data for migration and backup

Material-focused additions include:

- Material 3 theme roles across more app surfaces
- Bottom navigation for five or fewer main tabs, with scrollable TabLayout fallback for larger tab sets
- Default bottom main tab position for new/unset installs
- Dynamic/manual Theme color support
- NewPipe Material fork attribution in the About screen
- Release signing support for fork builds

---

## Installation

### IzzyOnDroid

<a href="https://apt.izzysoft.de/packages/org.wisso.newpipematerial"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="80" alt="Get it at IzzyOnDroid"></a>

Install NewPipe Material through an F-Droid-compatible client from the IzzyOnDroid repository.

### Release APK

Install NewPipe Material from this repository's GitHub releases or signed build artifacts when available.
Releases: https://github.com/wizdom13/NewPipe_Material/releases

NewPipe Material uses a different application ID from official NewPipe, so it can install side by side with the official app:

```text
Official NewPipe:  org.schabi.newpipe / net.newpipe.app depending on upstream build
NewPipe Material:  org.wisso.newpipematerial
Debug build:       org.wisso.newpipematerial.debug
```

### Migrating data

NewPipe Material does not automatically share app data with official NewPipe.

To migrate:

1. Open official NewPipe.
2. Export your database from Settings > Backup and Restore.
3. Install NewPipe Material.
4. Import the exported database from Settings > Backup and Restore.

Always keep a backup before importing data between builds.

### Google Play warning

Do not publish NewPipe Material, NewPipe, or forks of NewPipe to Google Play. This project follows the same practical distribution caution as upstream NewPipe.

---

## Building from source

NewPipe Material uses a pinned `PipePipeExtractor` Git submodule. Clone the repository together with its pinned submodule:

```bash
git clone --recurse-submodules https://github.com/wizdom13/NewPipe_Material.git
cd NewPipe_Material
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

Build the debug APK and run JVM checks using the same committed entry point used by CI:

```bash
scripts/build.sh debug
```

Other available build modes:

```bash
scripts/build.sh release
scripts/build.sh connected
scripts/build.sh checkstyle
```

Do not use `git submodule update --remote` for release builds. Every release must use the exact extractor commit recorded by the app commit or tag.

See [BUILDING.md](BUILDING.md) for the complete build, signing, submodule, and reproducible-release instructions.

The debug APK uses the app label **NewPipe Material Debug** and package `org.wisso.newpipematerial.debug`.

---

## Release signing

Release signing is configured through environment variables:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

When all four values are present, build the signed release APK with:

```bash
scripts/build.sh release
```

The release APK is generated under `app/build/outputs/apk/release/`. Verify it with:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

A published APK must be built from the exact commit referenced by its release tag, including the pinned extractor submodule commit. Do not replace an existing release APK with one built from a newer commit; publish a new version and tag instead.

---

## Development status

NewPipe Material is a fork in active Material 3 polish and productization work.

Completed or in-progress fork areas include:

- App name and application ID
- Debug/release identity separation
- Material 3 theme colors
- Dynamic/manual Theme color handling
- Bottom navigation and main tab polish
- About screen fork attribution
- Dialog, snackbar, settings, video detail, and download UI polish
- Release signing workflow support

Deferred or high-risk areas:

- Main player overlay retheme
- Seekbar and gesture overlay colors
- Queue overlay controls
- Quality/audio/caption popup behavior
- Broad playback/download behavior changes

Those areas need dedicated QA before visual or behavior changes.

---

## Contributing

Contributions are welcome, especially focused Material 3 polish, bug fixes, QA findings, documentation, and release-readiness work.

Please keep changes focused and testable. For UI work, include before/after screenshots where possible and verify Light, Dark, Black, Follow system, and at least one manual Theme color preset.

### PipePipeExtractor submodule

NewPipe Material builds against the `wizdom13/PipePipeExtractor` fork through the pinned submodule at `external/NewPipeExtractor`. Initialize it with:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

The submodule commit recorded by the NewPipe Material repository is part of the source definition. Do not replace it with the latest extractor branch tip when preparing a release or reproducible build.

Use the shared scripts for validation:

```bash
scripts/build.sh checkstyle
scripts/build.sh debug
```

Detailed instructions are maintained in [BUILDING.md](BUILDING.md).

---

## Upstream NewPipe

NewPipe Material is based on NewPipe.

Upstream resources:

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Please report issues carefully:

- Fork-specific design, identity, release, or Material 3 issues belong in this repository.
- Upstream extractor/service breakages may also need to be checked against official NewPipe.

---

## Donate

If you want to support upstream NewPipe, see the official NewPipe donation page:

https://newpipe.net/donate

NewPipe Material is an independent fork; upstream donations go to the upstream NewPipe project, not automatically to this fork.

---

## License

NewPipe Material is free software based on NewPipe and is distributed under the GNU General Public License version 3 or later.

See the repository license files and in-app license screen for full license and third-party notice details.
