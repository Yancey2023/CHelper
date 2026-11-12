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


def check_architecture() -> list[str]:
    failures = []
    facades = {}
    for legacy in ("androidApp", "desktopApp", "domain", "data", "core-jvm"):
        if (ROOT / legacy).exists():
            failures.append(f"{legacy}: obsolete flat module directory")
    if (ROOT / "shared/build.gradle.kts").exists():
        failures.append("shared: use explicitly named modules under shared/")
    for module, allowed in DEPENDENCIES.items():
        directory = ROOT / module
        build = (directory / "build.gradle.kts").read_text(encoding="utf-8")
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
