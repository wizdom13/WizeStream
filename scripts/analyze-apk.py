#!/usr/bin/env python3
"""Report APK ZIP payload sizes; these are not installed-size or runtime measurements."""
import argparse
import hashlib
import json
import zipfile
from collections import defaultdict
from pathlib import Path


def analyze(path):
    groups = defaultdict(lambda: {"compressed_bytes": 0, "uncompressed_bytes": 0, "entries": 0})
    with zipfile.ZipFile(path) as apk:
        entries = [entry for entry in apk.infolist() if not entry.is_dir()]
        for entry in entries:
            name = entry.filename
            group = "dex" if name.endswith(".dex") else name.split("/", 1)[0]
            if name.startswith("lib/"):
                group = "/".join(name.split("/")[:2])
            groups[group]["compressed_bytes"] += entry.compress_size
            groups[group]["uncompressed_bytes"] += entry.file_size
            groups[group]["entries"] += 1
        largest = sorted(entries, key=lambda entry: entry.compress_size, reverse=True)[:20]
        return {
            "apk": path.name,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "apk_bytes": path.stat().st_size,
            "zip_payload_uncompressed_bytes": sum(entry.file_size for entry in entries),
            "groups": dict(sorted(groups.items())),
            "largest_entries": [
                {"path": entry.filename, "compressed_bytes": entry.compress_size, "uncompressed_bytes": entry.file_size}
                for entry in largest
            ],
            "measurement": "APK file and ZIP payload only; excludes Android installation overhead, app data, caches, RAM, and runtime performance.",
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(analyze(args.apk), indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")


if __name__ == "__main__":
    main()
