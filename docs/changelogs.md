# WizeStream changelogs

Release history is listed newest first. The number beside each release is its Android version code.

## Unreleased

## WizeStream 1.11.0 (`1011000`)

### New features

- Added BitChute and Rumble browsing, search, channels, comments, live streams, and playback.

### Improvements

- Added direct notification keyword management to subscribed channel menus.
- Polished feed refreshing with a translucent overlay, circular progress, and immediate cancellation.
- Updated colors, typography, components, seekbars, and navigation indicators to Material 3.
- Added adaptive bottom navigation and navigation rails for phone, tablet, and landscape layouts.
- Improved player-control accessibility and touch targets.

### Fixes

- Prevented feed refreshes and filters from crashing when the displayed list shrinks to zero items.
- Prevented the circular feed refresh indicator from being clipped along its edges.
- Handled Rumble videos that do not provide related items.
- Kept navigation and drawer content clear of status bars, display cutouts, gesture navigation,
  and three-button navigation while preserving fullscreen playback.
- Hardened YouTube search suggestions and feed view-count parsing against malformed metadata.
- Prevented preference updates from reaching a destroyed feed view and handled unsupported channels.
- Prevented Android 6 device-sync linkage failures, bounded the image cache, and reused player
  thumbnails to reduce memory pressure.
- Rejected incompatible foreign databases before they could replace WizeStream data.

