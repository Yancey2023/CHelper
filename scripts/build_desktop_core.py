"""Build the Windows x64 JNI bridge bundled by CHelper-Compose."""

from pathlib import Path
import os
import shutil
import subprocess
import sys

from native_build_tools import PROJECT_DIR, get_native_build_env


def build_desktop_core():
    if sys.platform != "win32":
        raise RuntimeError("The Windows distribution must be built on Windows.")
    env = get_native_build_env()
    java = shutil.which("javac", path=env.get("Path", env.get("PATH")))
    if not java:
        raise RuntimeError("A full JDK is required to build the JNI bridge.")
    env.setdefault("JAVA_HOME", str(Path(java).resolve().parent.parent))
    build = PROJECT_DIR / "build" / "desktop_core"
    subprocess.run(
        [
            "cmake",
            "-S",
            str(PROJECT_DIR / "CHelper-Core"),
            "-B",
            str(build),
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=Release",
            "-DBUILD_TESTING=OFF",
            "-DCHELPER_BUILD_JNI=ON",
        ],
        env=env,
        check=True,
    )
    subprocess.run(
        ["cmake", "--build", str(build), "--target", "CHelperJvm"], env=env, check=True
    )
    destination = PROJECT_DIR / "CHelper-Compose/apps/desktop/native/windows-x64"
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(build / "CHelperJvm.dll", destination / "CHelperJvm.dll")
    # Include the MSVC runtime alongside JNI so the installer works on clean Windows hosts.
    redist = env.get("VCToolsRedistDir")
    if redist:
        for dll in (Path(redist) / "x64").glob("Microsoft.VC*.CRT/*.dll"):
            shutil.copy2(dll, destination / dll.name)
    return destination


if __name__ == "__main__":
    try:
        print(build_desktop_core())
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"Desktop core build failed: {error}", file=sys.stderr)
        sys.exit(1)
