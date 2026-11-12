import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from build_android import build_android_apk, get_gradlew
from build_android_core import build_android_core, ensure_download_android_ndk
from build_web_core import build_web_core, ensure_download_emsdk

from native_build_tools import PROJECT_DIR, get_native_build_env


def check_tool(command: list[str]):
    try:
        subprocess.run(command, capture_output=True, text=True, check=True)
    except FileNotFoundError as error:
        raise RuntimeError(f"Required tool not found: {command[0]}") from error


def generate_resources(env: dict[str, str]):
    build_directory = PROJECT_DIR / "build" / "resource_generator"
    subprocess.run(
        [
            "cmake",
            "-S",
            str(PROJECT_DIR / "CHelper-Core"),
            "-B",
            str(build_directory),
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=Release",
        ],
        env=env,
        check=True,
    )
    subprocess.run(
        [
            "cmake",
            "--build",
            str(build_directory),
            "--target",
            "CHelperResourceGenerator",
        ],
        env=env,
        check=True,
    )
    executable = (
        "CHelperResourceGenerator.exe"
        if sys.platform == "win32"
        else "CHelperResourceGenerator"
    )
    subprocess.run([str(build_directory / executable)], env=env, check=True)
    generated = PROJECT_DIR / "CHelper-Resource" / "generated"
    compose_resources = PROJECT_DIR / "CHelper-Compose" / "resources"
    shutil.copytree(
        generated / "cpack", compose_resources / "cpack", dirs_exist_ok=True
    )
    shutil.copytree(
        generated / "cpack",
        PROJECT_DIR / "CHelper-Web" / "src" / "assets",
        dirs_exist_ok=True,
    )
    shutil.copytree(
        generated / "old2new", compose_resources / "old2new", dirs_exist_ok=True
    )
    manifest_path = (
        PROJECT_DIR
        / "CHelper-Resource"
        / "resources"
        / "release"
        / "experiment"
        / "manifest.json"
    )
    with manifest_path.open(encoding="utf-8") as file:
        version = json.load(file)["version"]
    qt_assets = PROJECT_DIR / "CHelper-Core" / "src" / "apps" / "qt" / "assets"
    qt_assets.mkdir(parents=True, exist_ok=True)
    filename = f"release-experiment-{version}.cpack"
    shutil.copyfile(generated / "cpack" / filename, qt_assets / filename)


def main():
    # The component scripts share paths relative to the repository root.
    os.chdir(PROJECT_DIR)
    pnpm = "pnpm.cmd" if sys.platform == "win32" else "pnpm"
    for command in (
        ["node", "-v"],
        ["cmake", "--version"],
        ["ninja", "--version"],
        [pnpm, "-v"],
    ):
        check_tool(command)
    subprocess.run(
        [get_gradlew(), "--version"], cwd=PROJECT_DIR / "CHelper-Compose", check=True
    )
    native_env = get_native_build_env()
    toolchain_dir = str(PROJECT_DIR / "toolchain")
    Path(toolchain_dir).mkdir(exist_ok=True)
    ensure_download_android_ndk(toolchain_dir)
    ensure_download_emsdk(toolchain_dir)
    print("Generating resources...", flush=True)
    generate_resources(native_env)
    (PROJECT_DIR / "CHelper-Web" / "src" / "assets").mkdir(parents=True, exist_ok=True)
    print("Building Android core...", flush=True)
    build_android_core(toolchain_dir)
    print("Building Web core...", flush=True)
    build_web_core(toolchain_dir)
    print("Building APK...", flush=True)
    build_android_apk()
    if sys.platform == "win32":
        print("Building Windows distribution...", flush=True)
        subprocess.run(
            [get_gradlew(), ":apps:desktop:packageDistributionForCurrentOS"],
            cwd=PROJECT_DIR / "CHelper-Compose",
            check=True,
        )
    subprocess.run([pnpm, "install", "--frozen-lockfile"], check=True)
    subprocess.run([pnpm, "build"], check=True)
    subprocess.run([pnpm, "docs:build"], check=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
