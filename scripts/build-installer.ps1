# Builds a Windows app-image (jpackage) and optionally a polished Inno Setup installer.
#
# Prerequisites:
#   - JDK 17+ with jpackage (JDK 24 is fine)
#   - Maven
#   - Optional: Inno Setup 6 (iscc.exe on PATH) for GravityChunk-Setup.exe
#
# Usage:
#   .\scripts\build-installer.ps1
#   .\scripts\build-installer.ps1 -SkipInno

param(
    [switch]$SkipInno,
    [string]$AppVersion = "1.0.0",
    [string]$AppName = "GravityChunk"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaHome = (Get-Command java).Source | Split-Path | Split-Path
}
$jpackage = Join-Path $javaHome "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    Write-Error "jpackage not found at $jpackage. Set JAVA_HOME to a JDK that includes jpackage."
}

Write-Host "==> mvn package"
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jarName = "gravitychunk-1.0-SNAPSHOT.jar"
$jarPath = Join-Path $Root "target\$jarName"
if (-not (Test-Path $jarPath)) {
    Write-Error "Expected jar not found: $jarPath"
}

$dist = Join-Path $Root "dist"
$stage = Join-Path $dist "jpackage-input"
$appImageDest = Join-Path $dist "app-image"
$installerOut = Join-Path $dist "installer"

Remove-Item -Recurse -Force $stage, $appImageDest -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage, $installerOut | Out-Null
Copy-Item $jarPath (Join-Path $stage $jarName)

Write-Host "==> jpackage app-image"
& $jpackage `
    --type app-image `
    --name $AppName `
    --app-version $AppVersion `
    --input $stage `
    --main-jar $jarName `
    --main-class com.grumbo.Main `
    --dest $appImageDest `
    --java-options "-Xms512m" `
    --java-options "-Xmx8g" `
    --description "Real-time Barnes-Hut N-body simulation (OpenGL compute shaders)" `
    --vendor "Grumbo" `
    --win-console

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$appDir = Join-Path $appImageDest $AppName
Write-Host "App image: $appDir"

$iscc = Get-Command iscc -ErrorAction SilentlyContinue
if ($SkipInno) {
    Write-Host "Skipping Inno Setup (-SkipInno)."
    exit 0
}
if (-not $iscc) {
    Write-Warning "Inno Setup (iscc) not on PATH. App-image is ready at $appDir"
    Write-Warning "Install Inno Setup 6 and re-run without -SkipInno for GravityChunk-Setup.exe"
    exit 0
}

$iss = Join-Path $Root "installer\gravity.iss"
if (-not (Test-Path $iss)) {
    Write-Error "Missing $iss"
}

Write-Host "==> Inno Setup"
& $iscc.Source `
    "/DAppVersion=$AppVersion" `
    "/DAppName=$AppName" `
    "/DAppImageDir=$appDir" `
    "/DOutputDir=$installerOut" `
    $iss

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Installer output: $installerOut"
