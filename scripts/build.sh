#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

"$ROOT_DIR/scripts/prepare-extractor.sh"

MODE="${1:-debug}"
shift || true

case "$MODE" in
    debug)
        exec ./gradlew clean assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint "$@"
        ;;
    release)
        exec ./gradlew clean assembleRelease --stacktrace -DskipFormatKtlint "$@"
        ;;
    nightly)
        exec ./gradlew \
            --init-script "$ROOT_DIR/scripts/nightly.init.gradle" \
            clean assembleNightly \
            --stacktrace \
            -DskipFormatKtlint \
            "$@"
        ;;
    checkstyle)
        exec ./gradlew runCheckstyle --stacktrace -DskipFormatKtlint "$@"
        ;;
    connected)
        exec ./gradlew connectedCheck --stacktrace "$@"
        ;;
    extractor-bytecode)
        ./gradlew -p external/NewPipeExtractor :extractor:clean :extractor:compileJava --stacktrace "$@"
        exec javap -verbose -classpath external/NewPipeExtractor/extractor/build/classes/java/main org.schabi.newpipe.extractor.NewPipe
        ;;
    *)
        echo "Usage: $0 {debug|release|nightly|checkstyle|connected|extractor-bytecode} [extra Gradle arguments]" >&2
        exit 2
        ;;
esac
