"""Reject reflowed CHelper license headers without changing source files."""

from pathlib import Path
import re


def check_headers(root: Path) -> list[Path]:
    invalid = []
    for path in root.rglob("*.kt"):
        content = path.read_text(encoding="utf-8")
        header = re.match(r"\A/\*\*.*?\*/", content, re.DOTALL)
        if header is None or "It is part of CHelper." not in header[0]:
            continue
        if not re.search(r"^ \* Copyright \(C\) \d{4}[^\n]*$", header[0], re.MULTILINE):
            invalid.append(path)
        elif "License along with this program. If" in header[0]:
            invalid.append(path)
    return invalid


if __name__ == "__main__":
    source = Path(__file__).resolve().parents[1] / "CHelper-Android/app/src"
    failures = check_headers(source)
    for failure in failures:
        print(f"License header was reflowed: {failure}")
    raise SystemExit(bool(failures))
