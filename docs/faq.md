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

## Does WizeStream use NewPipeExtractor or PipePipeExtractor?

WizeStream uses extractor source integrated directly into its app module. That source is derived
from NewPipeExtractor and later incorporated PipePipeExtractor and WizeStream-specific changes. It
is therefore neither the unmodified official NewPipeExtractor nor an external PipePipeExtractor
dependency. The exact extractor source is versioned and released together with each WizeStream
build.

This integrated source supports WizeStream's services and features such as YouTube SABR playback.
WizeStream maintains these changes and accepts responsibility for defects caused by them.

## Where should I report a problem?

Use the WizeStream repository's issue form for every problem observed in WizeStream, including
service or extractor failures. Maintainers may ask whether the same problem occurs in official
NewPipe to identify a shared upstream defect, but that comparison is not a reason to reject a
WizeStream report. Report upstream only when the problem is reproducible there and follows the
upstream project's reporting rules. Do not include private URLs, cookies, passwords, or other
sensitive data in a public report.
