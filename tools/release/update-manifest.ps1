<#
.SYNOPSIS
    Пересобирает update_manifest.json под только что собранный APK.

.DESCRIPTION
    Манифест автообновления должен содержать размер и SHA-256 реально выпущенного файла.
    Подставлять значения вручную опасно: апдейтер проверяет имя тега, имя APK, размер,
    контрольную сумму и версию, и при расхождении просто не предложит обновление.

.EXAMPLE
    .\tools\release\update-manifest.ps1 -ApkPath app\build\outputs\apk\release\app-release.apk -Version 1.4.0.2 -Changelog "Краткое описание релиза."
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Changelog = ''
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK не найден: $ApkPath"
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$manifestPath = Join-Path $repoRoot 'update_manifest.json'

$file = Get-Item -LiteralPath $ApkPath
$sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$tag = "v$Version"
# Имя файла — как у всех прошлых релизов: KupuProxy-v<версия>.apk.
# UpdateArtifactPolicy принимает оба варианта, но менять схему имён без причины не нужно.
$apkName = "KupuProxy-v$Version.apk"
$base = "https://github.com/Kirillka645/KupuProxy/releases/download/$tag"

$manifest = [ordered]@{
    version     = $Version
    tag_name    = $tag
    apk_name    = $apkName
    apk_url     = "$base/$apkName"
    apk_size    = $file.Length
    sha256      = $sha256
    sha256_url  = "$base/$apkName.sha256"
    release_url = "https://github.com/Kirillka645/KupuProxy/releases/tag/$tag"
    changelog   = $Changelog
}

$json = $manifest | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($manifestPath, $json, (New-Object System.Text.UTF8Encoding($false)))

# Рядом кладём файл контрольной суммы — его тоже ожидает апдейтер.
$shaPath = Join-Path $file.DirectoryName "$apkName.sha256"
[System.IO.File]::WriteAllText($shaPath, "$sha256  $apkName`n", (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Манифест обновлён: $manifestPath"
Write-Host "  версия : $Version"
Write-Host "  размер : $($file.Length) байт"
Write-Host "  sha256 : $sha256"
Write-Host "  .sha256: $shaPath"