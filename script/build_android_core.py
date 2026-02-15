import os
import shutil
import subprocess
import sys
import zipfile
from urllib.request import urlretrieve


NDK_VERSION = "r29"

def ensure_download_android_ndk(toolchain_dir: str):
    ndk_path = os.path.join(toolchain_dir, f"android-ndk-{NDK_VERSION}")
    if os.path.exists(ndk_path):
        return
    if sys.platform == "win32":
        ndk_url = f"https://googledownloads.cn/android/repository/android-ndk-{NDK_VERSION}-windows.zip"
        archive_path = os.path.join(
            toolchain_dir, f"android-ndk-{NDK_VERSION}-windows.zip"
        )
    else:
        ndk_url = f"https://googledownloads.cn/android/repository/android-ndk-{NDK_VERSION}-linux.zip"
        archive_path = os.path.join(
            toolchain_dir, f"android-ndk-{NDK_VERSION}-linux.zip"
        )
    print(f"Downloading Android NDK from {ndk_url}")
    print("This may take several minutes...")
    try:

        def progress_callback(count, block_size, total_size):
            percent = int(count * block_size * 100 / total_size)
            print(f"\rDownloading... {percent}%")

        urlretrieve(ndk_url, archive_path, progress_callback)
        print("\nDownload completed!")
        print("Extracting NDK...")
        if sys.platform == "win32":
            with zipfile.ZipFile(archive_path, "r") as zip_ref:
                zip_ref.extractall(toolchain_dir)
        else:
            with zipfile.ZipFile(archive_path, "r") as zip_ref:
                zip_ref.extractall(toolchain_dir)
        os.remove(archive_path)
        print(f"NDK successfully installed to {ndk_path}")
    except Exception as e:
        print(f"Failed to download or extract NDK: {e}")
        print("\n--- Alternative Download Method ---")
        print("1. Manually download Android NDK:")
        print(f"   {ndk_url}")
        print(f"2. Extract it to: {toolchain_dir}")
        print(f"3. Ensure the folder is named: android-ndk-{NDK_VERSION}")
        sys.exit(1)


def build_android_core(toolchain_dir: str):
    ndk_path = os.path.join(toolchain_dir, f"android-ndk-{NDK_VERSION}")
    build_directory = "./build/android_core"
    subprocess.run(
        [
            "cmake",
            "-S",
            "./CHelper-Core",
            "-D",
            "CMAKE_BUILD_TYPE=Release",
            "-D",
            f'CMAKE_TOOLCHAIN_FILE={ndk_path}/build/cmake/android.toolchain.cmake',
            "-D",
            "ANDROID_ABI=arm64-v8a",
            "-D",
            f"ANDROID_NDK={ndk_path}",
            "-D",
            "ANDROID_PLATFORM=android-24",
            "-D",
            "CMAKE_ANDROID_ARCH_ABI=arm64-v8a",
            "-D",
            f"CMAKE_ANDROID_NDK={ndk_path}",
             "-D",
            "CMAKE_SYSTEM_NAME=Android",
             "-D",
            "CMAKE_SYSTEM_VERSION=24",
            "-B",
            build_directory,
            "-G",
            "Ninja",
        ],
        check=True,
    )
    subprocess.run(
        ["cmake", "--build", build_directory, "--target", "CHelperAndroid"],
        check=True,
    )
    so_file = os.path.join(build_directory, "libCHelperAndroid.so")
    if sys.platform == "win32":
        llvm_strip_path = (
            f"{ndk_path}/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"
        )
    else:
        llvm_strip_path = (
            f"{ndk_path}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
        )
    subprocess.run([llvm_strip_path, so_file, '-o', f'{so_file}.striped'], shell=True)
    shutil.copyfile(
        f"{so_file}.striped",
        os.path.join('.', 'CHelper-Android', 'app', 'libs', 'arm64-v8a', 'libCHelperAndroid.so')
    )


if __name__ == "__main__":
    # check toolchain
    if subprocess.run(["cmake", "--version"], capture_output=True).returncode != 0:
        print("please download cmake")
        exit(-1)
    if subprocess.run(["ninja", "--version"], capture_output=True).returncode != 0:
        print("please download ninja")
        exit(-1)
    toolchain_dir = os.path.join(os.getcwd(), "toolchain")
    os.makedirs(toolchain_dir, exist_ok=True)
    ensure_download_android_ndk(toolchain_dir)

    # build android core
    print("building android core...")
    build_android_core(toolchain_dir)
