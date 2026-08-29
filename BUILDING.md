# Building WizeStream

WizeStream includes the complete extractor and timeago-parser sources directly in the `app`
module. Java sources live under `app/src/main/java`, protocol definitions under
`app/src/main/proto`, and their unit tests under `app/src/test/java`. Every WizeStream commit and
release tag therefore records and builds the application and service extraction logic together.

## Requirements

- Git
- Eclipse Temurin JDK 21.0.12+8
- Android SDK platform 36.1 and Build Tools 36.1.0
- Android NDK 28.2.13676358
- Accepted Android SDK licenses

The exact toolchain is recorded in `gradle/reproducible-build.properties`. Install the pinned
Android components with:

```bash
sdkmanager "platforms;android-36.1" "build-tools;36.1.0" "ndk;28.2.13676358"
```

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

Build it twice from clean state and require identical SHA-256 hashes:

```bash
scripts/reproducible-build.sh --verify
```

Build the x86_64 release APK for Waydroid and Android-x86:

```bash
scripts/build.sh release -PreleaseAbi=x86_64
```

The `releaseAbi` property accepts `arm` (the default) or `x86_64`. Published releases build
both targets from the same tagged source and sign them with the same release key.

The reproducible entry point runs the complete release pipeline with one visible processor and one
Gradle worker. `JAVA_TOOL_OPTIONS` propagates the processor limit to Gradle, D8/L8, R8, and child
JVMs, while the existing R8 execution profile provides an additional dedicated-process safeguard.
Normal debug and test builds keep their parallelism. Build and configuration caches are disabled,
the locale and timezone are fixed, and `SOURCE_DATE_EPOCH` comes from the checked-out commit. A
verified APK and its `SHA256SUMS` file are written to `dist/reproducible/`.

Independent rebuilders must use `scripts/reproducible-build.sh` rather than invoking
`./gradlew assembleRelease` directly. The script validates the pinned toolchain and fails before
assembly if the Gradle JVM does not observe exactly one active processor.

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

Pull-request CI builds the release APK twice and fails when the SHA-256 hashes differ. The release
workflow applies the same check to both the signed ARM and x86_64 APKs before either asset can be
published.
