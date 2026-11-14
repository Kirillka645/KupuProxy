# JDK 17 jpackage requires WiX 3.x, not WiX 4+.
$ErrorActionPreference = 'Stop'
if (Get-Command light.exe -ErrorAction SilentlyContinue) { exit 0 }
$wix = Get-ChildItem "${env:ProgramFiles(x86)}\WiX Toolset*\bin\light.exe" -ErrorAction SilentlyContinue
if (-not $wix) {
    choco install wixtoolset --version=3.11.2 --yes --no-progress --limit-output
    if ($LASTEXITCODE -ne 0) { throw 'WiX 3.x installation failed' }
    $wix = Get-ChildItem "${env:ProgramFiles(x86)}\WiX Toolset*\bin\light.exe" -ErrorAction SilentlyContinue
}
if (-not $wix) { throw 'WiX 3.x light.exe was not found; MSI is required' }
Add-Content -Path $env:GITHUB_PATH -Value (Split-Path -Parent $wix[0].FullName)
