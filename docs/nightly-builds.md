# Nightly builds

The `Nightly release` workflow builds the latest `pipe` commit every day at 02:00 UTC and can also be started manually from GitHub Actions.

Successful builds are published as prereleases in:

- https://github.com/wizdom13/NewPipe_Material_Nightly/releases

The workflow skips publishing when the checked-out `pipe` commit already has a nightly release.

## Nightly identity

- Application ID: `org.wisso.newpipematerial.nightly`
- Application label: `NewPipe Material Nightly`
- Build type: optimized, non-debuggable, inherited from `release`
- Signing key: the same release signing key used for stable NewPipe Material builds

The nightly application ID differs from the stable application ID, so both applications can be installed at the same time even though they use the same signing key.

## Required source-repository secrets

Add these secrets to `wizdom13/NewPipe_Material`:

- `NIGHTLY_REPO_TOKEN`: fine-grained token with `Contents: Read and write` access to `wizdom13/NewPipe_Material_Nightly`
- `NEWPIPE_MATERIAL_RELEASE_KEYSTORE_BASE64`: Base64-encoded release keystore
- `NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD`
- `NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS`
- `NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD`

For compatibility with existing setups, the workflow also accepts these fallback secret names:

- Keystore: `RELEASE_KEYSTORE_BASE64` or `SIGNING_KEY`
- Store password: `KEYSTORE_PASSWORD`
- Alias: `KEY_ALIAS`
- Key password: `KEY_PASSWORD`

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
0.28.8-m11-nightly.20260714.dc108a3
nightly-20260714-dc108a3
```

## Retention

The workflow keeps the newest 14 nightly prereleases and deletes older releases and their tags. The duplicate GitHub Actions artifact is retained for 14 days.
