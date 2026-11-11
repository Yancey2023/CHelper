"""Run ktfmt while preserving each file's license header byte for byte."""

import argparse
from pathlib import Path
import re
import subprocess

HEADER = re.compile(rb"\A/\*\*.*?\*/\s*", re.DOTALL)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--formatter", required=True, type=Path)
    parser.add_argument("files", nargs="+", type=Path)
    args = parser.parse_args()
    headers = {}
    for path in args.files:
        match = HEADER.match(path.read_bytes())
        if match and b"It is part of CHelper." in match[0]:
            headers[path] = match[0]
    try:
        subprocess.run(
            [
                "java",
                "-jar",
                str(args.formatter),
                "--kotlinlang-style",
                *map(str, args.files),
            ],
            check=True,
        )
    finally:
        for path, header in headers.items():
            content = path.read_bytes()
            match = HEADER.match(content)
            if match is None:
                raise RuntimeError(f"Formatter removed the license header: {path}")
            path.write_bytes(header + content[match.end() :])


if __name__ == "__main__":
    main()
