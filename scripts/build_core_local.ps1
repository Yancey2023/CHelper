# Build CHelper-Core locally with D:\toolchain (MinGW + cmake + ninja).
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\build_core_local.ps1
# Notes:
#   - Requires installed toolchain (see install-toolchain.ps1) and git.
#   - First configure downloads third-party deps from GitHub (fmt/spdlog/xxhash/utfcpp/serialization/googletest/emscripten).
#     If it fails or stalls due to network, rerun (partial clones are cached) or set git proxy:
#       git config --global http.proxy http://127.0.0.1:7890
$ErrorActionPreference = 'Stop'

$envFile = 'D:\toolchain\env.ps1'
if (Test-Path $envFile) { . $envFile } else { Write-Error 'Toolchain not found at D:\toolchain. Run install-toolchain.ps1 first.' }

$root = Split-Path -Parent $PSScriptRoot
$coreDir = Join-Path $root 'CHelper-Core'
$buildDir = Join-Path $coreDir 'cmake-build-release'

Write-Host "cmake --version: $(cmake --version | Select-Object -First 1)"
Write-Host "ninja: $(ninja --version)"
Write-Host "g++: $(g++ --version | Select-Object -First 1)"

Write-Host '== configure =='
cmake -S $coreDir -B $buildDir -G Ninja
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'configure failed. If due to network when fetching deps, rerun this script (partial downloads are cached) or configure a git proxy, then rerun.'
    exit 1
}

Write-Host '== build (CHelperTest only; Web/Android/Qt targets need their own toolchains) =='
cmake --build $buildDir --target CHelperTest
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'build failed.'
    exit 1
}

Write-Host '== run tests =='
& (Join-Path $buildDir 'CHelperTest.exe')
