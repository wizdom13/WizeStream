# Building WizeStream

WizeStream is a multi-platform application for Android, Windows, macOS and Linux. It includes the
complete extractor and timeago-parser sources directly in the Android `app` module and reuses the
integrated sources in the Desktop backend. Java sources live under `app/src/main/java`, protocol
definitions under `app/src/main/proto`, and their unit tests under `app/src/test/java`. Every
WizeStream commit and release tag therefore records the application and service extraction logic
together.

## Android requirements

- Git
- JDK 21
- Android SDK with the required platform and build tools
- Accepted Android SDK licenses

## Clone the exact source

```bash
git clone https://github.com/wizdom13/WizeStream.git
cd WizeStream
```

No submodule initialization, separate extractor checkout, or separate extractor build is required.

## Android build entry points

The same committed scripts are used locally and in GitHub Actions.

Build the debug APK and run JVM checks:

```bash
scripts/build.sh debug
```

Build a release APK:

```bash
scripts/build.sh release
```

Run Android instrumented tests:

```bash
scripts/build.sh connected
```

Run style checks:

```bash
scripts/build.sh checkstyle
```

## Versioning and release tags

Stable WizeStream releases use semantic versions in `MAJOR.MINOR.PATCH` form. Update the three
`WIZESTREAM_VERSION_*` components in `buildSrc/src/main/kotlin/ProjectConfig.kt`; do not derive
them from a NewPipe version.

The Android version code is encoded as:

```text
MAJOR × 1,000,000 + MINOR × 1,000 + PATCH
```

Keep `MINOR` and `PATCH` between 0 and 999. Add the release notes at
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, then tag the exact release commit
as `vMAJOR.MINOR.PATCH`. The release workflow rejects a tag that does not match Gradle's
`versionName`.

Desktop preview versions are defined in `desktop/package.json` and use distinct tags ending in
`-unsigned-preview`. Desktop preview assets are immutable and must not reuse an existing tag.

## Release signing

Provide all four WizeStream signing variables before running `scripts/build.sh release`:

```text
WIZESTREAM_RELEASE_STORE_FILE
WIZESTREAM_RELEASE_STORE_PASSWORD
WIZESTREAM_RELEASE_KEY_ALIAS
WIZESTREAM_RELEASE_KEY_PASSWORD
```

The legacy `NEWPIPE_MATERIAL_RELEASE_*` names remain accepted as fallbacks so existing CI secrets and local build environments continue to work during migration.

The resulting APK is written under `app/build/outputs/apk/release/`.

## Desktop builds

Desktop development requires Node.js 24 and JDK 21. From `desktop/`:

```bash
npm ci
npm run dev
```

Run `npm run dist` on the target operating system to create a native package. The complete Desktop
CI matrix builds Windows x64, macOS x64/arm64 and Linux x64/arm64. See
[`desktop/README.md`](desktop/README.md) for architecture and validation details and
[`desktop/docs/releasing.md`](desktop/docs/releasing.md) for the current explicitly unsigned
preview policy.

## Reproducible release rule

Every published WizeStream APK must be built from the exact commit referenced by its release tag.
That commit includes the integrated extractor source under `app/src/main/java`. An APK must not be
replaced with one built from a newer untagged commit; publish a new version and tag instead.

The same immutability rule applies to Desktop preview packages: build every asset from the tagged
source commit, publish checksums and attestations, and use a higher preview version for any changed
binary. Never overwrite an existing preview asset.
