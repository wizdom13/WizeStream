# Desktop unsigned-preview testing

This checklist defines the Phase 9 manual acceptance pass for WizeStream Desktop. It supplements
the automated five-target Desktop CI matrix; it does not replace it.

## Safety and authenticity

1. Download the preview and `SHA256SUMS` only from the official
   [`wizdom13/WizeStream` releases page](https://github.com/wizdom13/WizeStream/releases).
2. Confirm that the release tag ends in `-unsigned-preview` and that the release notes identify the
   packages as explicitly unsigned.
3. Verify the downloaded package before opening it:

   - Windows PowerShell: `Get-FileHash -Algorithm SHA256 '<package>'`
   - macOS: `shasum -a 256 '<package>'`
   - Linux: `sha256sum '<package>'`

   Compare the complete result with the matching line in `SHA256SUMS`.
4. Treat an unexpected hash, unexpected publisher identity, or download from any other location as
   a failed test. Do not run the package.

Windows unknown-publisher or SmartScreen warnings and macOS Gatekeeper approval are expected for
the current unsigned preview. They are not evidence of a broken download when the SHA-256 value
matches the official manifest.

## Supported test matrix

| Platform | Architecture | Packages | Expected unsigned behavior |
| --- | --- | --- | --- |
| Windows | x64 | setup `.exe`, portable `.exe` | Unknown-publisher or SmartScreen warning may appear |
| macOS | x64 | `.dmg`, `.zip` | Gatekeeper approval may be required |
| macOS | arm64 | `.dmg`, `.zip` | Gatekeeper approval may be required |
| Linux | x64 | `.AppImage`, `.deb` | No publisher identity is provided |
| Linux | arm64 | `.AppImage`, `.deb` | No publisher identity is provided |

Use a clean user profile or virtual machine when practical. Record the release tag, exact package
filename, OS version, architecture, device model and SHA-256 result for every test pass.

## Acceptance checklist

For each platform and architecture:

- [ ] The package installs or extracts without corrupt or missing-file errors.
- [ ] WizeStream Desktop starts and presents a usable window rather than a blank screen.
- [ ] Closing and reopening the application preserves local data.
- [ ] Search returns results and opening a result loads its details.
- [ ] Video and audio playback start, pause, seek and stop correctly.
- [ ] Available audio and subtitle tracks can be selected without restarting the application.
- [ ] A progressive download completes to a valid playable file.
- [ ] An adaptive video-only download requires an audio choice, combines both components, and
      produces a valid playable file.
- [ ] Cancelling and retrying a download does not create a false completed state.
- [ ] Subscriptions, playlists, history and Learning Mode notes survive a restart.
- [ ] Pairing accepts only a valid, unexpired invitation and synchronization works with a trusted
      peer on the private local network.
- [ ] A stale hotspot address can recover the saved peer without trusting an unknown device.
- [ ] The application does not offer, download or install a production automatic update.

## Reporting a failure

Open a **Desktop preview bug report** in this repository and complete every required field. One
issue should describe one reproducible problem.

Never attach access tokens, private keys, pairing invitations, signed media URLs, or unredacted
private filesystem paths. Include the smallest useful redacted log or screenshot, the exact package
filename and exact reproduction steps.

## Phase 9 release gate

The existing `v0.6.0-beta.1-unsigned-preview` tag and assets are immutable. A second unsigned
preview is justified only when one or more material fixes change the tested binaries. A new preview
must use a higher beta version and a new tag; existing release assets must never be overwritten.

Signed public releases and production automatic updates remain postponed until maintainers adopt a
new release policy and configure trusted Windows and Apple signing identities.
