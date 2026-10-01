import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from build_android import build_android_apk, get_gradlew
from build_android_core import build_android_core, ensure_download_android_ndk
from build_web_core import build_web_core, ensure_download_emsdk

PROJECT_DIR = Path(__file__).resolve().parent.parent


def check_tool(command: list[str]):
    try:
        subprocess.run(command, capture_output=True, text=True, check=True)
    except FileNotFoundError as error:
        raise RuntimeError(f"Required tool not found: {command[0]}") from error


def get_native_build_env() -> dict[str, str]:
    env = os.environ.copy()
    if sys.platform != "win32" or shutil.which("cl.exe"):
        return env
    vswhere = shutil.which("vswhere.exe")
    if not vswhere:
        vswhere = str(
            Path(env.get("ProgramFiles(x86)", r"C:\Program Files (x86)"))
            / "Microsoft Visual Studio"
            / "Installer"
            / "vswhere.exe"
        )
    if not Path(vswhere).is_file():
        raise RuntimeError(
            "Cannot find vswhere.exe. Install Visual Studio or Build Tools with the Desktop development with C++ workload."
        )
    installation = subprocess.run(
        [
            vswhere,
            "-latest",
            "-products",
            "*",
            "-requires",
            "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
            "-property",
            "installationPath",
            "-utf8",
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    ).stdout.strip()
    if not installation:
        raise RuntimeError(
            "No MSVC toolchain found. Install the Desktop development with C++ workload."
        )
    vcvars = Path(installation) / "VC" / "Auxiliary" / "Build" / "vcvars64.bat"
    if not vcvars.is_file():
        raise RuntimeError(f"MSVC environment script not found: {vcvars}")
    print(f"Preparing MSVC environment: {vcvars}", flush=True)
    # Pass the path through the environment so cmd does not reinterpret its contents.
    env["CHELPER_VCVARS"] = str(vcvars)
    result = subprocess.run(
        'cmd.exe /d /u /c call "%CHELPER_VCVARS%" >nul && set',
        env=env,
        capture_output=True,
        check=True,
    )
    for line in result.stdout.decode("utf-16le").splitlines():
        name, separator, value = line.partition("=")
        if separator and name:
            env[name] = value
    env.pop("CHELPER_VCVARS", None)
    if not shutil.which("cl.exe", path=env.get("Path", env.get("PATH"))):
        raise RuntimeError("MSVC environment setup did not provide cl.exe.")
    return env


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
    android_assets = PROJECT_DIR / "CHelper-Android" / "app" / "src" / "main" / "assets"
    shutil.copytree(generated / "cpack", android_assets / "cpack", dirs_exist_ok=True)
    shutil.copytree(
        generated / "cpack",
        PROJECT_DIR / "CHelper-Web" / "src" / "assets",
        dirs_exist_ok=True,
    )
    shutil.copytree(
        generated / "old2new", android_assets / "old2new", dirs_exist_ok=True
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
        [get_gradlew(), "--version"], cwd=PROJECT_DIR / "CHelper-Android", check=True
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
    subprocess.run([pnpm, "install", "--frozen-lockfile"], check=True)
    subprocess.run([pnpm, "build"], check=True)
    subprocess.run([pnpm, "docs:build"], check=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
