import os
import shutil
import subprocess
import json
import sys
from build_android_core import ensure_download_android_ndk, build_android_core
from build_web_core import ensure_download_emsdk, build_web_core

if __name__ == "__main__":
    # check toolchain
    if subprocess.run(["node", "-v"], capture_output=True).returncode != 0:
        print("please download nodejs")
        exit(-1)
    if subprocess.run(["cmake", "--version"], capture_output=True).returncode != 0:
        print("please download cmake")
        exit(-1)
    if subprocess.run(["ninja", "--version"], capture_output=True).returncode != 0:
        print("please download ninja")
        exit(-1)
    if sys.platform == "win32":
        gradlew = "gradlew.bat"
        pnpm = "pnpm.cmd"
    else:
        gradlew = "gradlew"
        pnpm = "pnpm"
    if subprocess.run([pnpm, "-v"], capture_output=True).returncode != 0:
        print("please download pnpm")
        exit(-1)
    toolchain_dir = os.path.join(os.getcwd(), "toolchain")
    os.makedirs(toolchain_dir, exist_ok=True)
    ensure_download_android_ndk(toolchain_dir)
    ensure_download_emsdk(toolchain_dir)

    # clean
    print("cleaning...")
    shutil.rmtree(
        os.path.join(".", "CHelper-Core", "src", "apps", "qt", "assets"),
        ignore_errors=True,
    )
    shutil.rmtree(
        os.path.join(".", "CHelper-Resource", "generated"), ignore_errors=True
    )
    shutil.rmtree(
        os.path.join(".", "CHelper-Android", "app", "src", "main", "assets", "cpack"),
        ignore_errors=True,
    )
    shutil.rmtree(
        os.path.join(".", "CHelper-Android", "app", "build", "outputs"),
        ignore_errors=True,
    )
    shutil.rmtree(os.path.join(".", "CHelper-Web", "src", "assets"), ignore_errors=True)
    os.makedirs(
        os.path.join(".", "CHelper-Core", "src", "apps", "qt", "assets"), exist_ok=True
    )
    os.makedirs(os.path.join(".", "CHelper-Web", "src", "assets"), exist_ok=True)

    # build android core
    print("building android core...")
    build_android_core(toolchain_dir)

    # build web core
    print("building web core...")
    build_web_core(toolchain_dir)

    # generate resources
    print("generating resources")
    subprocess.run(
        [
            "cmake",
            "--build",
            "./CHelper-Core/cmake-build-release",
            "--target",
            "CHelperResourceGenerator",
        ],
        check=True,
    )
    subprocess.run(
        [
            os.path.join(
                ".",
                "CHelper-Core",
                "cmake-build-release",
                "CHelperResourceGenerator.exe",
            )
        ],
        check=True,
    )
    shutil.copytree(
        os.path.join(".", "CHelper-Resource", "generated", "cpack"),
        os.path.join(".", "CHelper-Android", "app", "src", "main", "assets", "cpack"),
        dirs_exist_ok=True,
    )
    shutil.copytree(
        os.path.join(".", "CHelper-Resource", "generated", "cpack"),
        os.path.join(".", "CHelper-Web", "src", "assets"),
        dirs_exist_ok=True,
    )
    with open(
        os.path.join(
            ".",
            "CHelper-Resource",
            "resources",
            "release",
            "experiment",
            "manifest.json",
        ),
        "r",
        encoding="utf-8",
    ) as file:
        manifest = json.load(file)
        version = manifest["version"]
        shutil.copyfile(
            os.path.join(
                ".",
                "CHelper-Resource",
                "generated",
                "cpack",
                f"release-experiment-{version}.cpack",
            ),
            os.path.join(
                ".",
                "CHelper-Core",
                "src",
                "apps",
                "qt",
                "assets",
                f"release-experiment-{version}.cpack",
            ),
        )

    # build apk
    print("building apk...")
    os.chdir("CHelper-Android")
    subprocess.run([gradlew, "assembleRelease"], check=True)
    os.chdir("..")

    # build web
    print("building web...")
    os.chdir("CHelper-Web")
    subprocess.run([pnpm, "build"], check=True)
    os.chdir("..")

    # build doc
    print("building doc...")
    os.chdir("CHelper-Doc")
    subprocess.run([pnpm, "docs:build"], check=True)
    os.chdir("..")
