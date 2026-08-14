# WizeStream Privacy Policy

Last updated: August 14, 2026

WizeStream is an independently maintained, open-source, multi-platform streaming application for
Android, Windows, macOS and Linux. It does not require a WizeStream account and does not include
advertising, analytics, or tracking SDKs.

## Network requests

WizeStream connects directly to the media services you choose to use. Those services and your network provider can receive ordinary connection information such as your IP address, request details, and cookies required for the selected service. Their own privacy policies apply to that activity.

On Android, WizeStream contacts the GitHub Releases API for `wizdom13/WizeStream` when update checks
are enabled or manually requested. The app does not add a WizeStream account identifier or
advertising identifier to those requests. Production automatic updates are disabled in the current
Desktop preview; Desktop updates are downloaded manually.

## Device synchronization

Device synchronization is optional and does not require a WizeStream cloud account. After you pair
two devices with a one-time invitation (shown as a QR code where supported), WizeStream can exchange
supported records directly through an encrypted peer-to-peer connection between those trusted
device identities.

Synchronized records can include subscriptions, feed groups, playlists, watch history, playback
progress, home tabs, content-filter selections, channel playback profiles, allowlisted settings, and
completed-download metadata. Search history is excluded by default and is exchanged only when the
option is enabled on both devices.

Downloaded video and audio files are not transferred. Completed-download metadata can include the
original content URL, media type, filename, MIME type, file size, and completion time so another
device can identify the item and offer to download it again from the original media service.

Trusted-device identities and synchronization state are stored locally. Automatic synchronization
periodically attempts to reach paired devices through Wi-Fi or Ethernet. Android observes its
battery and background-execution conditions; Desktop synchronization runs while WizeStream Desktop
is open. Clearing trusted devices removes their authorization and requires them to pair again.

## Crash and error reports

WizeStream does not upload crash reports automatically. On Android, the error screen lets you copy
a report or explicitly choose to send it through an email application or GitHub. Before sending,
you can review and edit your comment. A report can include the requested URL, service, app language,
content country and language, package and app version, Android version, timestamp, exception
details, and your comment. Desktop preview reports are submitted manually through the repository's
Desktop issue form after you review and redact any attached logs.

Email providers and GitHub process information according to their own privacy policies. GitHub issue reports can be public, so do not include passwords, authentication cookies, private links, or other sensitive information.

## Data stored on your device

Subscriptions, playlists, history, settings, cookies, downloads, and backups are stored locally on
your device or in a location you select. WizeStream only exports data when you choose an export
action. Removing the application or clearing its storage removes app-private data, subject to the
operating system and any backup provider you use.

## Permissions

On Android, WizeStream requests permissions only for features you use, such as notifications,
background playback, overlay playback, network access, camera access for scanning a device-pairing
QR code, and legacy file access on supported older Android versions. Android settings can revoke
optional permissions.

On Desktop, WizeStream uses ordinary operating-system access needed for network playback,
user-selected downloads and local peer-to-peer synchronization. Browser-style navigation, popups
and renderer permission requests are denied by default. Operating-system settings can revoke any
permission granted to the Desktop application.

## Changes and contact

Policy changes are published in this repository. For privacy questions, use the WizeStream repository's issue form without including sensitive personal information.

WizeStream is based on NewPipe but is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. This policy applies to WizeStream; upstream projects maintain their own policies.
