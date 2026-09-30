#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

collect_previews() {
    adb pull /sdcard/Download/wizestream-player-layout-previews \
        "${RUNNER_TEMP:-$ROOT_DIR/app/build/outputs}/player-layout-previews" || true
}

# Keep the test command's exit status while collecting previews on both success and failure.
trap collect_previews EXIT
"$ROOT_DIR/scripts/build.sh" connected "$@"
