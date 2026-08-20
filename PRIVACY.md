# WizeStream Privacy Policy

Last updated: August 20, 2026

WizeStream is an independently maintained, open-source Android streaming application. It does not require a WizeStream account and does not include advertising, analytics, or tracking SDKs.

## Network requests

WizeStream connects directly to the media services you choose to use. Those services and your network provider can receive ordinary connection information such as your IP address, request details, and cookies required for the selected service. Their own privacy policies apply to that activity.

When update checks are enabled or manually requested, WizeStream contacts the GitHub Releases API for `wizdom13/WizeStream`. The app does not add a WizeStream account identifier or advertising identifier to those requests.

DeArrow is optional and disabled by default. When enabled for YouTube, WizeStream sends the video's
public YouTube identifier to the community-operated DeArrow endpoints at `sponsor.ajay.app` and
`dearrow-thumb.ajay.app` to request accepted replacement titles and thumbnail frames. DeArrow
requests do not include a WizeStream account identifier or advertising identifier. The service and
your network provider receive ordinary connection information such as your IP address.

## Device synchronization

Device synchronization is optional and does not require a WizeStream cloud account. After you pair
two devices with a one-time QR code, WizeStream can exchange supported records directly through an
encrypted peer-to-peer connection between those trusted device identities.

Synchronized records can include subscriptions, feed groups, playlists, watch history, playback
progress, home tabs, content-filter selections, channel playback profiles, allowlisted settings, and
completed-download metadata. Search history is excluded by default and is exchanged only when the
option is enabled on both devices.

Downloaded video and audio files are not transferred. Completed-download metadata can include the
original content URL, media type, filename, MIME type, file size, and completion time so another
device can identify the item and offer to download it again from the original media service.

Trusted-device identities and synchronization state are stored locally. Automatic synchronization
periodically attempts to reach paired devices when the device has sufficient battery and is
connected through Wi-Fi or Ethernet. Clearing trusted devices removes their authorization and
requires them to pair again.

## TV casting

TV casting is initiated by the user. On Android 8 and newer, WizeStream can discover FCast and
Chromecast-compatible receivers on the local network. After you choose a receiver, WizeStream sends
that receiver the selected media URL and content type, together with the WizeStream version and the
Android device manufacturer and model used to identify the sender. The receiver then requests the
media directly from its original service, so that service and the receiver's network provider can
receive the receiver's ordinary connection information. WizeStream does not use Google Play
Services for discovery or casting.

## Crash and error reports

WizeStream does not upload crash reports automatically. The error screen lets you copy a report or explicitly choose to send it through an email application or GitHub. Before sending, you can review and edit your comment. A report can include the requested URL, service, app language, content country and language, package and app version, Android version, timestamp, exception details, and your comment.

Email providers and GitHub process information according to their own privacy policies. GitHub issue reports can be public, so do not include passwords, authentication cookies, private links, or other sensitive information.

## Data stored on your device

Subscriptions, playlists, history, settings, cookies, content-blocking rules, downloads, and backups
are stored locally on your device or in a location you select. WizeStream only exports data when you
choose an export action. Supported downloaded media can contain the public title, uploader, genre,
upload date, and original source URL as embedded file metadata. Removing the app or clearing its
storage removes app-private data, subject to Android and your backup provider's behavior.

## Permissions

WizeStream requests Android permissions only for features you use, such as notifications,
background playback, overlay playback, network and local-receiver discovery, camera access for
scanning a device-pairing QR code, and legacy file access on supported older Android versions.
Android settings can be used to revoke optional permissions.

## Changes and contact

Policy changes are published in this repository. For privacy questions, use the WizeStream repository's issue form without including sensitive personal information.

WizeStream is based on NewPipe but is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. This policy applies to WizeStream; upstream projects maintain their own policies.
