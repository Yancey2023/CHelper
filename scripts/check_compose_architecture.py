"""Check Compose module direction, source ownership, JNI boundaries and shared resources."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1] / "CHelper-Compose"
DEPENDENCIES = {
    "shared/domain": set(),
    "shared/data": {"shared/domain"},
    "shared/native": {"shared/domain"},
    "shared/ui": {"shared/domain", "shared/data", "shared/native"},
    "apps/android": {"shared/domain", "shared/data", "shared/native", "shared/ui"},
    "apps/desktop": {"shared/domain", "shared/data", "shared/native", "shared/ui"},
}
OWNERS = {
    "shared/domain": "yancey.chelper.domain",
    "shared/data": "yancey.chelper.data",
    "shared/native": "yancey.chelper.nativecore",
    "shared/ui": "yancey.chelper.ui",
    "apps/android": "yancey.chelper.android",
    "apps/desktop": "yancey.chelper.desktop",
}
JNI_TYPES = {
    "CHelperCore",
    "CommandContext",
    "ClickSuggestionResult",
    "ErrorReason",
    "SelectedString",
    "Suggestion",
}

PACKAGE_ROOTS = (
    "android",
    "androidx",
    "kotlin",
    "kotlinx",
    "java",
    "javax",
    "org",
    "com",
    "yancey",
    "okhttp3",
    "okio",
    "coil3",
    "retrofit2",
    "io",
    "net",
)
KOTLIN_PACKAGES = {
    "annotation",
    "collections",
    "comparisons",
    "concurrent",
    "contracts",
    "coroutines",
    "enums",
    "experimental",
    "io",
    "jvm",
    "math",
    "random",
    "ranges",
    "reflect",
    "sequences",
    "system",
    "text",
    "time",
    "uuid",
}
KOTLIN_FUNCTIONS = {
    "run",
    "with",
    "check",
    "require",
    "error",
    "runCatching",
    "repeat",
    "synchronized",
    "lazy",
}
QUALIFIED_REFERENCE = re.compile(
    r"(?<![\w$.])(?:" + "|".join(PACKAGE_ROOTS) + r")\s*\.\s*"
    r"[A-Za-z_]\w*(?:\s*\.\s*[A-Za-z_]\w*)*"
)


def kotlin_code(source: str) -> str:
    """Mask comments and literals, retaining offsets and code inside string templates."""
    masked = list(source)
    length = len(source)

    def hide(start: int, end: int) -> None:
        for offset in range(start, end):
            if source[offset] not in "\r\n":
                masked[offset] = " "

    def string(start: int) -> int:
        quote = '"""' if source.startswith('"""', start) else source[start]
        index = start + len(quote)
        hide(start, index)
        while index < length:
            if source.startswith(quote, index):
                hide(index, index + len(quote))
                return index + len(quote)
            if quote != '"""' and source[index] == "\\":
                hide(index, min(index + 2, length))
                index += 2
            elif quote != "'" and source.startswith("${", index):
                hide(index, index + 2)
                index = code(index + 2, template=True)
            else:
                hide(index, index + 1)
                index += 1
        return index

    def code(index: int, template: bool = False) -> int:
        depth = 0
        while index < length:
            if source.startswith("//", index):
                end = source.find("\n", index)
                end = length if end < 0 else end
                hide(index, end)
                index = end
            elif source.startswith("/*", index):
                start = index
                index += 2
                nesting = 1
                while index < length and nesting:
                    if source.startswith("/*", index):
                        nesting += 1
                        index += 2
                    elif source.startswith("*/", index):
                        nesting -= 1
                        index += 2
                    else:
                        index += 1
                hide(start, index)
            elif source[index] in "\"'":
                index = string(index)
            elif source[index] == "{":
                depth += 1
                index += 1
            elif source[index] == "}" and template and depth == 0:
                hide(index, index + 1)
                return index + 1
            else:
                if source[index] == "}":
                    depth -= 1
                index += 1
        return index

    code(0)
    result = "".join(masked)
    return re.sub(
        r"(?m)^[ \t]*(?:package|import)\b[^\r\n]*",
        lambda match: " " * len(match[0]),
        result,
    )


