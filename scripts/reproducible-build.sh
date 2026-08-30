#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERIFY=false
OUTPUT_DIR="$ROOT_DIR/dist/reproducible"
GRADLE_ARGS=()

while [ "$#" -gt 0 ]; do
    case "$1" in
        --verify)
            VERIFY=true
            ;;
        --output-dir)
            if [ "$#" -lt 2 ]; then
                echo "--output-dir requires a directory" >&2
                exit 2
            fi
            OUTPUT_DIR="$2"
            shift
            ;;
        --)
            shift
            GRADLE_ARGS+=("$@")
            break
            ;;
        -h|--help)
            echo "Usage: $0 [--verify] [--output-dir DIR] [-- extra Gradle arguments]"
            exit 0
            ;;
        *)
            GRADLE_ARGS+=("$1")
            ;;
    esac
    shift
done

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-XX:ActiveProcessorCount=1"

SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"
export SOURCE_DATE_EPOCH
export TZ=UTC
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

build_once() {
    local destination="$1"
    local apk_paths=()

    ./gradlew \
        --no-daemon \
        --max-workers=1 \
        --no-build-cache \
        --no-configuration-cache \
        clean assembleRelease \
        --stacktrace \
        -DskipFormatKtlint \
        "${GRADLE_ARGS[@]}"

    mapfile -t apk_paths < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | sort)
    if [ "${#apk_paths[@]}" -ne 1 ]; then
        echo "Expected exactly one release APK, found ${#apk_paths[@]}" >&2
        exit 1
    fi
    cp "${apk_paths[0]}" "$destination"
}

mkdir -p "$OUTPUT_DIR"

if [ "$VERIFY" = true ]; then
    build_once "$TMP_DIR/first.apk"
    build_once "$TMP_DIR/second.apk"

    FIRST_SHA256="$(sha256sum "$TMP_DIR/first.apk" | cut -d ' ' -f 1)"
    SECOND_SHA256="$(sha256sum "$TMP_DIR/second.apk" | cut -d ' ' -f 1)"

    if [ "$FIRST_SHA256" != "$SECOND_SHA256" ]; then
        cp "$TMP_DIR/first.apk" "$OUTPUT_DIR/first.apk"
        cp "$TMP_DIR/second.apk" "$OUTPUT_DIR/second.apk"
        printf 'Non-reproducible release APKs:\n  first  %s\n  second %s\n' \
            "$FIRST_SHA256" "$SECOND_SHA256" >&2
        exit 1
    fi

    cp "$TMP_DIR/second.apk" "$OUTPUT_DIR/release.apk"
    printf '%s  release.apk\n' "$SECOND_SHA256" > "$OUTPUT_DIR/SHA256SUMS"
    printf 'Reproducible release APK: %s\n' "$SECOND_SHA256"
else
    build_once "$OUTPUT_DIR/release.apk"
    sha256sum "$OUTPUT_DIR/release.apk" | sed 's# .*/#  #' > "$OUTPUT_DIR/SHA256SUMS"
    cat "$OUTPUT_DIR/SHA256SUMS"
fi
