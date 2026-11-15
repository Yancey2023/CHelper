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
        catalog = self.root / "gradle/libs.versions.toml"
        catalog.parent.mkdir()
        catalog.write_text(
            '[versions]\ntoaster = "15.2"\n\n[libraries]\n'
            'toaster = { module = "com.github.getActivity:Toaster", version.ref = "toaster" }\n',
            encoding="utf-8",
        )
        for module in architecture.DEPENDENCIES:
            directory = self.root / module
            directory.mkdir(parents=True)
            build = "plugins {}\n"
            if module == "apps/android":
                build += "dependencies { implementation(libs.toaster) }\n"
            (directory / "build.gradle.kts").write_text(build, encoding="utf-8")

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
        self.source("platform/jvm", "yancey.chelper.core", "CHelperCore")
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
        self.source("platform/jvm", "yancey.chelper.core", "FeatureRepository")
        self.assert_rejected("only ABI types belong to the JNI package")

    def test_rejects_material_dependency_hidden_behind_an_alias(self):
        catalog = self.root / "gradle/libs.versions.toml"
        with catalog.open("a", encoding="utf-8") as target:
            target.write(
                'widgets = { module = "org.jetbrains.compose.material3:material3", version = "1" }\n'
            )
        self.assert_rejected("Material / Material 3 dependencies are forbidden")

    def test_accepts_explicit_exclusion_of_desktop_material(self):
        build = self.root / "apps/desktop/build.gradle.kts"
        build.write_text(
            'configurations.configureEach { exclude(group = "org.jetbrains.compose.material") }\n',
            encoding="utf-8",
        )
        self.assertEqual([], architecture.check_architecture())

    def test_rejects_removing_android_toaster(self):
        build = self.root / "apps/android/build.gradle.kts"
        build.write_text("plugins {}\n", encoding="utf-8")
        self.assert_rejected("retain Toaster")

    def test_rejects_java_in_common_tests(self):
        self.source("shared/data", "yancey.chelper.data", body="import java.io.File")
        original = self.root / "shared/data/src/commonMain"
        original.rename(original.with_name("commonTest"))
        self.assert_rejected("JVM dependency in common code")

    def test_rejects_jvm_shared_directory(self):
        (self.root / "shared/data/src/jvmSharedTest").mkdir(parents=True)
        self.assert_rejected("obsolete jvmSharedTest")

    def test_rejects_native_implementation_dependency_from_ui(self):
        (self.root / "shared/ui/build.gradle.kts").write_text(
            'dependencies { implementation(project(":platform:jvm")) }'
        )
        self.assert_rejected("forbidden dependencies")

    def test_rejects_bypassing_common_with_target_specific_shared_sources(self):
        self.source("shared/data", "yancey.chelper.data", body="import java.io.File")
        original = self.root / "shared/data/src/commonMain"
        original.rename(original.with_name("jvmMain"))
        self.assert_rejected("shared Kotlin belongs to commonMain/commonTest")

    def test_platform_adapter_cannot_import_data_implementation(self):
        self.source(
            "platform/jvm",
            "yancey.chelper.nativecore",
            body="import yancey.chelper.data.local.ClientDataStores",
        )
        self.assert_rejected("forbidden import")

    def test_network_runtime_cannot_be_reintroduced_as_shared_global_state(self):
        self.source(
            "shared/data", "yancey.chelper.data.remote", body="object ServiceManager {}"
        )
        self.assert_rejected("application-owned network services")


if __name__ == "__main__":
    unittest.main()
