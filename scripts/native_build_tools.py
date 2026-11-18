"""Shared native toolchain discovery used by build entry points."""

import os
import shutil
import subprocess
import sys
from pathlib import Path

PROJECT_DIR = Path(__file__).resolve().parent.parent


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
