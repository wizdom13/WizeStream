#!/usr/bin/env python3
"""Require an app-version bump when a pull request changes the Room schema version."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

MIGRATIONS = "app/src/main/java/org/schabi/newpipe/database/Migrations.kt"
PROJECT_CONFIG = "buildSrc/src/main/kotlin/ProjectConfig.kt"


def read_at(ref: str | None, path: str) -> str:
    if ref is None:
        return Path(path).read_text(encoding="utf-8")
    return subprocess.check_output(
        ["git", "show", f"{ref}:{path}"],
        text=True,
        encoding="utf-8",
    )


def database_version(text: str) -> int:
    match = re.search(r"const val DB_VER_CURRENT = DB_VER_(\d+)", text)
    if not match:
        raise ValueError("Could not read DB_VER_CURRENT")
    return int(match.group(1))


def app_version_code(text: str) -> int:
    values = {}
    for part in ("MAJOR", "MINOR", "PATCH"):
        match = re.search(
            rf"const val WIZESTREAM_VERSION_{part} = (\d+)",
            text,
        )
        if not match:
            raise ValueError(f"Could not read WIZESTREAM_VERSION_{part}")
        values[part] = int(match.group(1))

    return (
        values["MAJOR"] * 1_000_000
        + values["MINOR"] * 1_000
        + values["PATCH"]
    )


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <base-git-ref>", file=sys.stderr)
        return 2

    base_ref = sys.argv[1]
    base_db = database_version(read_at(base_ref, MIGRATIONS))
    current_db = database_version(read_at(None, MIGRATIONS))

    if current_db == base_db:
        return 0

    base_version = app_version_code(read_at(base_ref, PROJECT_CONFIG))
    current_version = app_version_code(read_at(None, PROJECT_CONFIG))

    if current_version <= base_version:
        print(
            "Room database schema changed "
            f"({base_db} -> {current_db}) without increasing the WizeStream "
            f"version code ({base_version} -> {current_version}).",
            file=sys.stderr,
        )
        print(
            "Bump WIZESTREAM_VERSION_MAJOR/MINOR/PATCH in ProjectConfig.kt "
            "in the same PR as the schema migration.",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
