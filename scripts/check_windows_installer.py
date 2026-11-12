"""Read the MSI database without installing it and verify CHelper's URL protocol registration."""

import argparse
import ctypes
from ctypes import wintypes
from pathlib import Path


def check_installer(path: Path) -> None:
    msi = ctypes.WinDLL("msi")
    handle = wintypes.UINT
    msi.MsiOpenDatabaseW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.LPCWSTR,
        ctypes.POINTER(handle),
    ]
    msi.MsiDatabaseOpenViewW.argtypes = [
        handle,
        wintypes.LPCWSTR,
        ctypes.POINTER(handle),
    ]
    msi.MsiViewExecute.argtypes = [handle, handle]
    msi.MsiViewFetch.argtypes = [handle, ctypes.POINTER(handle)]
    msi.MsiRecordGetStringW.argtypes = [
        handle,
        wintypes.UINT,
        wintypes.LPWSTR,
        ctypes.POINTER(wintypes.DWORD),
    ]
    msi.MsiCloseHandle.argtypes = [handle]
    handles = []

    def success(code: int) -> None:
        if code:
            raise OSError(code, "Unable to read the MSI database")

    try:
        database = handle()
        success(msi.MsiOpenDatabaseW(str(path.resolve()), None, ctypes.byref(database)))
        handles.append(database)
        view = handle()
        success(
            msi.MsiDatabaseOpenViewW(
                database,
                "SELECT `Key`, `Name`, `Value` FROM `Registry`",
                ctypes.byref(view),
            )
        )
        handles.append(view)
        success(msi.MsiViewExecute(view, 0))
        protocol = []
        while True:
            row = handle()
            code = msi.MsiViewFetch(view, ctypes.byref(row))
            if code == 259:  # ERROR_NO_MORE_ITEMS
                break
            success(code)
            try:
                values = []
                for column in range(1, 4):
                    buffer = ctypes.create_unicode_buffer(32768)
                    length = wintypes.DWORD(len(buffer))
                    success(
                        msi.MsiRecordGetStringW(
                            row, column, buffer, ctypes.byref(length)
                        )
                    )
                    values.append(buffer.value)
                if values[0].startswith("Software\\Classes\\chelper"):
                    protocol.append(values)
            finally:
                msi.MsiCloseHandle(row)
        assert any(
            key == "Software\\Classes\\chelper" and name == "URL Protocol"
            for key, name, _ in protocol
        ), protocol
        assert any(
            key.endswith("shell\\open\\command")
            and value == '"[INSTALLDIR]CHelper.exe" "%1"'
            for key, _, value in protocol
        ), protocol
        print("Windows installer URL protocol passed:", path.name)
    finally:
        for value in reversed(handles):
            msi.MsiCloseHandle(value)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("msi", type=Path)
    check_installer(parser.parse_args().msi)
