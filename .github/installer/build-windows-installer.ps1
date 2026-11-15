[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [int]$BuildNumber = 0,
    [string]$PayloadDir = "desktopApp/build/compose/binaries/main-release/app/Metrolist",
    [string]$ManifestPath = "desktopApp/build/windows-portable/Release/install-manifest.json",
    [string]$IconPath = "desktopApp/src/desktopMain/resources/logo.ico",
    [string]$WixDir = "build/wix311",
    [string]$WorkDir = "desktopApp/build/installer-work",
    [string]$OutputExe = ""
)

$ErrorActionPreference = "Stop"

if ($Version -notmatch '^(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z0-9][A-Za-z0-9._-]*)?(?:\+[0-9A-Za-z.-]+)?$') {
    throw "Invalid version format: '$Version'. Expected MAJOR.MINOR.PATCH."
}

$major = [int]$matches[1]
$minor = [int]$matches[2]
$patch = [int]$matches[3]
if ($major -gt 255 -or $minor -gt 255 -or $patch -gt 65535) {
    throw "Windows installer version out of WiX bounds: $major.$minor.$patch"
}
$revision = if ($BuildNumber -gt 0) { (($BuildNumber - 1) % 65534) + 1 } else { 0 }
$msiVersion = "$major.$minor.$patch.$revision"
$baseVersion = "$major.$minor.$patch"

if ([string]::IsNullOrWhiteSpace($OutputExe)) {
    $OutputExe = "dist/Metrolist-Setup-$baseVersion-win-x64.exe"
}

$scriptDir = $PSScriptRoot
$productWxs = Join-Path $scriptDir "Product.wxs"
$bundleWxs = Join-Path $scriptDir "Bundle.wxs"

foreach ($required in @($productWxs, $bundleWxs, (Join-Path $PayloadDir "Metrolist.exe"), $ManifestPath, $IconPath)) {
    if (-not (Test-Path $required)) {
        throw "Required installer input not found: $required"
    }
}

$resolvedWix = $null
foreach ($candidate in @($WixDir, $(if ($env:WIX) { Join-Path $env:WIX "bin" }))) {
    if ($candidate -and (Test-Path (Join-Path $candidate "light.exe"))) {
        $resolvedWix = (Resolve-Path $candidate).Path
        break
    }
}
if (-not $resolvedWix) {
    throw "WiX 3.x binaries were not found in '$WixDir' or WIX env var."
}

$resolvedPayload = (Resolve-Path $PayloadDir).Path
$resolvedManifest = (Resolve-Path $ManifestPath).Path
$resolvedIcon = (Resolve-Path $IconPath).Path
$outDir = Split-Path $OutputExe -Parent
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
if (Test-Path $WorkDir) {
    Remove-Item -Recurse -Force $WorkDir
}
$objDir = Join-Path $WorkDir "obj"
New-Item -ItemType Directory -Force -Path $objDir | Out-Null

$appFilesWxs = Join-Path $WorkDir "AppFiles.wxs"
$msiOut = Join-Path $WorkDir "Metrolist-$baseVersion-win-x64.msi"
$msiPdb = Join-Path $WorkDir "Metrolist.wixpdb"
$exePdb = Join-Path $WorkDir "Setup.wixpdb"

Write-Host "Building Metrolist Windows installer (MSI/Bundle version $msiVersion -> $OutputExe)"

& "$resolvedWix\heat.exe" dir $resolvedPayload -nologo -cg AppFiles -gg -scom -sreg -sfrag -srd -dr INSTALLFOLDER -var var.SourceDir -out $appFilesWxs
if ($LASTEXITCODE -ne 0) { throw "heat.exe failed ($LASTEXITCODE)" }

& "$resolvedWix\candle.exe" -nologo -arch x64 "-dMsiVersion=$msiVersion" "-dSourceDir=$resolvedPayload" "-dIconPath=$resolvedIcon" "-dManifestPath=$resolvedManifest" -out "$objDir\" $productWxs $appFilesWxs
if ($LASTEXITCODE -ne 0) { throw "candle.exe MSI failed ($LASTEXITCODE)" }

& "$resolvedWix\light.exe" -nologo -b $resolvedPayload -pdbout $msiPdb -out $msiOut (Join-Path $objDir "Product.wixobj") (Join-Path $objDir "AppFiles.wixobj")
if ($LASTEXITCODE -ne 0) { throw "light.exe MSI failed ($LASTEXITCODE)" }

& "$resolvedWix\candle.exe" -nologo -ext WixBalExtension "-dMsiVersion=$msiVersion" "-dIconPath=$resolvedIcon" "-dMsiPath=$msiOut" -out "$objDir\" $bundleWxs
if ($LASTEXITCODE -ne 0) { throw "candle.exe Bundle failed ($LASTEXITCODE)" }

& "$resolvedWix\light.exe" -nologo -ext WixBalExtension -pdbout $exePdb -out $OutputExe (Join-Path $objDir "Bundle.wixobj")
if ($LASTEXITCODE -ne 0) { throw "light.exe Bundle failed ($LASTEXITCODE)" }

if (-not (Test-Path $OutputExe) -or (Get-Item $OutputExe).Length -le 0) {
    throw "Installer executable was not produced at $OutputExe"
}

Get-Item $OutputExe | Select-Object FullName, Length, LastWriteTime
