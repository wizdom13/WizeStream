# WizeStream Desktop FAQ

## What is WizeStream Desktop?

WizeStream Desktop is the Windows, macOS, and Linux version of WizeStream. It provides
privacy-friendly streaming, subscriptions, playlists, history, downloads, local media playback,
device synchronization, SponsorBlock support, and optional learning tools in a desktop interface.

## Which operating systems are supported?

The Desktop app is built for:

- Windows x64
- macOS Intel and Apple Silicon
- Linux x64 and arm64

Packages are provided as a Windows installer and portable executable, macOS DMG and ZIP files,
and Linux AppImage and DEB files.

## Is WizeStream an official NewPipe application?

No. WizeStream is independently maintained and is not affiliated with, sponsored by, or endorsed
by the official NewPipe project, TeamNewPipe, or NewPipe e.V. WizeStream is built from NewPipe and
preserves its libre software license, upstream credits, and third-party notices.

## Why does my computer warn me before opening a beta?

Current Desktop betas are explicitly unsigned. Windows may show an unknown-publisher or
SmartScreen warning, and macOS Gatekeeper may block the app until you deliberately allow it.
Download betas only from the official WizeStream GitHub repository, verify `SHA256SUMS`, and
install them only if you understand and accept these warnings.

## How do Desktop beta updates work?

Beta updates are manual. Production automatic updates and updater metadata are disabled for
unsigned betas. Download a newer beta from the official GitHub release and install it using
the normal method for your operating system. Your local WizeStream data should remain available,
but keeping a recent backup is recommended.

## Where is my WizeStream data stored?

Settings, subscriptions, playlists, history, playback progress, and other app data are stored
locally in the operating system's application-data directory. Downloads are stored in the
`WizeStream` folder inside your system Downloads folder. WizeStream does not require an account.

## Can I move my subscriptions between Android and Desktop?

Yes. In Android, export subscriptions or create a full backup. In Desktop, open
**Settings > Backup and restore > Import subscriptions only** and select the Android JSON export or
full-backup ZIP. Existing subscriptions are merged rather than replaced.

## What does a full Desktop backup include?

A full backup includes subscriptions, playlists, settings, SponsorBlock preferences, history,
search history, and Learning Mode notes. Use **Settings > Backup and restore** to export or restore
a ZIP backup. Keep the original backup until you have verified the restored data.

## How does device synchronization work?

Open **Devices** on two WizeStream devices, pair them using the one-time invitation, and choose the
data categories you want to share. Paired devices synchronize directly over the local network.
Downloaded video and audio files are not transferred; only supported app data and completed-download
metadata are synchronized.

## How does SponsorBlock work on Desktop?

SponsorBlock is disabled by default. Enable it under **Settings > SponsorBlock**, choose the segment
categories, and select whether each category is skipped automatically, shown for manual skipping,
or only marked on the timeline. When enabled, WizeStream contacts SponsorBlock for videos you open,
so SponsorBlock can see your IP address.

## Can WizeStream play files already stored on my computer?

Yes. Use the local media option to open supported video or audio files. Playback support depends on
the bundled media components and the file's codecs. Local files stay on your computer.

## What should I try when a video does not play?

Try another video or stream quality first. Check that your internet connection works and that the
streaming service is available in your region. If the issue affects only one video, include its
public URL when reporting the problem. Never post private links, cookies, passwords, or personal
backup files in a public issue.

## Where can I report a problem or request a feature?

Use the [WizeStream issue forms](https://github.com/wizdom13/WizeStream/issues/new/choose). Include
your operating system, WizeStream version, package type, clear reproduction steps, and any safe
error message. For beta-package problems, also include the exact release tag and filename.

## Where can I read the privacy policy and license?

Read the [WizeStream privacy policy](../../PRIVACY.md) and the
[GNU General Public License](../../LICENSE). WizeStream is libre software: you may use, study,
share, and improve it under the license terms.
