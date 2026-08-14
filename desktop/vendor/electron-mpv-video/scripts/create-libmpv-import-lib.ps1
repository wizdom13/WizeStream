param(
  [string]$DllPath = $(
    if ($env:MPV_RUNTIME_DIR) {
      Join-Path $env:MPV_RUNTIME_DIR 'libmpv-2.dll'
    } else {
      Join-Path (Join-Path $env:USERPROFILE 'libmpv') 'bin\libmpv-2.dll'
    }
  ),
  [string]$OutputPath = $(
    if ($env:MPV_LIB) {
      $env:MPV_LIB
    } else {
      Join-Path (Join-Path $env:USERPROFILE 'libmpv') 'lib\mpv.lib'
    }
  )
)

$ErrorActionPreference = 'Stop'
$DllPath = [System.IO.Path]::GetFullPath($DllPath)
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$DefPath = [System.IO.Path]::ChangeExtension($OutputPath, '.def')

if (-not (Test-Path $DllPath -PathType Leaf)) {
  throw "libmpv DLL not found: $DllPath"
}

$exports = & dumpbin.exe /nologo /exports $DllPath |
  Select-String '^\s+\d+\s+[0-9A-Fa-f]+\s+[0-9A-Fa-f]+\s+(\S+)' |
  ForEach-Object { $_.Matches[0].Groups[1].Value }

if ($LASTEXITCODE -ne 0 -or $exports.Count -eq 0) {
  throw 'Unable to read exports. Run this script from a Visual Studio x64 Native Tools command prompt.'
}

New-Item -ItemType Directory -Force -Path (Split-Path $OutputPath) | Out-Null
@('LIBRARY libmpv-2.dll', 'EXPORTS') + $exports |
  Set-Content -Path $DefPath -Encoding ascii

& lib.exe /nologo "/def:$DefPath" "/out:$OutputPath" /machine:x64
if ($LASTEXITCODE -ne 0) {
  throw 'lib.exe failed to create the MSVC import library.'
}

Write-Host "Created $OutputPath"
