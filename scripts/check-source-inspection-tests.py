#!/usr/bin/env python3
"""Prevent new production-source inspection tests from being added.

Existing source-reading tests are architecture guards that predate this gate. Rather
than trying to reproduce a brittle repository-wide count, compare each current test
file with a base commit and fail if a source-inspecting file gains test methods.
Behavioral tests can still be added in dedicated files without touching this debt.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

TEST_ROOT = Path("app/src/test")
SOURCE_READ_MARKERS = (
    "Files.readString(",
    "Files.readAllLines(",
    "Files.readAllBytes(",
    ".readText(",
    ".readLines(",
)
PRODUCTION_CODE_MARKERS = (
    'Path.of("src/main/java")',
    'Path.of("app/src/main/java")',
    '"src/main/java/',
    '"app/src/main/java/',
)
TEST_ANNOTATION = re.compile(r"(?m)^\s*@Test\b")


def is_source_inspection_test(text: str) -> bool:
    return (
        any(marker in text for marker in SOURCE_READ_MARKERS)
        and any(marker in text for marker in PRODUCTION_CODE_MARKERS)
    )


def test_count(text: str) -> int:
    return len(TEST_ANNOTATION.findall(text))


def read_from_git(ref: str, path: Path) -> str | None:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path.as_posix()}"],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        return None
    return result.stdout


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check-source-inspection-tests.py <base-commit>", file=sys.stderr)
        return 2

    base_ref = sys.argv[1]
    violations: list[str] = []
    inspected_files = 0

    for path in sorted(TEST_ROOT.rglob("*")):
        if path.suffix not in {".java", ".kt"}:
            continue

        current = path.read_text(encoding="utf-8")
        if not is_source_inspection_test(current):
            continue

        inspected_files += 1
        current_count = test_count(current)
        previous = read_from_git(base_ref, path)
        previous_count = (
            test_count(previous)
            if previous is not None and is_source_inspection_test(previous)
            else 0
        )

        if current_count > previous_count:
            violations.append(
                f"{path}: source-inspection tests grew from "
                f"{previous_count} to {current_count}"
            )

    print(f"Existing production-source inspection files checked: {inspected_files}")
    if not violations:
        print("No source-inspection test growth detected.")
        return 0

    print(
        "New source-inspection tests are not allowed; add behavioral coverage instead:",
        file=sys.stderr,
    )
    for violation in violations:
        print(f"  - {violation}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
