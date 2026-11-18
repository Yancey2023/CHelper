"""Regression checks for Kotlin import-style detection; no workspace files are modified."""

import unittest

from check_compose_architecture import qualified_references


class KotlinImportStyleTest(unittest.TestCase):
    def references(self, source):
        return [name for _, _, name in qualified_references(source)]

    def test_types_annotations_functions_and_nested_members(self):
        self.assertEqual(
            self.references(
                "@androidx.compose.runtime.Immutable\n"
                "val file: java.io.File = java.io.File(path)\n"
                "kotlinx.coroutines.withContext(Dispatchers.IO) {}\n"
                "android.provider.Settings.Secure.getString(resolver, key)"
            ),
            [
                "androidx.compose.runtime.Immutable",
                "java.io.File",
                "java.io.File",
                "kotlinx.coroutines.withContext",
                "android.provider.Settings",
            ],
        )

    def test_package_imports_comments_and_literals_are_not_code(self):
        self.assertEqual(
            self.references(
                "package yancey.chelper.ui\n"
                "import java.io.File as LocalFile\n"
                "// java.io.File(path)\n"
                "/* org.junit.Test /* java.io.File */ java.util.Locale */\n"
                'val name = "yancey.chelper.desktop.MainKt"\n'
                'val raw = """androidx.compose.runtime.Immutable"""\n'
                'val escaped = "\\${java.io.File(path)}"\n'
            ),
            [],
        )

    def test_code_in_templates_is_checked_and_literal_text_is_preserved(self):
        self.assertEqual(
            self.references(
                'val text = "java.io.File: ${java.io.File(path).name}"\n'
                'val nested = "${"${java.util.Locale.ROOT}"}"\n'
                'val raw = """${kotlin.math.max(a, b)}"""'
            ),
            ["java.io.File", "java.util.Locale", "kotlin.math.max"],
        )

    def test_member_access_and_gradle_accessors_are_preserved(self):
        self.assertEqual(
            self.references(
                'kotlin.srcDir("src/jvmSharedMain/kotlin")\n'
                'kotlin.directories.add("src/debug/kotlin")\n'
                "implementation(libs.kotlinx.coroutines.swing)\n"
                "owner.navigationEventDispatcher.addInput(input)\n"
                "Res.drawable.some_icon"
            ),
            [],
        )

    def test_multiline_qualified_name_has_original_offsets(self):
        source = "// prefix\nval result: java.io.\n  File = input"
        [(start, end, name)] = qualified_references(source)
        self.assertEqual(name, "java.io.File")
        self.assertEqual(source[start:end], "java.io.\n  File")

    def test_short_names_and_import_aliases_need_no_cleanup(self):
        self.assertEqual(
            self.references(
                "import yancey.chelper.ui.designsystem.text.highlightCommand as renderCommand\n"
                "val file = File(path)\n"
                "Settings.Secure.getString(resolver, key)\n"
                "renderCommand(command)"
            ),
            [],
        )


if __name__ == "__main__":
    unittest.main()
