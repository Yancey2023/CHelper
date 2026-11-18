import os
import shutil
import subprocess
import sys


def get_gradlew() -> str:
    gradlew = "gradlew.bat" if sys.platform == "win32" else "gradlew"
    return os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "CHelper-Compose", gradlew)
    )


def build_android_apk():
    gradlew = get_gradlew()
    android_dir = os.path.abspath(os.path.join(".", "CHelper-Compose"))
    release_note = os.path.join(android_dir, "resources", "about", "release_note.txt")
    changelog = os.path.abspath(os.path.join(".", "CHANGELOG.md"))
    with open(release_note, "rb") as file:
        original_release_note = file.read()
    try:
        shutil.copyfile(changelog, release_note)
        subprocess.run(
            [gradlew, ":apps:android:assembleRelease"],
            cwd=android_dir,
            check=True,
        )
    finally:
        with open(release_note, "wb") as file:
            file.write(original_release_note)


if __name__ == "__main__":
    # check toolchain
    if (
        subprocess.run(
            [get_gradlew(), "--version"],
            capture_output=True,
            check=False,
            cwd=os.path.abspath(os.path.join(".", "CHelper-Compose")),
        ).returncode
        != 0
    ):
        print("please download JDK (required by gradle wrapper)")
        sys.exit(-1)

    # build apk
    print("building apk...")
    build_android_apk()
