import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path

from build_android import build_android_apk, get_gradlew
from build_android_core import build_android_core, ensure_download_android_ndk
from build_web_core import build_web_core, ensure_download_emsdk

from native_build_tools import PROJECT_DIR, get_native_build_env

PACK_FILENAME = re.compile(
    r"(?:release|beta|netease)-(?:vanilla|experiment)-[0-9]+(?:\.[0-9]+)+\.cpack"
)


def check_tool(command: list[str]):
    try:
        subprocess.run(command, capture_output=True, text=True, check=True)
    except FileNotFoundError as error:
        raise RuntimeError(f"Required tool not found: {command[0]}") from error


def sync_resource_packs(source: Path, *destinations: Path):
    packs = {
        path.name: path
        for path in source.iterdir()
        if PACK_FILENAME.fullmatch(path.name) and path.is_file()
    }
    branches = {
        f"{edition}-{branch}"
        for edition in ("release", "beta", "netease")
        for branch in ("vanilla", "experiment")
    }
    if (
        len(packs) != len(branches)
        or {name.rsplit("-", 1)[0] for name in packs} != branches
    ):
        raise RuntimeError(
            f"Expected one generated resource pack per branch in {source}"
        )
    for destination in destinations:
        destination.mkdir(parents=True, exist_ok=True)
        for name, path in packs.items():
            shutil.copy2(path, destination / name)
    for destination in destinations:
        for path in destination.iterdir():
            if (
                PACK_FILENAME.fullmatch(path.name)
                and path.name not in packs
                and path.is_file()
            ):
                path.unlink()


def generate_resources(env: dict[str, str], *, update_versions: bool = False):
    resource_project = PROJECT_DIR / "CHelper-Resource"
    resources = resource_project / "resources"
    command = "refresh-suggestion-media"
    if update_versions:
        command = "generate"
        config_path = resource_project / "config.toml"
        config = {}
        if config_path.is_file():
            with config_path.open("rb") as file:
                config = tomllib.load(file)
        resources = resource_project / config.get("output_dir", "output/chelper")
    subprocess.run(
        ["cargo", "run", "--locked", "--release", "--", command],
        cwd=resource_project,
        check=True,
    )

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
    generated = resource_project / "generated"
    subprocess.run(
        [str(build_directory / executable), str(resources), str(generated)],
        env=env,
        check=True,
    )
    compose_resources = PROJECT_DIR / "CHelper-Compose" / "resources"
    sync_resource_packs(
        generated / "cpack",
        compose_resources / "cpack",
        PROJECT_DIR / "CHelper-Web" / "src" / "assets",
    )
    shutil.copytree(
        generated / "old2new", compose_resources / "old2new", dirs_exist_ok=True
    )
    manifest_path = resources / "release" / "experiment" / "manifest.json"
    with manifest_path.open(encoding="utf-8") as file:
        version = json.load(file)["version"]
    qt_assets = PROJECT_DIR / "CHelper-Core" / "src" / "apps" / "qt" / "assets"
    qt_assets.mkdir(parents=True, exist_ok=True)
    filename = f"release-experiment-{version}.cpack"
    shutil.copyfile(generated / "cpack" / filename, qt_assets / filename)


def main():
    parser = argparse.ArgumentParser(description="Build CHelper resources and clients.")
    parser.add_argument(
        "--resources-only",
        action="store_true",
        help="generate and sync resource packs only",
    )
    parser.add_argument(
        "--update-resources",
        action="store_true",
        help="regenerate resources from configured version sources before packaging",
    )
    args = parser.parse_args()

    # The component scripts share paths relative to the repository root.
    os.chdir(PROJECT_DIR)
    pnpm = "pnpm.cmd" if sys.platform == "win32" else "pnpm"
    for command in (
        ["cargo", "--version"],
        ["cmake", "--version"],
        ["ninja", "--version"],
    ):
        check_tool(command)
    if not args.resources_only:
        check_tool(["node", "-v"])
        check_tool([pnpm, "-v"])
        subprocess.run(
            [get_gradlew(), "--version"],
            cwd=PROJECT_DIR / "CHelper-Compose",
            check=True,
        )
    native_env = get_native_build_env()
    print("Generating resources...", flush=True)
    generate_resources(native_env, update_versions=args.update_resources)
    if args.resources_only:
        return

    toolchain_dir = str(PROJECT_DIR / "toolchain")
    Path(toolchain_dir).mkdir(exist_ok=True)
    ensure_download_android_ndk(toolchain_dir)
    ensure_download_emsdk(toolchain_dir)
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
    except (
        RuntimeError,
        OSError,
        subprocess.CalledProcessError,
        tomllib.TOMLDecodeError,
    ) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
