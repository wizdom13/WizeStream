# Channel playback mute

The speaker button beside fullscreen changes the current video's audio immediately. When the source identifies its channel, the choice applies to that service and channel's other videos and survives app restarts. Pressing it again removes the saved mute. The existing Mute action in the extra player tools uses the same state.

The main player keeps this control in the seek row beside fullscreen. Popup playback uses a compact pair of over-video mute and fullscreen buttons, preserving room for timestamps and seeking at its minimum width. Very small popups have a minimum height of 96dp to keep the two control rows separate; the video continues to fit within the window. The accessible label identifies whether the next action will mute or unmute the channel. Audio, Listen mode and live playback use the same player state.

## Identity and lifetime

- The key uses the existing channel playback profile identity: service ID and the SHA-256 of the uploader URL. It is app-wide, matching playback profiles, rather than tied to a subscription or user profile.
- Explicit mute choices are remembered even when automatic speed, quality and caption profiles are disabled. Subscription feed muting is a separate setting.
- Local files and streams without a channel have a temporary choice for the current item. A different item with no saved choice starts unmuted.
- A click made before channel metadata arrives remains authoritative. Matching metadata attaches that choice to the channel; stale metadata from a previous item cannot change it. Placeholder source tags do not represent a different video.
- Queue selection applies known mute state before media preparation or seeking. Media3 transitions also resolve the active source, including transitions that reuse stream metadata or audio decoders. Engine replacement reapplies the selected volume before playback resumes.

## Persistence and transfer

Saved mute values are Boolean `.muted` entries under the existing `channel_playback_profile.v1` preference prefix. Unmute removes the entry. WizeStream's full settings backup includes these entries through its normal typed preference export and restore.

Device Sync continues to transfer the existing speed, quality and caption profile fields. It neither sends nor removes local mute entries; this keeps the current sync protocol compatible with older devices. Portable subscription exports do not carry playback mute choices.

## Verification

Focused behavioral tests cover service/channel separation, recreation, late metadata and explicit clicks, unknown and local items, stale source events, volume/focus callbacks, UI visibility and accessible actions, popup minimum width, directionality and the Device Sync boundary. Device validation should also exercise real audio output while a mixed-channel queue advances, then switch between main, popup, Listen and background playback. JVM tests do not measure physical audio output or replace Android device rendering checks.