[View the complete changes since v1.10.4](https://github.com/wizdom13/WizeStream/compare/v1.10.4...v1.11.0)

## WizeStream 1.10.4 (`1010004`)

### New features

- Enabled BiliBili and NicoNico as selectable streaming services.
- Added reusable saved-search feeds with service-specific filters, persistent bounded result
  caching, pagination, deduplication, manual refresh, and cached-result fallback.

### Improvements

- Reproducible release APK builds via pinned toolchain.
- Auto-refreshes Subscriptions/What's New on service switch.

### Fixes

- Instantly halts deleted downloads & clears temp files.
- Fixed empty-body POST requests in extractors.
- Prevented FCast crashes from missing stream manifests.
- Handled BiliBili risk-control blocks & fallbacks.
- Restored YouTube HD, audio & download options.
- Added Retry dialog for unstarted livestreams.
- Fixed layout overlap in portrait videos.  

[View the complete changes since v1.10.3](https://github.com/wizdom13/WizeStream/compare/v1.10.3...v1.10.4)

## WizeStream 1.10.3 (`1010003`)

### Fixes

- Restored live playback when a service provides only an HLS or DASH manifest without separate
  audio or video stream entries.
- Kept manifest-only YouTube live streams playing by preferring their refreshable HLS source over
  a finite DASH DVR window.
- Fixed selected bottom-navigation labels occasionally being truncated until the app was
  restarted.

[View the complete changes since v1.10.2](https://github.com/wizdom13/WizeStream/compare/v1.10.2...v1.10.3)

## WizeStream 1.10.2 (`1010002`)

### Fixes

- Restored playback for YouTube videos incorrectly reported as unavailable by using the standard
  Android Reel request path and retaining the existing client fallbacks.
- Retried transient YouTube page-reload responses once automatically before showing an error.
- Bounded persistent database growth by clearing feed caches and unreferenced stream metadata,
  compacting inactive sync journals while preserving paired-device synchronization state.

[View the complete changes since v1.10.1](https://github.com/wizdom13/WizeStream/compare/v1.10.1...v1.10.2)

## WizeStream 1.10.1 (`1010001`)

### Improvements

- Sped up MP3 encoding while retaining the selected output quality.
- Kept caption controls accessible on small screens.

### Fixes

- Restored audio downloads when only muxed MP4 streams are available and made the download dialog
  fit its content while remaining scrollable on smaller screens.
- Stopped active or queued downloads immediately when their entries are deleted, while preserving
  the previous state when the deletion is undone.
- Restored channel avatars and banner artwork, including reliable banner rendering inside the
  collapsible channel header.

[View the complete changes since v1.10.0](https://github.com/wizdom13/WizeStream/compare/v1.10.0...v1.10.1)

## WizeStream 1.10.0 (`1010000`)

### Fixes

- Fixed the bottom navigation occasionally remaining translated off-screen after an interrupted
  player-sheet transition, leaving a clipped blank area until the app was restarted.
- Fixed NewPipe subscription imports failing completely when one channel is unavailable; valid
  channels now import independently and the result reports imported and skipped counts.
- Made history date scrolling transient, consistently show month and year, and process large
  histories without blocking the interface or unnecessarily reloading the database.
- Fixed Material You wallpaper colors falling back to green with the Black night theme, while
  preserving true-black surfaces, and made the player seek bar follow the selected accent color.

### Improvements

- Moved the History date scrollbar closer to the screen edge while preserving its accessible touch
  target, and added smooth cancellable fade transitions.

### New features

- Added separate x86_64 release APKs for Waydroid and Android-x86 environments while keeping the
  existing ARM release download unchanged.
- Added optional on-device MP3 audio downloads at 128, 192, 256, or 320 kbps, with conversion
  progress, cancellation-safe temporary files, and title, uploader, and source URL metadata.
- Added per-channel keyword and phrase filters for new-stream notifications.
- Added date-aware fast scrolling and a calendar jump action for navigating large watch histories.
- Added an option to prioritize main-tab swiping when viewing pinned channels.

[View the complete changes since v1.9.1](https://github.com/wizdom13/WizeStream/compare/v1.9.1...v1.10.0)

## WizeStream 1.9.1 (`1009001`)

### Fixes

- Kept channel metadata and Subscribe controls reachable while the banner collapses, including in
  landscape and on channels without banners.
- Fixed YouTube Android VR AV1/HFR streams repeatedly failing with HTTP 403 by temporarily falling
  back to the nearest available stream without changing the saved quality preference.
- Fixed duplicate global and contextual search actions appearing together on channel pages.
- Fixed channel names and avatars not opening their channels from History and local playlists.

[View the complete changes since v1.9.0](https://github.com/wizdom13/WizeStream/compare/v1.9.0...v1.9.1)

## WizeStream 1.9.0 (`1009000`)

### New features

- Added a first-class Listen mode that switches videos to audio-only playback without losing the
  queue or playback position and keeps the normal player controls available.
- Added a permission-free spectrum visualizer for Listen mode, driven directly by decoded player
  audio without microphone access.
- Added Android Auto media browsing and voice-search playback for audio-only listening, with video
  and visualization intentionally excluded from the driving surface.
- Added Android Auto media resumption, a bounded Continue listening section, car-friendly content
  style hints, and time-limited browse requests for a more reliable driving experience.
- Added continuous pinch-to-zoom from 100% to 400% and two-finger panning in the main player, with
  a default-enabled Behavior setting, while preserving the vertical two-finger speed gesture.
- Added opt-in native Android picture-in-picture for visible video playback on Android 8 and newer,
  with automatic Android 12+ entry, media-session controls, and popup-player fallback on older
  devices.
- Added app-wide HTTP and SOCKS5 proxy configuration for remote browsing, playback, images, and
  downloads, including securely stored proxy authentication and automatic local-network bypass
  for casting and device synchronization.

### Improvements

- Added 15 selectable Listen-mode visualizers and a distinct waveform icon so Listen mode is no
  longer confused with the Background player action.
- Fixed History filter chips not updating the displayed watched-video list.
- Added bulk video and audio downloads directly from local playlists; entries that already point to
  local media files are skipped.

### Fixes

- Fixed rotating a playing video to landscape no longer entering fullscreen after native
  picture-in-picture configuration handling was added.
- Fixed a crash when opening video details in landscape on large-screen devices.
- Fixed channel metadata and Subscribe controls disappearing on channels without banners.
- Fixed playback failing with an audio visualizer runtime error on newer Android versions.

[View the complete changes since v1.8.0](https://github.com/wizdom13/WizeStream/compare/v1.8.0...v1.9.0)

## WizeStream 1.8.0 (`1008000`)

### New features

- Added bulk video and audio downloads for complete playlists and loaded play queues, with default-quality selection, collision-safe filenames, and optional track-number prefixes.
- Added local content blocking for individual videos, channels, and title/uploader keywords, with long-press actions and a dedicated management screen.
- Added in-page search for channel tabs, remote playlists, and local playlists.
- Added optional DeArrow titles and thumbnails for YouTube lists and video details, with request deduplication, in-memory caching, and automatic fallback to original metadata.
- Added TV casting to discovered FCast and Chromecast-compatible receivers from video details on
  Android 8 and newer.

### Improvements

- Embedded title, uploader, genre, upload date, and source URL metadata in generated M4A, MP4, and Opus downloads.
- Improved subscription group management with an Import shortcut and a clearer Material 3 creation and editing dialog with guided validation.

### Fixes

- Fixed channel and playlist search crashing when restored before the initial content load completed.

[View the complete changes since v1.7.1](https://github.com/wizdom13/WizeStream/compare/v1.7.1...v1.8.0)

## WizeStream 1.7.1 (`1007001`)

### Improvements

- Improved recovery and client fallback for expiring or rejected YouTube media URLs.

### Fixes

- Fixed YouTube playback stopping with HTTP 403, including streams failing around the one-minute mark.
- Fixed media.ccc.de live streams failing to open.

[View the complete changes since v1.7.0](https://github.com/wizdom13/WizeStream/compare/v1.7.0...v1.7.1)

## WizeStream 1.7.0 (`1007000`)

### New features

- Added local music and video browsing and playback, including search, filters, thumbnails, background playback, popup playback, and queue integration.
- Added local media information such as format, resolution, duration, capture date, and audio quality.

### Improvements

- Improved recovery from temporary YouTube playback failures by refreshing media URLs with bounded retries while preserving playback position.

### Fixes

- Restored YouTube comment author avatars.
- Fixed UTF-8 YouTube search queries.
- Fixed the app-data export picker on Android 8.
- Fixed bookmark removal before playlist loading completes.

[View the complete changes since v1.6.0](https://github.com/wizdom13/WizeStream/compare/v1.6.0...v1.7.0)

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
