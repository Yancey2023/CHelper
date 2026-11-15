"""Regression coverage for assets that compile but disappear from the Android APK."""

from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from check_android_resources import ASSET_PREFIX, check_apk


class AndroidResourceChecksTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="chelper-resources-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.prepared = self.root / "prepared"
        self.icon = self.prepared / "drawable/pack_icon.webp"
        self.icon.parent.mkdir(parents=True)
        self.icon.write_bytes(b"image fixture")
        self.apk = self.root / "app.apk"

    def test_accepts_complete_compose_assets(self):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr(
                ASSET_PREFIX + "drawable/pack_icon.webp", self.icon.read_bytes()
            )
        self.assertEqual([], check_apk(self.apk, self.prepared))

    def test_native_drawable_does_not_replace_compose_asset(self):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr("res/drawable/pack_icon.webp", self.icon.read_bytes())
        failures = check_apk(self.apk, self.prepared)
        self.assertTrue(
            any("missing Compose resource" in failure for failure in failures)
        )

    def test_detects_outdated_asset_bytes(self):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr(ASSET_PREFIX + "drawable/pack_icon.webp", b"old image")
        failures = check_apk(self.apk, self.prepared)
        self.assertTrue(
            any("incorrect Compose resource content" in failure for failure in failures)
        )

    def test_requires_generated_resources_before_verification(self):
        self.assertTrue(check_apk(self.apk, self.root / "missing"))


if __name__ == "__main__":
    unittest.main()
