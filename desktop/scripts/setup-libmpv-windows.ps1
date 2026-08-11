$ErrorActionPreference = 'Stop'

$sdkRoot = Join-Path $env:RUNNER_TEMP 'wizestream-libmpv'
$archive = Join-Path $sdkRoot 'libmpv.7z'
$extracted = Join-Path $sdkRoot 'archive'
$includeDirectory = Join-Path $sdkRoot 'include'
$runtimeDirectory = Join-Path $sdkRoot 'bin'
$library = Join-Path $sdkRoot 'lib\mpv.lib'

New-Item -ItemType Directory -Force -Path $sdkRoot, $extracted, $includeDirectory, $runtimeDirectory | Out-Null
$assetName = 'mpv-dev-x86_64-20260811-git-f4d13e1c2c.7z'
$assetUrl = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20260811/$assetName"
$expectedSha256 = 'd849de71d4e57ac7f92cedbda50564af4431d84bd1898e9ee6f9a9fc21d42427'
$headers = @{ 'User-Agent' = 'WizeStream-Desktop-CI' }
Invoke-WebRequest -Headers $headers -Uri $assetUrl -OutFile $archive
$actualSha256 = (Get-FileHash -Algorithm SHA256 -Path $archive).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
  throw "The pinned libmpv archive checksum does not match: $actualSha256"
}
& 7z.exe x $archive "-o$extracted" -y | Out-Null
if ($LASTEXITCODE -ne 0) { throw '7-Zip could not extract the libmpv development archive.' }

$clientHeader = Get-ChildItem -Path $extracted -Recurse -Filter client.h |
  Where-Object { $_.Directory.Name -eq 'mpv' } |
  Select-Object -First 1
if (-not $clientHeader) { throw 'mpv/client.h was not found in the development archive.' }
$mpvInclude = Join-Path $includeDirectory 'mpv'
New-Item -ItemType Directory -Force -Path $mpvInclude | Out-Null
Copy-Item -Path (Join-Path $clientHeader.Directory.FullName '*') -Destination $mpvInclude -Recurse -Force

$runtimeFiles = Get-ChildItem -Path $extracted -Recurse -Filter '*.dll'
if (-not $runtimeFiles) { throw 'No libmpv runtime DLL was found in the development archive.' }
$runtimeFiles | ForEach-Object { Copy-Item -Path $_.FullName -Destination $runtimeDirectory -Force }
$mpvDll = Get-ChildItem -Path $runtimeDirectory -Filter 'libmpv-2.dll' | Select-Object -First 1
if (-not $mpvDll) { $mpvDll = Get-ChildItem -Path $runtimeDirectory -Filter 'mpv-2.dll' | Select-Object -First 1 }
if (-not $mpvDll) { throw 'The extracted runtime does not contain libmpv-2.dll or mpv-2.dll.' }

$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$visualStudio = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $visualStudio) { throw 'Visual Studio C++ build tools were not found.' }
$toolsVersion = (Get-Content (Join-Path $visualStudio 'VC\Auxiliary\Build\Microsoft.VCToolsVersion.default.txt')).Trim()
$toolsBin = Join-Path $visualStudio "VC\Tools\MSVC\$toolsVersion\bin\Hostx64\x64"
$env:Path = "$toolsBin;$env:Path"

$env:MPV_INCLUDE_DIR = $includeDirectory
$env:MPV_LIB = $library
$env:MPV_RUNTIME_DIR = $runtimeDirectory
& (Join-Path $PSScriptRoot '..\node_modules\electron-mpv-video\scripts\create-libmpv-import-lib.ps1') `
  -DllPath $mpvDll.FullName -OutputPath $library

"MPV_INCLUDE_DIR=$includeDirectory" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
"MPV_LIB=$library" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
"MPV_RUNTIME_DIR=$runtimeDirectory" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
Write-Host "Prepared and verified $assetName for the WizeStream embedded player."
