# CHelper toolchain installer (Windows, portable, no admin, installs to D:\toolchain)
# Installs: CMake + Ninja + MinGW-w64 (GCC, posix+seh). All are official zips.
# If GitHub API is unreachable: falls back to built-in pinned versions after 20s timeout.
# If downloads fail: pass -Proxy, or manually drop the zips into <Root>\_download and rerun.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\install-toolchain.ps1
#   optional: -Root D:\toolchain -Proxy http://127.0.0.1:7890 -DownloadOnly
# After install, source env.ps1 once per shell before building:
#   . D:\toolchain\env.ps1
param(
    [string]$Root = "D:\toolchain",
    [switch]$DownloadOnly,
    [string]$Proxy = ""
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:web = @{}
if ($Proxy) {
    $script:web['Proxy'] = $Proxy
}

New-Item -ItemType Directory -Force -Path $Root | Out-Null
$dl = Join-Path $Root '_download'
New-Item -ItemType Directory -Force -Path $dl | Out-Null

function Get-LatestAsset {
    param([string]$Repo, [string]$NameRegex, [string]$FallbackUrl = '')
    Write-Host "Querying $Repo latest release ..."
    try {
        $args = @{
            Uri        = "https://api.github.com/repos/$Repo/releases/latest"
            Headers    = @{ 'User-Agent' = 'CHelper-toolchain' }
            TimeoutSec = 20
        }
        if ($script:web['Proxy']) { $args['Proxy'] = $script:web['Proxy'] }
        $r = Invoke-RestMethod @args
        $a = $r.assets | Where-Object { $_.name -match $NameRegex } | Select-Object -First 1
        if ($a) {
            return @{ url = $a.browser_download_url; name = $a.name; tag = $r.tag_name; source = 'github-api' }
        }
        if ($FallbackUrl) {
            Write-Warning 'No matching asset found; using built-in pinned version'
            return @{ url = $FallbackUrl; name = $FallbackUrl.Substring($FallbackUrl.LastIndexOf('/') + 1); tag = ''; source = 'fallback' }
        }
        throw "No asset matching $NameRegex in $Repo"
    } catch {
        if ($FallbackUrl) {
            Write-Warning "Query failed: $($_.Exception.Message)"
            Write-Warning "Fallback to pinned version: $FallbackUrl"
            return @{ url = $FallbackUrl; name = $FallbackUrl.Substring($FallbackUrl.LastIndexOf('/') + 1); tag = ''; source = 'fallback' }
        }
        throw $_
    }
}

function Download {
    param([string]$Url, [string]$Out)
    if (Test-Path $Out) {
        Write-Host "Already downloaded, skip: $(Split-Path $Out -Leaf)"
        return
    }
    Write-Host "Downloading $Url"
    $args = @{
        Uri            = $Url
        OutFile        = $Out
        UseBasicParsing = $true
        TimeoutSec     = 180
    }
    if ($script:web['Proxy']) { $args['Proxy'] = $script:web['Proxy'] }
    try {
        Invoke-WebRequest @args
    } catch {
        throw "Download failed: $Url`n$($_.Exception.Message)"
    }
}

# GitHub Release acceleration mirrors (China). Used when direct GitHub download fails.
$script:mirrorPrefixes = @(
    'https://ghfast.top/',
    'https://gh-proxy.com/',
    'https://ghproxy.net/',
    'https://mirror.ghproxy.com/'
)

# ---------- Resolve download targets ----------
$ninjaFallback = 'https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-win.zip'
$cmakeFallback = 'https://github.com/Kitware/CMake/releases/download/v3.31.5/cmake-3.31.5-windows-x86_64.zip'

$targets = @{}
$targets['ninja'] = Get-LatestAsset 'ninja-build/ninja' 'ninja-win\.zip' $ninjaFallback
$targets['cmake'] = Get-LatestAsset 'Kitware/CMake' 'windows-x86_64\.zip' $cmakeFallback

# MinGW has no stable pinned URL (filenames change per release): skip without aborting
try {
    $targets['mingw'] = Get-LatestAsset 'brechtsanders/winlibs_mingw' 'x86_64-posix-seh.*\.zip'
} catch {
    Write-Warning 'MinGW query failed. A winlibs (x86_64-posix-seh) zip is required.'
    Write-Warning 'Download it from: https://github.com/brechtsanders/winlibs_mingw/releases'
    Write-Warning "and put it into: $dl"
    $existing = Get-ChildItem $dl -Filter '*.zip' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'x86_64-posix-seh' } | Select-Object -First 1
    if ($existing) {
        $targets['mingw'] = @{ url = ''; name = $existing.Name; tag = ''; source = 'local' }
    }
}

