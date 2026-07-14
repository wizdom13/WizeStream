# Building NewPipe Material

NewPipe Material uses a pinned `PipePipeExtractor` Git submodule at `external/NewPipeExtractor`. The submodule commit recorded by each NewPipe Material commit or tag is part of the source definition and must not be replaced with the latest extractor branch tip.

## Requirements

- Git with submodule support
- JDK 21
- Android SDK with the required platform and build tools
- Accepted Android SDK licenses

## Clone the exact source

```bash
git clone --recurse-submodules https://github.com/wizdom13/NewPipe_Material.git
cd NewPipe_Material
```

For an existing checkout or after switching tags:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

Do not use `git submodule update --remote` for release or reproducible builds. It moves the extractor away from the commit pinned by the app repository.

## Shared build entry points

The same committed scripts are used locally and in GitHub Actions.

Prepare and verify the pinned extractor checkout:

```bash
scripts/prepare-extractor.sh
```

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

## Release signing

Provide all four signing variables before running `scripts/build.sh release`:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

The resulting APK is written under `app/build/outputs/apk/release/`.

## Reproducible release rule

Every published APK must be built from the exact commit referenced by its release tag, including the submodule commit recorded by that tag. An APK must not be replaced with one built from a newer untagged commit; publish a new version and tag instead.
