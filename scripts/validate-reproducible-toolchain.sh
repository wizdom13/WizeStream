#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="$ROOT_DIR/gradle/reproducible-build.properties"

property() {
    local key="$1"
    local value
    value="$(sed -n "s/^${key}=//p" "$CONFIG_FILE")"
    if [ -z "$value" ]; then
        echo "Missing ${key} in ${CONFIG_FILE}" >&2
        exit 1
    fi
    printf '%s' "$value"
}

EXPECTED_JAVA_VERSION="$(property jdkVersion)"
EXPECTED_AGP_VERSION="$(property androidGradlePluginVersion)"
ANDROID_PLATFORM="$(property androidPlatform)"
ANDROID_BUILD_TOOLS_VERSION="$(property androidBuildToolsVersion)"
ANDROID_NDK_VERSION="$(property androidNdkVersion)"

ACTUAL_JAVA_VERSION="$(java -XshowSettings:properties -version 2>&1 \
    | sed -n 's/^ *java.version = //p' \
    | head -n 1)"
if [ "$ACTUAL_JAVA_VERSION" != "$EXPECTED_JAVA_VERSION" ]; then
    echo "JDK mismatch: expected ${EXPECTED_JAVA_VERSION}, found ${ACTUAL_JAVA_VERSION:-unknown}" >&2
    exit 1
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_ROOT" ]; then
    echo "ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK" >&2
    exit 1
fi

test -s "$SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar" || {
    echo "Missing Android platform ${ANDROID_PLATFORM} under ${SDK_ROOT}" >&2
    exit 1
}

for tool in aapt apksigner zipalign; do
    test -x "$SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION/$tool" || {
        echo "Missing Build Tools ${ANDROID_BUILD_TOOLS_VERSION} executable: ${tool}" >&2
        exit 1
    }
done

NDK_PROPERTIES="$SDK_ROOT/ndk/$ANDROID_NDK_VERSION/source.properties"
test -s "$NDK_PROPERTIES" || {
    echo "Missing Android NDK ${ANDROID_NDK_VERSION} under ${SDK_ROOT}" >&2
    exit 1
}

ACTUAL_NDK_VERSION="$(sed -n 's/^Pkg.Revision *= *//p' "$NDK_PROPERTIES" | head -n 1)"
if [ "$ACTUAL_NDK_VERSION" != "$ANDROID_NDK_VERSION" ]; then
    echo "NDK mismatch: expected ${ANDROID_NDK_VERSION}, found ${ACTUAL_NDK_VERSION:-unknown}" >&2
    exit 1
fi

ACTUAL_AGP_VERSION="$(sed -n 's/^agp = "\([^"]*\)"/\1/p' "$ROOT_DIR/gradle/libs.versions.toml")"
if [ "$ACTUAL_AGP_VERSION" != "$EXPECTED_AGP_VERSION" ]; then
    echo "AGP mismatch: reproducible config expects ${EXPECTED_AGP_VERSION}, version catalog uses ${ACTUAL_AGP_VERSION:-unknown}" >&2
    exit 1
fi

printf 'Pinned toolchain: JDK %s, %s, Build Tools %s, NDK %s, AGP %s\n' \
    "$ACTUAL_JAVA_VERSION" \
    "$ANDROID_PLATFORM" \
    "$ANDROID_BUILD_TOOLS_VERSION" \
    "$ACTUAL_NDK_VERSION" \
    "$ACTUAL_AGP_VERSION"
