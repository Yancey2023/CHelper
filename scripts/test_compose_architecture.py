"""Regression tests for the Compose module and namespace boundary checker."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_compose_architecture as architecture


class ArchitectureChecksTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="chelper-architecture-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.patch = patch.object(architecture, "ROOT", self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        for module in architecture.DEPENDENCIES:
            directory = self.root / module
            directory.mkdir(parents=True)
            (directory / "build.gradle.kts").write_text("plugins {}\n")

    def source(self, module, package, name="Example", body="", directory=None):
        directory = directory or package.replace(".", "/")
        path = self.root / module / "src/commonMain/kotlin" / directory / f"{name}.kt"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"package {package}\n\n{body}\nclass {name}\n")

    def assert_rejected(self, fragment):
        failures = architecture.check_architecture()
        self.assertTrue(any(fragment in failure for failure in failures), failures)

    def test_accepts_owned_packages_and_existing_jni_abi(self):
        self.source("shared/domain", "yancey.chelper.domain.library")
        self.source("shared/native", "yancey.chelper.core", "CHelperCore")
        self.assertEqual([], architecture.check_architecture())

    def test_rejects_packages_owned_by_another_module(self):
        self.source("shared/data", "yancey.chelper.ui.library")
        self.assert_rejected("namespace belongs to another module")

    def test_namespace_owner_must_match_a_complete_package_segment(self):
        self.source("shared/domain", "yancey.chelper.domainextra")
        self.assert_rejected("namespace belongs to another module")

    def test_rejects_package_and_directory_mismatch(self):
        self.source(
            "shared/domain", "yancey.chelper.domain.library", directory="misplaced"
        )
        self.assert_rejected("path differs from package")

    def test_rejects_fully_qualified_host_references_without_imports(self):
        self.source(
            "shared/ui",
            "yancey.chelper.ui.feature.home",
            body="val integration = yancey.chelper.android.window.Manager",
        )
        self.assert_rejected("forbidden qualified reference")

    def test_rejects_reverse_module_dependencies(self):
        path = self.root / "shared/domain/build.gradle.kts"
        path.write_text('dependencies { implementation(project(":apps:desktop")) }')
        self.assert_rejected("forbidden dependencies")

    def test_rejects_datastore_construction_in_hosts(self):
        self.source(
            "apps/android",
            "yancey.chelper.android.storage",
            body="val store = DataStoreFactory.create(serializer)",
        )
        self.assert_rejected("construct stores through ClientDataStores")

    def test_rejects_new_business_classes_in_jni_namespace(self):
        self.source("shared/native", "yancey.chelper.core", "FeatureRepository")
        self.assert_rejected("only ABI types belong to the JNI package")


if __name__ == "__main__":
    unittest.main()
