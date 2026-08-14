# WizeStream changelogs

Release history is listed newest first. The number beside each release is its Android version code.

## WizeStream 1.6.0 (`1006000`)

### New features

- Added a built-in 10-band audio equalizer with five presets, automatic clipping headroom, and one persistent custom curve.
- Added read-only YouTube channel posts with text, images, polls, links, attached videos and playlists, and continuation-page support.

### Improvements

- Improved device synchronization recovery after hotspot or local-network address changes.

### Fixes

- Restored channel metadata in portrait layouts.
- Fixed timestamped note button visibility during Learning Mode playback.

[View the complete changes since v1.5.0](https://github.com/wizdom13/WizeStream/compare/v1.5.0...v1.6.0)

## WizeStream 1.5.0 (`1005000`)

### New features

- Added optional Learning Mode with timestamped notes, study sessions, playlist progress, and a learning dashboard.
- Added **Members only** badges for membership-restricted videos, an explanation when they are opened, and an option to hide them from content lists.
- Added swipe-to-remove with an Undo action for videos in local playlists.

### Improvements

- Restored the main-player position and playback state after returning from popup playback.

### Fixes

- Fixed repeated bookmark-removal actions and reduced memory usage when removing very large playlists.

[View the complete changes since v1.4.1](https://github.com/wizdom13/WizeStream/compare/v1.4.1...pipe)

## WizeStream 1.4.1 (`1004001`)

### Improvements

- Improved consistency between the main and video-detail navigation bars.

### Fixes

- Fixed a crash when displaying feed cards in landscape mode.

[View the complete changes since v1.4.0](https://github.com/wizdom13/WizeStream/compare/v1.4.0...pipe)

## WizeStream 1.4.0 (`1004000`)

### New features

- Introduced the new WizeStream visual identity across the launcher, splash screen, notifications, updater, and Android TV.
- Added channel avatars to stream lists and made channel identity areas open their channels.

### Improvements

- Restored conventional YouTube playback and downloading, removing SABR and its bundled runtime to reduce complexity and APK size.
- Optimized subscription feed refreshes by increasing lightweight RSS concurrency and replacing long blocking pauses with short, cancellable rate-limit delays only for full YouTube extraction batches.
- Made the two-finger playback-speed gesture respect the configured speed increment.
- Improved launcher-logo scale and themed-icon consistency.

### Fixes

- Fixed exiting fullscreen sometimes leaving the device locked in landscape orientation.
- Fixed release builds failing on duplicate Bouncy Castle license and notice metadata.

**Compatibility note:** YouTube playback and downloads now use conventional stream extraction. SABR support introduced in WizeStream 1.2.0 has been removed.

[View the complete changes since v1.3.0](https://github.com/wizdom13/WizeStream/compare/v1.3.0...pipe)

## WizeStream 1.3.0 (`1003000`)

### New features

- Added configurable bottom-navigation labels: **Always visible**, **Active only**, or **Hidden**.
- Added an optional two-finger vertical swipe gesture for adjusting playback speed.

### Improvements

- Optimized release APKs for ARM devices to reduce download and installation size.
- Improved YouTube Music and Movies/Shows Trending charts.

### Fixes

- Changed the default playback speed from **1.2× to 1.0×** for new installations. Existing saved speeds remain unchanged.
- Improved recovery from temporary YouTube media-server DNS failures while preserving playback position.
- Fixed playback stopping during longer SABR redirect chains.
- Added redirect-loop detection and bounded stream-information recovery.
- Locked single-finger player gestures to their initial direction, preventing volume or brightness gestures from unexpectedly becoming seeking gestures—and vice versa.
- Fixed Nightly release cleanup occasionally deleting the newly published build.

**Compatibility note:** Official release APKs now support ARM64 and ARMv7 Android devices. x86 and x86_64 device builds are no longer included.

[View the complete changes since v1.2.0](https://github.com/wizdom13/WizeStream/compare/v1.2.0...pipe)

## WizeStream 1.2.0 (`1002000`)

- Added YouTube SABR playback and downloading.
- Added dedicated YouTube Music and YouTube Shorts destinations.
- Separated Subscriptions and What's New by service, including independent refresh states.
- Added remote playlist sorting.
- Improved device synchronization and player transitions.
- Fixed WebView and large-download crashes, channel headers, playlist counts, and synchronization stability.

## WizeStream 1.1.1 (`1001001`)

- Redesigned local search with a dedicated floating button for searching the selected streaming service.
- Automatically carries the typed query into the online search.
- Keeps local and online search actions clearly separated.
- Improved search-button positioning above bottom navigation and bottom tabs.
- Pressing Done now dismisses the keyboard without switching search modes.

## WizeStream 1.1.0 (`1001000`)

- Added secure peer-to-peer sync for subscriptions, playlists, history, settings and download metadata, including automatic background sync.
- Added "Download on this device" for synchronized media missing locally.
- Added search filters, channel sorting, podcast tabs and per-channel playback profiles.
- Added multi-audio selection with clearer original, dubbed and descriptive labels.
- Improved live-stream playback and fixed startup, artwork and migration issues.

## WizeStream 1.0.0 (`1000000`)

- Adopted an independent semantic version and release cycle.
- Added unified filters for stream lists.
- Fixed subscription import when a source has no matching placeholder.
- Improved release and update validation.

## WizeStream 0.28.8-m14 (`101314`)

### Hotfix

- Fixed a crash when opening or restoring local search with no matching results.
- Added release-build and Android API 23/35 regression checks.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m13 (`101313`)

- Completed project-wide rebranding from NewPipe Material to WizeStream.
- Added sleep timer presets, custom time, end-of-video/queue and fade-out.
- Added search to subscriptions, playlists, feeds, history and Downloads.
- Modernized video details navigation while keeping playback visible.
- Hardened updates with checksum and APK identity checks.
- Fixed YouTube reloads, metadata and double splash on older Android.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m12 (`101312`)

- Added swipe down to the miniplayer without disabling fullscreen swipe, with a default-on toggle.
- Added an option to keep the video visible while scrolling.
- Added view and subscriber counts.
- Positioned the miniplayer above bottom navigation.
- Improved the splash transition, playlist dialog theming, and update dialogs on small screens.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m11 (`101311`)

- Fixed playback option checkboxes that could render incorrectly.
- Fixed the search keyboard not closing reliably on older Android versions.
- Improved the warning shown before enabling GitHub update checks.
- Pinned PipePipeExtractor to ensure consistent and reproducible builds.
- Unified local, CI, and external builds through shared build scripts and updated documentation.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m10 (`101310`)

- Added customizable Home navigation with Downloads and smarter drawer entries.
- Added per-category SponsorBlock behavior, colors, presets, and playback improvements.
- Polished SponsorBlock settings with app switches, aligned headers, and icons.
- Updated the launcher icon to follow the Android 12+ system palette.
- Improved update downloads on Android.
- Clarified GitHub update safety and handled missing browsers.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m9 (`101309`)

- Added a clearer in-app update flow with changelog preview, APK download progress, and install prompt.
- Improved Backup/Restore safety and user experience.
- Improved large-screen and landscape layouts with Material 3 styling.
- Updated launcher, splash, and TV banner artwork to use Material 3 colors.
- Added playback retry handling for HTTP 403/404 errors.
- Recognized WizeStream release builds correctly.
- Fixed PipePipeExtractor source checkout instructions.
- Disabled Android dependency metadata in APK builds.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m8 (`101308`)

- Updated the app base version to NewPipe 0.28.8.
- Improved YouTube thumbnail, poster, and avatar loading.
- Improved YouTube embedded web playback request handling.
- Fixed channel tab pagination compatibility with the updated extractor.
- Improved release build stability.

Based on NewPipe 0.28.8.

## WizeStream 0.28.7-m7 (`101207`)

- Switched to the latest PipePipeExtractor source checkout to improve YouTube compatibility.
- Updated app integration for the new extractor API and Java 21 bytecode requirements.
- Improved YouTube live and post-live stream compatibility through the updated extractor.
- Improved YouTube rating compatibility when likes and dislikes are not provided directly by the extractor.
- Suppressed non-critical optional metadata extraction warnings in release builds when stream loading still succeeds.
- Continued extractor compatibility and release stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m6 (`101206`)

- Added an Updates settings screen with current version, installed app version, manual update checks, changelog viewing, and optional automatic background update checks.
- Improved update checks for WizeStream GitHub Releases, including prerelease-aware release selection and in-app manual check results.
- Added Swipe seek, Fullscreen swipe, and Hold to speed up player gesture settings under Video and audio.
- Added Show dislikes in Appearance, enabled by default where supported by the extractor.
- Added SponsorBlock settings, category controls, automatic skipping, manual skip overlay button, and seek-bar segment markers for supported YouTube segments.
- Improved YouTube quality availability through the updated extractor client strategy, allowing higher adaptive qualities such as 720p and 1080p when available.
- Continued Material settings polish and stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m5 (`101205`)

- Added helpful summaries to the main settings categories.
- Switched to the modified NewPipeExtractor source checkout with HLS fallback support to improve livestream playback reliability.
- Added README instructions for setting up the NewPipeExtractor source checkout.
- Fixed bottom navigation staying visible when only one main page tab is enabled.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m4 (`101204`)

- Cleaned up release workflow after pausing custom F-Droid repo publishing.
- Kept GitHub release/artifact publishing with APK signature verification.
- Shortened release changelog metadata for F-Droid readiness.
- Prepared release metadata flow for IzzyOnDroid and official F-Droid submission.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m3 (`101203`)

- Added localized Fastlane metadata and store assets.
- Updated screenshots and feature artwork.
- Improved player popup readability.
- Improved comment reply contrast.
- Expanded contribution and release QA docs.
- Cleaned up README release links.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m2 (`101202`)

- Added WizeStream version identity using the 0.28.7-m2 format.
- Updated release workflow to publish signed APKs from m-tags.
- Added per-release changelog support from `changelogs/m*.txt`.
- Improved GitHub release naming and APK artifact naming.
- Continued Material polish and stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m1 (`101201`)

- First WizeStream release based on NewPipe 0.28.7.
- Added Material 3 visual polish across the app.
- Improved bottom navigation with Material-style bottom navigation behavior.
- Improved About screen fork attribution.
- Added WizeStream identity and fork attribution.
- Fixed fullscreen status bar restoration after rotating video playback.
- Removed flaky live YouTube dependency from subscription instrumentation tests.
- Completed Material readiness audit and release QA documentation.

Based on NewPipe 0.28.7.
