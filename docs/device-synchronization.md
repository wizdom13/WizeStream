# Device synchronization

WizeStream can synchronize supported app data directly between trusted WizeStream
devices. It does not require a platform login or a WizeStream cloud account.

## Pair two devices

Both devices must have a WizeStream version that supports device
synchronization and must be able to reach each other on the same Wi-Fi,
Ethernet, or local hotspot network. Synchronization does not use the public
internet.

1. Open **Settings > Device synchronization** on both devices.
2. On the first device, select **Show pairing code** and keep the code visible.
3. On the second device, select **Scan pairing code** and scan the first
   device's QR code.
4. Wait for both devices to confirm that pairing succeeded.

The QR code expires after five minutes and can be used only once. Pairing
establishes trust between the two device identities; later synchronization
does not require rescanning the code.

Use **Clear trusted devices** to remove every pairing. Each device must be
paired again before it can synchronize.

## Synchronize now or in the background

Select **Sync now** to exchange data immediately with reachable trusted
devices. The result lists the data categories exchanged with each device and
any category or device that needs attention.

**Automatic background synchronization** is enabled after pairing unless it
is turned off. WizeStream attempts a background sync approximately once per
hour when:

- The device has sufficient battery.
- The device is connected through Wi-Fi or Ethernet.
- A trusted device is reachable on the same Wi-Fi, Ethernet, or hotspot network.

A trusted device being temporarily unavailable does not remove the pairing.
Use **Sync now** when both devices are available if an immediate update is
needed.

## Synchronized data

WizeStream synchronizes:

- Subscriptions
- Feed groups
- Local and remote playlists
- Watch history and playback progress
- Home-tab configuration
- Content-filter selections
- Per-channel playback profiles
- Other explicitly allowlisted settings
- Completed-download metadata

Search history is private by default. It is synchronized only when
**Synchronize search history** is enabled on both devices.

Synchronization merges supported records from both devices. It is not a
one-way replacement or a full-device backup. Keep using
**Settings > Backup and restore** when you need a portable backup.

## Downloads and media files

Only completed-download metadata is synchronized. WizeStream does not
transfer downloaded video or audio files between devices.

When synchronized metadata describes a download whose file is missing on the
current device, the Downloads screen shows the item as **Not local**. Select
it and choose **Download on this device** to open the original source in
WizeStream and use the normal download dialog. This resolves currently
available streams and lets you choose the format and quality for this device.

If the same source and media type is already downloaded or pending locally,
WizeStream hides the metadata-only duplicate.

The original source may no longer be available or may have changed since the
other device completed its download. In that case, WizeStream cannot recreate
the missing local media.

## Privacy and security

- Synchronization uses an encrypted peer-to-peer connection between paired
  device identities.
- Only trusted devices can participate after the one-time QR pairing succeeds.
- Search history remains excluded unless it is explicitly enabled on both devices.
- Media-file contents are never transferred by device synchronization.
- Clearing trusted devices requires every device to pair again.

Review the project's [privacy policy](../PRIVACY.md) for the broader app
privacy model.
