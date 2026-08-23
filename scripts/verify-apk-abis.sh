#!/usr/bin/env bash
set -euo pipefail

if (( $# < 2 )); then
    echo "Usage: $0 <apk> <expected-abi> [expected-abi ...]" >&2
    exit 2
fi

apk_path="$1"
shift

test -s "$apk_path"

actual_file="$(mktemp)"
expected_file="$(mktemp)"
trap 'rm -f "$actual_file" "$expected_file"' EXIT

unzip -Z1 "$apk_path" \
    | sed -n 's#^lib/\([^/]*\)/[^/][^/]*\.so$#\1#p' \
    | sort -u > "$actual_file"
printf '%s\n' "$@" | sort -u > "$expected_file"

if [[ ! -s "$actual_file" ]]; then
    echo "No native libraries were found in $apk_path; the APK is architecture-independent."
    exit 0
fi

if ! diff -u "$expected_file" "$actual_file"; then
    echo "APK native-library ABIs do not match the expected release target." >&2
    exit 1
fi

echo "Verified APK ABIs: $(paste -sd, "$actual_file")"
