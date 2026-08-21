# Android Auto

WizeStream integrates with Android Auto as an audio media app. The vehicle interface can browse
supported WizeStream content, continue recent listening, accept supported voice-search requests,
and control playback. For driving safety, Android Auto receives audio playback only: video and
Listen-mode visualizers are never displayed on the vehicle screen.

Android Auto support was added after WizeStream 1.8.0. Install a newer build before following this
guide.

## Normal setup

1. Install a current WizeStream build and open the app at least once.
2. Connect the phone to Android Auto by USB or wirelessly.
3. Open Android Auto's app launcher or **Customize launcher** settings.
4. Enable WizeStream if it is not already selected.

Stable builds installed from a trusted source should be discovered automatically.

## Testing a sideloaded build

Android Auto normally hides apps that were not installed from a trusted source. This includes debug,
nightly, CI, and other manually installed APKs. To test one of these builds, enable Android Auto's
developer option for unknown sources:

1. Open Android Auto settings on the phone.
2. Open **About** and tap **Version and permission info** 10 times.
3. Approve the prompt to enable Android Auto developer mode.
4. Open the overflow menu and select **Developer settings**.
5. Enable **Unknown sources**.
6. Disconnect and reconnect Android Auto, then check **Customize launcher** again.

This option is inside Android Auto. It is different from Android's general **Install unknown apps**
permission, and it should only be enabled when testing an APK from a source you trust.

## Available features

- Car-safe media browsing
- Voice-search playback for supported queries
- Play, pause, previous, next, and queue controls
- Media resumption after reconnecting
- A bounded **Continue listening** section
- Audio-only playback with no video or visualizer on the vehicle display

The visualizer style selected under **Settings > Video and audio > Visualizer style** applies to
Listen mode on the phone only.

## Troubleshooting

If WizeStream is still missing:

- Confirm that the installed build is newer than version 1.8.0.
- For a sideloaded APK, confirm that Android Auto developer mode and **Unknown sources** are enabled.
- Force-stop Android Auto, reconnect the phone, and check **Customize launcher** again.
- Open WizeStream once and start audio playback before reconnecting.
- Update Android Auto to the latest version available for the device.

When reporting a problem, include the WizeStream version, whether the build is stable or debug, the
Android and Android Auto versions, the connection type, and whether **Unknown sources** is enabled.

For Google's current testing requirements, see
[Test Android apps for cars](https://developer.android.com/training/cars/testing).
