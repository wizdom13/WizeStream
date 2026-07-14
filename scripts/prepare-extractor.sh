#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXTRACTOR_DIR="$ROOT_DIR/external/NewPipeExtractor"

cd "$ROOT_DIR"

git submodule sync --recursive
git submodule update --init --recursive

if [ ! -d "$EXTRACTOR_DIR/.git" ] && [ ! -f "$EXTRACTOR_DIR/.git" ]; then
    echo "PipePipeExtractor submodule checkout is missing at external/NewPipeExtractor" >&2
    exit 1
fi

EXPECTED_COMMIT="$(git ls-tree HEAD external/NewPipeExtractor | awk '{print $3}')"
ACTUAL_COMMIT="$(git -C "$EXTRACTOR_DIR" rev-parse HEAD)"

if [ -z "$EXPECTED_COMMIT" ] || [ "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]; then
    echo "PipePipeExtractor submodule mismatch: expected $EXPECTED_COMMIT, got $ACTUAL_COMMIT" >&2
    exit 1
fi

test -d "$EXTRACTOR_DIR/extractor"
grep -q "JavaLanguageVersion.of(21)" "$EXTRACTOR_DIR/build.gradle"
grep -q "options.release = 21" "$EXTRACTOR_DIR/build.gradle"

rm -rf \
    "$EXTRACTOR_DIR/build" \
    "$EXTRACTOR_DIR/extractor/build" \
    "$EXTRACTOR_DIR/timeago-parser/build"

echo "Using PipePipeExtractor commit $ACTUAL_COMMIT"
