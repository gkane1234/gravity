# Encode a FrameRecorder PNG sequence into MP4 and WebM for the website.
#
# FrameRecorder names files like: frame_20260721_103045_000000.png
#
# Usage:
#   .\scripts\encode-capture.ps1 -CaptureDir captures\rec_YYYYMMDD_HHMMSS
#   .\scripts\encode-capture.ps1 -CaptureDir captures\rec_... -Name galaxy-merger -Fps 60
#
# Requires ffmpeg on PATH: https://ffmpeg.org/download.html

param(
    [Parameter(Mandatory = $true)]
    [string]$CaptureDir,

    [string]$Name = "",
    [int]$Fps = 60,
    [string]$OutDir = "website/public/videos"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Error "ffmpeg not found on PATH. Install it, then re-run."
}

$CaptureDir = (Resolve-Path $CaptureDir).Path
$frames = @(Get-ChildItem -Path $CaptureDir -Filter "frame_*.png" | Sort-Object Name)
if ($frames.Count -eq 0) {
    Write-Error "No frame_*.png files found in $CaptureDir"
}

if ([string]::IsNullOrWhiteSpace($Name)) {
    $Name = Split-Path $CaptureDir -Leaf
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$mp4 = Join-Path $OutDir "$Name.mp4"
$webm = Join-Path $OutDir "$Name.webm"

# Stage to a contiguous %06d sequence (recorder embeds timestamps in filenames).
$stage = Join-Path $env:TEMP ("gravitychunk_encode_" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
try {
    for ($i = 0; $i -lt $frames.Count; $i++) {
        $dest = Join-Path $stage ("frame_{0:D6}.png" -f $i)
        Copy-Item $frames[$i].FullName $dest
    }

    $pattern = Join-Path $stage "frame_%06d.png"

    Write-Host "Encoding $($frames.Count) frames @ ${Fps}fps -> $mp4"
    ffmpeg -y -framerate $Fps -i $pattern -c:v libx264 -pix_fmt yuv420p -crf 18 -movflags +faststart $mp4
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "Encoding WebM -> $webm"
    ffmpeg -y -framerate $Fps -i $pattern -c:v libvpx-vp9 -b:v 0 -crf 32 -an $webm
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "Done."
    Write-Host "  $mp4"
    Write-Host "  $webm"
}
finally {
    Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}
