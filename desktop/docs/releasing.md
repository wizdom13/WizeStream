# Desktop release operations

## Current unsigned preview policy

WizeStream Desktop releases are unsigned open-source builds produced by GitHub Actions. Users
should download them only from the official repository and verify the supplied `SHA256SUMS` and
GitHub artifact attestations. Production automatic updates are not provided.

Preview upgrades are installed manually. Windows may show an unknown-publisher or SmartScreen
warning, and macOS Gatekeeper may require explicit user approval; these warnings are expected for
unsigned packages. Signed public releases and production automatic updates are postponed. The
future signing procedures below remain inactive unless the maintainers explicitly change this
policy and configure protected, trusted signing identities.

Phase 9 feedback and manual acceptance use the checklist in
[`preview-testing.md`](preview-testing.md). Preview failures must be reported through the dedicated
Desktop issue form with the exact release tag, package filename, platform and architecture. The
existing `v0.6.0-beta.1-unsigned-preview` release is immutable; publish a higher beta only after
material fixes pass the Android regression checks and the complete five-target Desktop matrix.

## Future signed-release setup (postponed)

1. Create the public `wizdom13/WizeStream_Desktop` repository with a `main` branch. Its README must
   link to `wizdom13/WizeStream` as the corresponding GPL source repository.
2. Create the `desktop-release` environment in the source repository. Require an explicit reviewer,
   limit deployment branches to `pipe`, and prevent administrators from bypassing the
   gate where the organization policy permits it.
3. Create a fine-grained GitHub token with Contents read/write access only to
   `wizdom13/WizeStream_Desktop`. Store it as `WIZESTREAM_DESKTOP_RELEASE_TOKEN` in the protected
   environment.
4. Add the signing secrets below to the same protected environment. Never put certificate data,
   private keys or passwords in an issue, pull request, workflow input, log, or chat.

| Secret | Value |
| --- | --- |
| `WIZESTREAM_DESKTOP_WINDOWS_CERTIFICATE` | Base64 PKCS#12 (`.pfx`/`.p12`) OV code-signing identity |
| `WIZESTREAM_DESKTOP_WINDOWS_CERTIFICATE_PASSWORD` | PKCS#12 password |
| `WIZESTREAM_DESKTOP_WINDOWS_PUBLISHER` | Exact certificate subject/publisher name |
| `WIZESTREAM_DESKTOP_MACOS_CERTIFICATE` | Base64 PKCS#12 export of the Developer ID Application identity |
| `WIZESTREAM_DESKTOP_MACOS_CERTIFICATE_PASSWORD` | PKCS#12 password |
| `WIZESTREAM_DESKTOP_APPLE_API_KEY` | Base64 contents of the App Store Connect `.p8` API key |
| `WIZESTREAM_DESKTOP_APPLE_API_KEY_ID` | App Store Connect API key ID |
| `WIZESTREAM_DESKTOP_APPLE_API_ISSUER` | App Store Connect API issuer ID |
| `WIZESTREAM_DESKTOP_APPLE_TEAM_ID` | Apple Developer Team ID |

The Windows workflow currently accepts an exportable OV identity. Azure Trusted Signing, an HSM or
a PKCS#11 provider is a supported future replacement when the workflow is changed to that signing
backend. Do not buy an EV hardware-token certificate expecting to export it as PKCS#12.

For macOS, enroll in the Apple Developer Program, create a **Developer ID Application** certificate,
and create an App Store Connect API key for CI notarization. Preserve the `.p8` file when it is
downloaded because Apple only offers it once.

## Credential-free validation

Every push to `pipe` runs the unsigned five-target Desktop CI matrix. It checks the
release contract, builds updater metadata with architecture-distinct filenames, executes unit and
native-media tests, smoke-tests the unpacked app, audits production dependencies, and uploads
short-lived workflow artifacts. It never reads the protected release environment.

From `desktop/`, the local contract check is:

```bash
npm ci --ignore-scripts
npm run release:verify
npm run typecheck
npm test
```

## Future signed beta release (postponed)

1. Confirm `pipe` is green and points at the intended source commit.
2. Open **Desktop signed beta release** in Actions and enter exactly `v0.6.0-beta.1`.
3. Approve the `desktop-release` environment deployment.
4. The gate verifies every protected credential and the separate release repository before any
   package job starts.
5. Native runners build all five targets. Windows validates every `.exe` Authenticode signature.
   macOS validates the packaged application signature, Gatekeeper assessment and stapled
   notarization ticket before uploading its DMG and ZIP containers.
6. The final job combines Intel and Apple Silicon metadata, creates `SHA256SUMS`, attests every
   release asset, and publishes a public prerelease.

Do not rerun a partially published tag with changed binaries. Delete a failed draft before it is
public, or increment the beta version and publish a new immutable tag.

## Future signed updater contract (postponed)

- Provider: public GitHub releases in `wizdom13/WizeStream_Desktop`.
- Channel: `beta`; prereleases are allowed and downgrades are disabled.
- Metadata: `beta.yml` for Windows, combined `beta-mac.yml` for macOS,
  `beta-linux.yml` for Linux x64, and `beta-linux-arm64.yml` for Linux arm64.
- Windows uses the NSIS update artifact; macOS uses ZIP update artifacts; Linux uses the matching
  AppImage/deb updater implementation.
- `electron-updater` validates update metadata hashes. Windows and macOS also enforce the operating
  system signing identity. Linux packages remain unsigned and are protected by SHA-512 metadata,
  the published SHA-256 manifest and GitHub provenance attestations.
- Startup and manual checks are allowed. Automatic download, install-on-quit, downgrade and silent
  restart are disabled. The user confirms download and restart separately.

Validate a signed beta-to-beta update on clean Windows x64, macOS x64, macOS arm64, Linux x64 and
Linux arm64 installations before merging to `pipe`. Confirm the displayed target version, correct
architecture, successful signature/hash validation, explicit download prompt, explicit restart
prompt, preserved local data, and successful relaunch.

## Rollback and incident response

Release binaries are immutable. If a beta is unsafe, immediately convert the affected GitHub
release to a draft (or remove it from public release discovery), record the incident, and publish a
fixed **higher** beta version. Do not overwrite assets and do not force a downgrade. Users who have
already downloaded but not installed can choose **Later**; users who installed receive the fixed
version through the normal confirmed update flow.

Revoke and replace any exposed token, API key, certificate or certificate password. A revoked
Windows or Apple identity requires a new signed build and a higher version. Keep the bad artifact's
hash and provenance record in the incident notes even when public download access is removed.