def qualified_references(source: str) -> list[tuple[int, int, str]]:
    """Find package-qualified symbols, leaving static members/nested types on the short name."""
    references = []
    code = kotlin_code(source)
    for match in QUALIFIED_REFERENCE.finditer(code):
        names = list(re.finditer(r"[A-Za-z_]\w*", match[0]))
        # Gradle's `kotlin` source-set accessor is an object, not the Kotlin package.
        if (
            names[0][0] == "kotlin"
            and names[1][0] not in KOTLIN_PACKAGES | KOTLIN_FUNCTIONS
            and not names[1][0][0].isupper()
        ):
            continue
        last = next(
            (index for index, name in enumerate(names) if name[0][0].isupper()),
            len(names) - 1,
        )
        qualified = ".".join(name[0] for name in names[: last + 1])
        references.append((match.start(), match.start() + names[last].end(), qualified))
    return references


def check_import_style(path: Path, source: str) -> list[str]:
    return [
        f"{path.relative_to(ROOT)}:{source.count(chr(10), 0, start) + 1}: "
        f"import {qualified} instead of using its package name in code"
        for start, _, qualified in qualified_references(source)
    ]


def check_architecture() -> list[str]:
    failures = []
    facades = {}
    catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    if re.search(r"(?im)^\s*[\w.-]*material[\w.-]*\s*=", catalog) or re.search(
        r"(?:androidx|org\.jetbrains)\.compose\.material", catalog
    ):
        failures.append(
            "version catalog: Material / Material 3 dependencies are forbidden by AGENTS.md"
        )
    if not re.search(
        r'^toaster\s*=\s*\{\s*module\s*=\s*"com.github.getActivity:Toaster"',
        catalog,
        re.MULTILINE,
    ):
        failures.append(
            "version catalog: retain Android Toaster compatibility dependency"
        )
    for legacy in ("androidApp", "desktopApp", "domain", "data", "core-jvm"):
        if (ROOT / legacy).exists():
            failures.append(f"{legacy}: obsolete flat module directory")
    if (ROOT / "shared/build.gradle.kts").exists():
        failures.append("shared: use explicitly named modules under shared/")
    for module, allowed in DEPENDENCIES.items():
        directory = ROOT / module
        build = (directory / "build.gradle.kts").read_text(encoding="utf-8")
        failures.extend(check_import_style(directory / "build.gradle.kts", build))
        # Excluding the desktop aggregate's default Material dependency is intentional.
        policy_build = re.sub(
            r'\bexclude\(\s*group\s*=\s*"(?:androidx|org\.jetbrains)\.compose\.material(?:3)?"\s*\)',
            "",
            build,
        )
        if re.search(
            r"\bcompose\.material(?:3)?\b|(?:androidx|org\.jetbrains)\.compose\.material",
            policy_build,
        ):
            failures.append(
                f"{module}: Material / Material 3 dependencies are forbidden by AGENTS.md"
            )
        if module == "apps/android" and "implementation(libs.toaster)" not in build:
            failures.append(
                "apps/android: retain Toaster; shared feedback must not replace its platform compatibility"
            )
        actual = {
            dependency.replace(":", "/")
            for dependency in re.findall(r'project\(":([^"\)]+)"\)', build)
        }
        if unexpected := actual - allowed:
            failures.append(f"{module}: forbidden dependencies {sorted(unexpected)}")
        if "jvmSharedSources" in build or "src/jvmShared" in build:
            failures.append(f"{module}: JVM source mapping belongs to build-logic")

        for path in (directory / "src").rglob("*.kt"):
            source = path.read_text(encoding="utf-8")
            failures.extend(check_import_style(path, source))
            if re.search(r"\bandroidx\.compose\.material(?:3)?\.", source):
                failures.append(
                    f"{path.relative_to(ROOT)}: use project Foundation widgets, not Material / Material 3"
                )
            if re.search(
                r"\b(?:androidx\.compose\.ui\.window|android\.app|androidx\.appcompat\.app)\.(?:Dialog|AlertDialog)\b"
                r"|(?<![\w])(?:Dialog|AlertDialog|BasicAlertDialog)\s*\(",
                source,
            ):
                failures.append(
                    f"{path.relative_to(ROOT)}: use AppDialogHost, not a system Dialog window"
                )
            relative = path.relative_to(directory / "src")
            source_set = relative.parts[0]
            package_match = re.search(r"^package\s+(\S+)", source, re.MULTILINE)
            if not package_match:
                failures.append(f"{path.relative_to(ROOT)}: missing package")
                continue
            package = package_match[1]
            expected = (
                Path(source_set)
                / "kotlin"
                / Path(package.replace(".", "/"))
                / path.name
            )
            if relative != expected:
                failures.append(
                    f"{path.relative_to(ROOT)}: path differs from package {package}"
                )
            jni = module == "shared/native" and package == "yancey.chelper.core"
            owner = OWNERS[module]
            if package != owner and not package.startswith(owner + ".") and not jni:
                failures.append(
                    f"{path.relative_to(ROOT)}: namespace belongs to another module"
                )
            production = not source_set.lower().endswith("test")
            if jni and production and path.stem not in JNI_TYPES:
                failures.append(
                    f"{path.relative_to(ROOT)}: only ABI types belong to the JNI package"
                )
            if not production:
                continue

            forbidden = ()
            if module == "shared/domain":
                forbidden = (
                    "android.",
                    "androidx.",
                    "java.",
                    "javax.",
                    "org.jetbrains.compose.",
                    "yancey.chelper.data.",
                    "yancey.chelper.ui.",
                    "yancey.chelper.core.",
                    "yancey.chelper.nativecore.",
                )
            elif module == "shared/data":
                forbidden = (
                    "android.",
                    "yancey.chelper.android.",
                    "yancey.chelper.ui.",
                    "yancey.chelper.core.",
                    "yancey.chelper.nativecore.",
                )
            elif module in ("shared/native", "shared/ui"):
                forbidden = (
                    "android.",
                    "yancey.chelper.android.",
                    "yancey.chelper.desktop.",
                )
                if module == "shared/ui" and source_set == "androidMain":
                    forbidden = ("yancey.chelper.android.", "yancey.chelper.desktop.")
            if source_set == "commonMain":
                forbidden += ("java.", "javax.")
                if module == "shared/ui":
                    forbidden += (
                        "yancey.chelper.data.",
                        "yancey.chelper.core.",
                        "yancey.chelper.nativecore.",
                    )
            for qualified in re.findall(r"\byancey\.chelper\.(?:\w+\.)+\w+", source):
                if qualified.startswith(forbidden):
                    failures.append(
                        f"{path.relative_to(ROOT)}: forbidden qualified reference {qualified}"
                    )
            for imported in re.findall(r"^import\s+([^\s]+)", source, re.MULTILINE):
                if imported.startswith(forbidden):
                    failures.append(
                        f"{path.relative_to(ROOT)}: forbidden import {imported}"
                    )
                if module.startswith("apps/") and imported.startswith(
                    ("androidx.datastore.core.", "androidx.datastore.preferences.")
                ):
                    failures.append(
                        f"{path.relative_to(ROOT)}: hosts only supply DataStore paths"
                    )
            if module.startswith("apps/") and re.search(
                r"\b(?:dataStore|preferencesDataStore|createDataStore)\s*\(|\b(?:DataStoreFactory|PreferenceDataStoreFactory|MultiProcessDataStoreFactory)\s*\.\s*create",
                source,
            ):
                failures.append(
                    f"{path.relative_to(ROOT)}: construct stores through ClientDataStores"
                )
            if re.search(
                r"^(?:suspend )?fun |^(?:private |internal )?(?:val|var) ",
                source,
                re.MULTILINE,
            ):
                key = package, path.stem
                if key in facades:
                    failures.append(
                        f"Duplicate JVM facade: {facades[key]} and {path.relative_to(ROOT)}"
                    )
                facades[key] = path.relative_to(ROOT)

    # Build convention scripts and the root/settings scripts follow the same import rule.
    extra_scripts = list((ROOT / "build-logic/src").rglob("*.kt"))
    extra_scripts += list((ROOT / "build-logic/src").rglob("*.kts"))
    extra_scripts += list(ROOT.glob("*.kts")) + list(
        (ROOT / "build-logic").glob("*.kts")
    )
    for path in extra_scripts:
        failures.extend(check_import_style(path, path.read_text(encoding="utf-8")))

    resources = ROOT / "shared/ui/src/commonMain/composeResources"
    for path in resources.rglob("*.xml"):
        source = path.read_text(encoding="utf-8")
        if "@android:" in source or "?attr/" in source:
            failures.append(
                f"{path.relative_to(ROOT)}: platform reference in shared resources"
            )
        if path.parent.name.startswith("values") and re.search(r"%(?:s|d|f)", source):
            failures.append(
                f"{path.relative_to(ROOT)}: shared format arguments must be numbered"
            )
    return failures


if __name__ == "__main__":
    problems = check_architecture()
    for problem in problems:
        print(problem)
    if not problems:
        print("Compose module ownership and architecture boundaries passed")
    raise SystemExit(bool(problems))
