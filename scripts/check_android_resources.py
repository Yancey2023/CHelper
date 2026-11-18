"""Check that Android APKs contain the Compose resources produced by shared:ui."""

import argparse
from pathlib import Path
from zipfile import BadZipFile, ZipFile

PROJECT = Path(__file__).resolve().parents[1]
PREPARED = (
    PROJECT
    / "CHelper-Compose/shared/ui/build/generated/compose/resourceGenerator"
    / "preparedResources/commonMain/composeResources"
)
ASSET_PREFIX = "assets/composeResources/yancey.chelper.resources/"


def check_apk(apk: Path, prepared: Path = PREPARED) -> list[str]:
    expected = sorted(path for path in prepared.rglob("*") if path.is_file())
    if not expected:
        return [f"{prepared}: no prepared Compose resources; build shared:ui first"]
    failures = []
    try:
        with ZipFile(apk) as archive:
            names = set(archive.namelist())
            for path in expected:
                name = ASSET_PREFIX + path.relative_to(prepared).as_posix()
                if name not in names:
                    failures.append(f"{apk}: missing Compose resource {name}")
                elif archive.read(name) != path.read_bytes():
                    failures.append(f"{apk}: incorrect Compose resource content {name}")
    except (OSError, BadZipFile) as error:
        failures.append(f"{apk}: cannot read APK: {error}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", type=Path, nargs="+")
    args = parser.parse_args()
    failures = [failure for apk in args.apks for failure in check_apk(apk)]
    for failure in failures:
        print(failure)
    if not failures:
        print(f"Compose resource contents verified in {len(args.apks)} APK(s)")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
