# WizeStream Desktop Android-Parity Roadmap

## Goal

Bring every useful Android feature to Desktop while adapting mobile-only behavior to Windows,
macOS and Linux conventions. Existing Desktop functionality must remain stable across all five
supported build targets.

## Milestone 1 — Playback foundation

Status: implemented in the Desktop client. Release validation remains part of the normal Desktop CI
and beta acceptance process.

- [x] Implement a persistent playback queue.
- [x] Add next, previous, reorder, remove and clear controls.
- [x] Add repeat-one, repeat-all and shuffle.
- [x] Add autoplay and automatic related-video enqueueing.
- [x] Extend the sleep timer with end-of-video and end-of-queue modes.
- [x] Add chapter navigation.
- [x] Add seek-bar preview thumbnails.
- [x] Add configurable seek duration and precise or inexact seeking.
- [x] Remember speed for live streams.
- [x] Add per-channel profiles for quality, audio, captions and playback speed.
- [x] Add configurable start-fullscreen and preferred opening actions.

## Milestone 2 — Desktop playback modes

Use desktop-appropriate equivalents for Android playback modes:

- Support background playback when the application is minimized.
- Add a compact always-on-top mini-player or picture-in-picture mode.
- Add a user-facing external-player option.
- Add Kodi playback where Kodi is installed.
- Add system media-key and media-session integration.
- Add configurable minimize and close behavior.
- Restore playback after an interrupted audio session.
- Add keyboard and mouse shortcuts corresponding to Android player gestures.
- Add optional touch gestures on touchscreen Desktop devices.

## Milestone 3 — Local media completion

- Add an **Open media** command and native file picker.
- Support local video, audio and subtitle files.
- Permit validated local paths through the Electron security boundary.
- Extract and display local metadata.
- Allow local items in history, playlists and Learning Mode.
- Test Unicode paths and Windows, macOS and Linux path formats.
- Correct the current documentation if any claimed format cannot be supported.

## Milestone 4 — Search, discovery and channels

- Add search suggestions.
- Add service-provided content, date, duration, feature and sorting filters.
- Add dedicated YouTube Music and Shorts destinations.
- Add trending and service kiosk destinations.
- Add complete channel tabs.
- Support channel videos, Shorts, live streams, playlists, podcasts, posts and About information.
- Add latest, popular and oldest channel sorting.
- Display members-only status and optionally hide members-only videos.
- Preserve channel avatars, subscriber counts, views and publication information throughout the
  interface.

## Milestone 5 — Subscriptions and library

- Add channel and feed groups.
- Support group creation, editing, deletion and reordering.
- Maintain independent service and feed refresh state.
- Add remote-playlist bookmarks.
- Add remote-playlist content sorting.
- Allow complete playlists to be played or added to the queue.
- Add playlist item reordering.
- Add removal with Undo.
- Add local searching across subscriptions, groups, playlists, feeds, history and downloads.
- Allow a local query to continue as an online service search.

## Milestone 6 — Learning Mode completion

- Add playlist completion progress.
- Add mark-all-watched and reset-progress actions.
- Add active and completed learning playlists.
- Add a Learning dashboard.
- Add continue-learning shortcuts.
- Add recently annotated videos.
- Track study time.
- Add current and longest learning streaks.
- Add a 28-day activity calendar.
- Allow background listening to be included or excluded from statistics.
- Synchronize the additional Learning Mode information between trusted devices.

## Milestone 7 — Settings parity

Add applicable Android settings to Desktop:

- Application and content language.
- Content country.
- PeerTube instance.
- Age-restricted content.
- YouTube Restricted Mode.
- Hide members-only videos.
- Search suggestions.
- Image quality.
- Visibility controls for comments, related items, description and metadata.
- Configurable channel tabs and feed-fetching behavior.
- Feed refresh threshold.
- Configurable home sections and default main page.
- Theme color presets and separate light and night preferences.
- Caption styling.
- Default list or grid preference.
- Navigation layout and label options.
- Show or hide dislike counts.
- Separate playback-resume and playback-state controls.
- Separate actions for clearing metadata, playback positions, search history and cookies.

## Milestone 8 — Downloads

- Add native video, audio and caption folder selection.
- Add ask-for-location mode.
- Add filename character-set and replacement rules.
- Add configurable retry limits.
- Add configurable simultaneous-download and queue limits.
- Add metered-network behavior where supported by the operating system.
- Improve completed-download sharing and opening actions.
- Preserve pause, resume, verification and lossless audio/video combining.

## Milestone 9 — Notifications and device synchronization

- Add new-stream notifications.
- Allow notification scheduling, network rules and channel selection.
- Add native operating-system media notifications and controls.
- Add trusted-device removal.
- Add clear-all-trusted-devices with confirmation.
- Optionally scan pairing QR codes through an available camera.
- Synchronize newly implemented groups, playlists, profiles, settings and Learning data.
- Investigate an optional background sync helper while keeping the existing app-open
  synchronization as the safe default.

## Milestone 10 — Updates and release readiness

- Keep manual updates available for unsigned beta releases.
- Add changelog preview and download progress.
- Add checksum and artifact-attestation validation.
- Prepare automatic updates behind a disabled feature flag.
- Enable production automatic updates only after trusted Windows signing and Apple
  signing/notarization are available.
- Preserve separate stable, beta and nightly release behavior.

## Platform-specific adaptations

Android-specific controls will not be copied blindly:

| Android behavior | Desktop equivalent |
| --- | --- |
| Brightness and volume swipe gestures | Keyboard, mouse-wheel and optional touch controls |
| Popup overlay | Always-on-top mini-player or picture-in-picture |
| Android Storage Access Framework | Native folder and file pickers |
| ExoPlayer decoder settings | Relevant libmpv diagnostics and recovery options |
| Android media notifications | Windows, macOS and Linux media-session integration |
| Mobile-data limits | Metered-network controls where the operating system exposes them |
| Android Auto and Android TV | Deferred unless a dedicated Desktop TV interface is requested |

## Quality requirements for every milestone

- Add backend and renderer tests.
- Preserve the Electron sandbox and narrow preload API.
- Keep database and synchronization changes backward-compatible.
- Verify accessibility, keyboard navigation and right-to-left text.
- Pass Android tests and the complete Desktop CI matrix.
- Update `desktop/README.md`, `desktop/docs/faq.md` and relevant release documentation.
- Do not enable signed releases or automatic production updates without separate explicit approval.
