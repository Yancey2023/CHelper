"""Run ktfmt while preserving each file's license header byte for byte."""

import argparse
from pathlib import Path
import re
import subprocess
import time

HEADER = re.compile(rb"\A/\*\*.*?\*/\s*", re.DOTALL)


def retry_file_operation(action):
    for attempt in range(5):
        try:
            return action()
        except OSError:
            if attempt == 4:
                raise
            time.sleep(0.05)


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
        failures = []
        for path, header in headers.items():
            try:
                content = retry_file_operation(path.read_bytes)
                match = HEADER.match(content)
                if match is None:
                    raise RuntimeError(f"Formatter removed the license header: {path}")
                restored = header + content[match.end() :]
                retry_file_operation(lambda: path.write_bytes(restored))
            except Exception as error:
                failures.append(f"{path}: {error}")
        if failures:
            raise RuntimeError(
                "Unable to restore license headers:\n" + "\n".join(failures)
            )


if __name__ == "__main__":
    main()
