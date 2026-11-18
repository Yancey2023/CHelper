"""Format maintained Compose sources and Gradle conventions, preserving license headers."""

import argparse
from pathlib import Path
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[1]
COMPOSE = PROJECT / "CHelper-Compose"


def source_files() -> list[Path]:
    files = set(COMPOSE.glob("*.gradle.kts"))
    for group in ("apps", "shared", "platform"):
        for module in (COMPOSE / group).iterdir():
            files.update(module.glob("*.gradle.kts"))
            files.update((module / "src").rglob("*.kt"))
    conventions = COMPOSE / "build-logic"
    files.update(conventions.glob("*.gradle.kts"))
    files.update((conventions / "src").rglob("*.gradle.kts"))
    return sorted(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--formatter", type=Path, default=PROJECT / "build/format-tools/ktfmt.jar"
    )
    args = parser.parse_args()
    files = source_files()
    for offset in range(0, len(files), 20):
        subprocess.run(
            [
                sys.executable,
                str(PROJECT / "scripts/format_android.py"),
                "--formatter",
                str(args.formatter.resolve()),
                *map(str, files[offset : offset + 20]),
            ],
            check=True,
        )
    print(f"Formatted {len(files)} Kotlin and Gradle files")


if __name__ == "__main__":
    main()
