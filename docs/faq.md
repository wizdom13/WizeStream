# WizeStream FAQ

## What is WizeStream?

WizeStream is an independent, NewPipe-based streaming client for Android. It combines a modern
Material 3 Expressive interface with privacy-friendly playback, useful local-first features, and
support for multiple streaming platforms. Material 3 is an important design goal, not the
project's only purpose.

## Is WizeStream an official NewPipe release?

No. WizeStream is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. Upstream attribution and license notices are preserved in the app and repository.

## Why does the package ID still contain `newpipematerial`?

The stable application ID remains `org.wisso.newpipematerial` so existing WizeStream installations can receive updates without losing app data. Changing it would create a separate Android application. Internal upstream namespaces, database identities, preference keys, and notification-channel IDs are also retained where changing them would break compatibility.

## Can WizeStream and official NewPipe be installed together?

Yes. They use different Android application IDs. WizeStream Nightly also has a separate application ID and can be installed alongside the stable build.

## Can I import an existing NewPipe backup?

WizeStream supports compatible NewPipe backup data. Export from the source app, open WizeStream's **Settings > Backup and restore**, and select the exported ZIP. Keep a copy of the original backup until you have verified the imported data.

## How does device synchronization work?

Open **Settings > Device synchronization** on two WizeStream devices, show the one-time QR code on
one device, and scan it with the other. Paired devices can synchronize directly over the same Wi-Fi
or Ethernet network, either manually or approximately once per hour in the background. See the
[device synchronization guide](device-synchronization.md) for full instructions.

## What data is synchronized?

Device synchronization supports subscriptions, feed groups, playlists, watch history, playback
progress, home tabs, content-filter selections, per-channel playback profiles, allowlisted settings,
and completed-download metadata. Search history is excluded by default and is exchanged only when
it is enabled on both devices.

## Are downloaded media files transferred between devices?

No. WizeStream synchronizes completed-download metadata, not video or audio files. A missing item is
shown as **Not local** and can be fetched from its original source with **Download on this device**.

## How should I update WizeStream?

Use the same source from which you installed it whenever possible. IzzyOnDroid users should update through their F-Droid-compatible client. Direct installations can use signed WizeStream GitHub releases or the in-app GitHub release checker.

## Which services are supported?

Service support comes from the extractor source integrated into each WizeStream build. See the
main [README](../README.md#supported-services) for the current list.

## How do bulk downloads work?

Open a remote or local playlist's menu and choose **Download playlist**, or open the play queue and
choose its download action. WizeStream loads the complete remote playlist when needed, then lets you
choose video or audio once for the batch. Each item uses its default available quality and audio
track. Track-number prefixes are optional, duplicate filenames are made unique automatically, and
entries that already point to local media files are skipped.

Bulk downloading requires valid default video and audio folders. Disable **Ask where to download**
and choose the folders under **Settings > Download** before starting a batch.

## How does content blocking work?

Long-press a video to block that video or its channel. Under **Settings > Content > Blocked
content**, you can enable or disable filtering, add title or uploader keywords, review blocked
videos and channels, remove individual rules, or clear everything. Matching remote videos,
channels, posts, playlists, feeds, search results, and related content are hidden locally. Blocking
does not unsubscribe from channels, delete history, or report anything to a service.

## What is DeArrow support?

DeArrow is an optional community service that provides descriptive YouTube titles and alternative
thumbnail frames. It is disabled by default. Enable it under **Settings > SponsorBlock > DeArrow**,
where title and thumbnail replacement can be controlled separately. If no accepted contribution is
available or the request fails, WizeStream keeps the original title and thumbnail.

## How do I zoom a video?

Pinch with two fingers over the main player to zoom continuously from 100% to 400%. Once enlarged,
drag with two fingers to move around the video. Pinch back to 100% or tap the Fit/Fill/Zoom control
to reset the custom zoom. A vertical two-finger swipe still changes playback speed when that gesture
is enabled and the video has not already been enlarged.

## How do I cast to a TV?

On Android 8 or newer, open a compatible video's details and select **Cast to TV**. WizeStream
discovers FCast and Chromecast-compatible receivers on the local network, sends the selected media
URL to the receiver, and provides play, pause, stop, and receiver-switching controls. Casting is
separate from Kodi support and does not require Google Play Services. Some streams cannot be cast
when the service does not expose a compatible HLS, DASH, or progressive media URL.

## Does WizeStream use NewPipeExtractor or PipePipeExtractor?

WizeStream uses extractor source integrated directly into its app module. That source is derived
from NewPipeExtractor and later incorporated PipePipeExtractor and WizeStream-specific changes. It
is therefore neither the unmodified official NewPipeExtractor nor an external PipePipeExtractor
dependency. The exact extractor source is versioned and released together with each WizeStream
build.

This integrated source supports WizeStream's services and conventional YouTube playback.
WizeStream maintains these changes and accepts responsibility for defects caused by them.

## Where should I report a problem?

Use the WizeStream repository's issue form for every problem observed in WizeStream, including
service or extractor failures. Maintainers may ask whether the same problem occurs in official
NewPipe to identify a shared upstream defect, but that comparison is not a reason to reject a
WizeStream report. Report upstream only when the problem is reproducible there and follows the
upstream project's reporting rules. Do not include private URLs, cookies, passwords, or other
sensitive data in a public report.
