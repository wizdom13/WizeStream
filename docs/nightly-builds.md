# Nightly builds

The `Nightly release` workflow builds the latest `pipe` commit every day at 02:00 UTC and can also be started manually from GitHub Actions.

Successful builds are published as prereleases in:

- https://github.com/wizdom13/WizeStream_Nightly/releases

Every nightly publishes separate ARM64/ARMv7 and x86_64 APKs with matching SHA-256 checksum
files. Use the filename ending in `-x86_64.apk` for Waydroid or another 64-bit Intel or AMD
Android environment; use the standard filename on ARM devices.

The workflow skips publishing when the checked-out `pipe` commit already has a nightly release.

## Nightly identity

- Application ID: `org.wisso.newpipematerial.nightly`
- Application label: `WizeStream Nightly`
- Build type: optimized, non-debuggable, inherited from `release`
- Signing key: the same release signing key used for stable WizeStream builds

The nightly application ID differs from the stable application ID, so both applications can be installed at the same time even though they use the same signing key.

## Required source-repository secrets

Add these secrets to `wizdom13/WizeStream`:

- `NIGHTLY_REPO_TOKEN`: fine-grained token with `Contents: Read and write` access to `wizdom13/WizeStream_Nightly`
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

## Retention

The workflow keeps the newest 14 nightly prereleases and deletes older releases and their tags. The duplicate GitHub Actions artifact is retained for 14 days.
