# Nightly builds

WizeStream publishes automated Android and Desktop testing builds from the latest `pipe` commit.
Both are available as prereleases at:

- https://github.com/wizdom13/WizeStream_Nightly/releases

The `Nightly release` workflow checks Android at 02:00 UTC. The `Desktop Nightly release` workflow
checks Desktop at 03:00 UTC. Both can also be started manually from GitHub Actions, and each skips
publishing if its platform has already published the checked-out commit.

## Android nightly identity

- Application ID: `org.wisso.newpipematerial.nightly`
- Application label: `WizeStream Nightly`
- Build type: optimized, non-debuggable, inherited from `release`
- Signing key: the same release signing key used for stable WizeStream builds

The nightly application ID differs from the stable application ID, so both applications can be
installed at the same time even though they use the same signing key.

## Desktop nightly packages

Desktop nightlies are explicitly unsigned packages for:

- Windows x64 installer and portable executable
- macOS Intel and Apple Silicon DMG/ZIP
- Linux x64 and arm64 AppImage/DEB

Windows and macOS may show unsigned-application warnings. Production automatic updates are
disabled, so install a newer nightly manually. Each release includes `SHA256SUMS`, a release
manifest and GitHub artifact attestations for verification.

## Required source-repository secrets

Add these secrets to `wizdom13/WizeStream`:

- `NIGHTLY_REPO_TOKEN`: fine-grained token with `Contents: Read and write` access to `wizdom13/WizeStream_Nightly`

The Android nightly additionally requires:

- `WIZESTREAM_RELEASE_KEYSTORE_BASE64`: Base64-encoded release keystore
- `WIZESTREAM_RELEASE_STORE_PASSWORD`
- `WIZESTREAM_RELEASE_KEY_ALIAS`
- `WIZESTREAM_RELEASE_KEY_PASSWORD`

For compatibility with existing setups, the workflow also accepts these fallback secret names:

- Keystore: `NEWPIPE_MATERIAL_RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_BASE64`, or `SIGNING_KEY`
- Store password: `NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD` or `KEYSTORE_PASSWORD`
- Alias: `NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS` or `KEY_ALIAS`
- Key password: `NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD` or `KEY_PASSWORD`

To encode the existing keystore on Linux:

```bash
base64 -w 0 path/to/release.keystore
```

On PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\release.keystore"))
```

Desktop nightlies do not use Windows or Apple signing credentials.

## Versioning

Each published nightly receives:

- version code: `YYDDDHHMM` in UTC
- version suffix: `-nightly.YYYYMMDD.<short-source-sha>`
- tag: `nightly-YYYYMMDD-<short-source-sha>`

For example:

```text
1.0.0-nightly.20260714.dc108a3
nightly-20260714-dc108a3
```

Each Desktop nightly uses a unique package version and a separate tag so that it cannot collide
with Android. For example:

```text
0.6.0-nightly.20260714.dc108a3
desktop-nightly-20260714-dc108a3
```

## Retention

The workflows independently keep the newest 14 Android nightlies and the newest 14 Desktop
nightlies. Older releases and tags for the same platform are deleted. Duplicate GitHub Actions
artifacts are retained for 14 days.
