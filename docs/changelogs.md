# WizeStream changelogs

Release history is listed newest first. The number beside each release is its Android version code.

## Unreleased

## WizeStream 1.13.0 (`1013000`)

### New features

- Expanded Local Media with audio and video categories, SAF folder browsing, search, refresh,
  sorting, grouped views, and queue actions.
- Added NewPipe and PipePipe migration for history, playlists, compatible settings, and PipePipe
  SponsorBlock preferences.
- Added an option to enter fullscreen by physically rotating the phone even when Android rotation
  is locked.
- Added a Videos filter to What's New for standard non-live videos while keeping Shorts separate.
- Added equalizer and visualizer controls to the background audio queue player.

### Improvements

- Aligned Local Media with the rest of the app using a History-style search action, overflow menu,
  compact rounded search field, scrolling header, and full media refresh action.
- Made the primary player PiP action use Android's native Picture-in-Picture transition while
  keeping the legacy floating Popup player available separately.
- Smoothed fullscreen-to-mini-player transitions, preserved the current video frame during layout
  changes, and restored normal orientation when minimizing fullscreen playback.

### Fixes

- Prevented internal Play Queue navigation from accidentally triggering Picture-in-Picture.
- Guarded Picture-in-Picture and fullscreen-rotation paths on unsupported or not-yet-connected
  devices.
- Rebound and, when necessary, recreated the local-video SurfaceView after fullscreen transitions
  to recover from device-specific black video output without restarting playback.

[View the complete changes since v1.12.0](https://github.com/wizdom13/WizeStream/compare/v1.12.0...v1.13.0)

## WizeStream 1.12.0 (`1012000`)

### New features

- Added import of history and playlists from NewPipe-compatible backups.
- Added YouTube Music search filter chips.
- Added adjustable comment text and optional drawer service sections.

### Improvements

- Improved tablet video details, fullscreen rotation, grid titles, feeds, refresh indicators, and
  watched-progress presentation.

### Fixes

- Fixed BiliBili playback, blocked-keyword filtering, comments, playlist swipes, search, and
  avatars.

[View the complete changes since v1.11.5](https://github.com/wizdom13/WizeStream/compare/v1.11.5...v1.12.0)

## WizeStream 1.11.5 (`1011005`)

### New features

- Added optional full video titles in grid layouts.
- Added configurable tablet grid columns with responsive thumbnails.
- Added a copy-title action to video menus.

### Improvements

- Made automatic queues prefer Shorts when watching Shorts.
- Stabilized tablet video details after rotation.
- Made reproducible release builds portable across independent build environments.

[View the complete changes since v1.11.4](https://github.com/wizdom13/WizeStream/compare/v1.11.4...v1.11.5)

## WizeStream 1.11.4 (`1011004`)

### New features

- Added a tablet setting to place app navigation on the left rail or bottom bar.

### Fixes

- Prevented Android 6 feed filtering failures when opening or refreshing What's New.
- Made tablet grids recalculate from the available content width after navigation and orientation
  changes.
- Selected the single- or two-pane video-detail layout from the current screen width and removed
  navigation-rail gaps from expanded and fullscreen playback.

[View the complete changes since v1.11.3](https://github.com/wizdom13/WizeStream/compare/v1.11.3...v1.11.4)

## WizeStream 1.11.3 (`1011003`)

### Fixes

- Serialized the complete release pipeline, including Gradle, D8/L8, R8, and child JVMs, to
  prevent nondeterministic DEX interface ordering across independent build environments.
- Added a fail-fast processor-count check and limited reproducible release builds to one Gradle
  worker while retaining normal parallelism for debug and test builds.

[View the complete changes since v1.11.2](https://github.com/wizdom13/WizeStream/compare/v1.11.2...v1.11.3)

## WizeStream 1.11.2 (`1011002`)

### Fixes

- Kept fullscreen video edge-to-edge while protecting interactive controls from status bars,
  navigation bars, and display cutouts.
- Kept video-detail tabs and content above system navigation and restored compact fullscreen seek
  feedback.
- Prevented long fullscreen titles, channel names, and audio-track labels from being clipped.
- Reset video-surface geometry between queue transitions so cached return previews fill the player
  correctly, including rotated and anamorphic video.

[View the complete changes since v1.11.1](https://github.com/wizdom13/WizeStream/compare/v1.11.1...v1.11.2)

## WizeStream 1.11.1 (`1011001`)

### Fixes

- Kept the expanded video-detail player below the status bar and prevented the parent screen from
  covering it during loading transitions.
- Restored compact player-overlay text and icons while preserving 48dp accessible touch targets.
- Corrected the collapsed mini-player layout and hid content behind an opaque system-navigation
  surface.
- Hardened YouTube search suggestions and feed view-count parsing against malformed metadata.
- Prevented preference updates from reaching a destroyed feed view and handled unsupported channels.
- Prevented Android 6 device-sync linkage failures, bounded the image cache, and reused player
  thumbnails to reduce memory pressure.
- Rejected incompatible foreign databases before they could replace WizeStream data.

[View the complete changes since v1.11.0](https://github.com/wizdom13/WizeStream/compare/v1.11.0...v1.11.1)

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
