# Building WizeStream

WizeStream includes the complete extractor and timeago-parser sources directly in the `app`
module. Java sources live under `app/src/main/java`, protocol definitions under
`app/src/main/proto`, and their unit tests under `app/src/test/java`. Every WizeStream commit and
release tag therefore records and builds the application and service extraction logic together.

## Requirements

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

## Shared build entry points

The same committed scripts are used locally and in GitHub Actions.

Build the debug APK and run JVM checks:

```bash
scripts/build.sh debug
```

Build the default ARM64/ARMv7 release APK:

```bash
scripts/build.sh release
```

Build the x86_64 release APK for Waydroid and Android-x86:

```bash
scripts/build.sh release -PreleaseAbi=x86_64
```

The `releaseAbi` property accepts `arm` (the default) or `x86_64`. Published releases build
both targets from the same tagged source and sign them with the same release key.

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

## Release signing

Provide all four WizeStream signing variables before running `scripts/build.sh release`:

```text
WIZESTREAM_RELEASE_STORE_FILE
WIZESTREAM_RELEASE_STORE_PASSWORD
WIZESTREAM_RELEASE_KEY_ALIAS
WIZESTREAM_RELEASE_KEY_PASSWORD
```

The legacy `NEWPIPE_MATERIAL_RELEASE_*` names remain accepted as fallbacks so existing CI secrets and local build environments continue to work during migration.

The resulting APK is written under `app/build/outputs/apk/release/`. The release workflow
publishes the existing `wizestream_vX.Y.Z.apk` ARM asset and a separate
`wizestream_vX.Y.Z_x86_64.apk` asset so ARM downloads do not increase in size.

## Reproducible release rule

Every published WizeStream APK must be built from the exact commit referenced by its release tag.
That commit includes the integrated extractor source under `app/src/main/java`. An APK must not be
replaced with one built from a newer untagged commit; publish a new version and tag instead.