# ---------- Download & extract (missing items are collected, others continue) ----------
$missing = @()
foreach ($k in 'ninja', 'cmake', 'mingw') {
    $t = $targets[$k]
    if ($null -eq $t) {
        $missing += $k
        continue
    }
    $zip = Join-Path $dl $t.name
    if ($t.source -ne 'local') {
        # try official URL first, then GitHub-release mirrors
        $urls = @($t.url)
        foreach ($p in $script:mirrorPrefixes) { $urls += ($p + $t.url) }
        $downloaded = $false
        foreach ($u in $urls) {
            try {
                Download $u $zip
                $downloaded = $true
                break
            } catch {
                Write-Warning "Failed from $u"
            }
        }
        if (-not $downloaded) {
            Write-Warning "Download failed for $k from all sources."
            Write-Warning "Download it manually and put the zip into: $dl"
            $missing += $k
            continue
        }
    }
    if ($DownloadOnly) {
        continue
    }
    try {
        Write-Host "Extracting $($t.name) ..."
        Expand-Archive -Path $zip -DestinationPath (Join-Path $Root "_tmp_$k") -Force
    } catch {
        Write-Warning "Extract failed for $k : $($_.Exception.Message)"
        $missing += $k
    }
}

if ($DownloadOnly) {
    Write-Host "Download finished (-DownloadOnly). zips are in $dl"
    return
}

$missing = $missing | Sort-Object -Unique

# 1) ninja: zip contains ninja.exe at root
$ninjaBin = Join-Path $Root 'ninja'
if ($missing -notcontains 'ninja') {
    New-Item -ItemType Directory -Force -Path $ninjaBin | Out-Null
    Copy-Item (Join-Path $Root '_tmp_ninja\ninja.exe') $ninjaBin -Force
}

# 2) cmake: zip contains a single top-level dir cmake-<ver>-windows-x86_64
$cmakeDir = Join-Path $Root 'cmake'
if ($missing -notcontains 'cmake') {
    $cmakeTop = Get-ChildItem (Join-Path $Root '_tmp_cmake') -Directory | Select-Object -First 1
    if (Test-Path $cmakeDir) { Remove-Item $cmakeDir -Recurse -Force }
    Move-Item $cmakeTop.FullName $cmakeDir
}

# 3) mingw: zip contains mingw64/ dir
$mingwDir = Join-Path $Root 'mingw64'
if ($missing -notcontains 'mingw') {
    $mingwTop = Join-Path $Root '_tmp_mingw\mingw64'
    if (-not (Test-Path $mingwTop)) {
        $mingwTop = Get-ChildItem (Join-Path $Root '_tmp_mingw') -Directory | Select-Object -First 1 | ForEach-Object FullName
    }
    if (Test-Path $mingwDir) { Remove-Item $mingwDir -Recurse -Force }
    Move-Item $mingwTop $mingwDir
}

# Cleanup temp dirs
foreach ($k in 'ninja', 'cmake', 'mingw') {
    Remove-Item (Join-Path $Root "_tmp_$k") -Recurse -Force -ErrorAction SilentlyContinue
}

# Generate env.ps1 (adds installed tools to PATH)
$pathParts = @()
if ($missing -notcontains 'cmake') { $pathParts += "$cmakeDir\bin" }
if ($missing -notcontains 'ninja') { $pathParts += $ninjaBin }
if ($missing -notcontains 'mingw') { $pathParts += "$mingwDir\bin" }
if ($pathParts.Count -gt 0) {
    $joined = $pathParts -join ';'
    $envLine = "`$env:PATH = `"$joined;`" + `$env:PATH"
    $envScript = '# Source this file before building to add the CHelper toolchain to PATH.' + "`r`n" + $envLine
    Set-Content -Path (Join-Path $Root 'env.ps1') -Value $envScript -Encoding ASCII
}

Write-Host ''
Write-Host '======================================================'
Write-Host "Installed to: $Root"
if ($missing -notcontains 'cmake') { Write-Host "  cmake : $cmakeDir\bin" } else { Write-Host '  cmake : MISSING' }
if ($missing -notcontains 'ninja') { Write-Host "  ninja : $ninjaBin" } else { Write-Host '  ninja : MISSING' }
if ($missing -notcontains 'mingw') { Write-Host "  g++   : $mingwDir\bin" } else { Write-Host '  g++   : MISSING' }
Write-Host ''
if ($missing.Count -gt 0) {
    Write-Host "Missing: $($missing -join ', ')"
    Write-Host "Download the missing zips into $dl , then rerun this script to finish."
} else {
    Write-Host "Before building, run:  . $Root\env.ps1"
}
Write-Host '======================================================'
