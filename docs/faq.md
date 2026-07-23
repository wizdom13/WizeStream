# WizeStream FAQ

## What is WizeStream?

WizeStream is an independently maintained Android streaming app based on NewPipe. It adds a distinct WizeStream identity, Material 3 design, playback enhancements, and its own update channel.

## Is WizeStream an official NewPipe release?

No. WizeStream is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. Upstream attribution and license notices are preserved in the app and repository.

## Why does the package ID still contain `newpipematerial`?

The stable application ID remains `org.wisso.newpipematerial` so existing WizeStream installations can receive updates without losing app data. Changing it would create a separate Android application. Internal upstream namespaces, database identities, preference keys, and notification-channel IDs are also retained where changing them would break compatibility.

## Can WizeStream and official NewPipe be installed together?

Yes. They use different Android application IDs. WizeStream Nightly also has a separate application ID and can be installed alongside the stable build.

## Can I import an existing NewPipe backup?

WizeStream supports compatible NewPipe backup data. Export from the source app, open WizeStream's **Settings > Backup and restore**, and select the exported ZIP. Keep a copy of the original backup until you have verified the imported data.

## How should I update WizeStream?

Use the same source from which you installed it whenever possible. IzzyOnDroid users should update through their F-Droid-compatible client. Direct installations can use signed WizeStream GitHub releases or the in-app GitHub release checker.

## Which services are supported?

Service support comes from the pinned WizeStreamExtractor source included with each WizeStream build. See the main [README](../README.md#supported-services) for the current list.

## Where should I report a problem?

Use the WizeStream repository's issue form for WizeStream-specific problems. Check whether a service or extractor failure also affects upstream NewPipe before reporting it. Do not include private URLs, cookies, passwords, or other sensitive data in a public report.
