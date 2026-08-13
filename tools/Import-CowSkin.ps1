# Turns an AI-generated cow skin into a real 64x32 Minecraft entity texture.
#
# Three steps, and each one exists because a previous attempt failed on it:
#
#   1. DOWNSCALE by sampling the CENTRE of each cell. Sources are never a clean multiple of 64x32,
#      Paul's are 1774x887 which is 27.72 real pixels per texture pixel. Ordinary resizing blends
#      neighbouring regions together and turns pixel art to mush.
#   2. RESPECT ALPHA if the source has it, and fall back to keying near-black if it does not. The
#      first attempt was a JPG, which cannot carry transparency at all and used black instead.
#   3. MASK to the vanilla cow's UV footprint, so stray pixels outside the real regions are dropped
#      and any legal pixel the art missed gets filled rather than left as a hole.
#
# The mask reads only the ALPHA CHANNEL of the vanilla texture, which is geometry, not artwork.
# No Mojang colour is read and nothing of theirs is shipped.
#
# Usage: Import-CowSkin.ps1 -Source "C:\path\to\chatgpt.png"

param(
    [Parameter(Mandatory = $true)][string] $Source,
    [int] $BlackThreshold = 40
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'src\main\resources\assets\reactocraft\textures\entity\irradiated_cow.png'

# --- pull the vanilla cow purely for its alpha mask ---------------------------------------------
$vanillaJar = "C:\Users\Paul's PC2\AppData\Roaming\.minecraft\versions\1.21.1\1.21.1.jar"
$maskPath = Join-Path $env:TEMP 'vanilla_cow_mask.png'
if (-not (Test-Path $maskPath)) {
    $zip = [IO.Compression.ZipFile]::OpenRead($vanillaJar)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/minecraft/textures/entity/cow/cow.png' }
        if (-not $entry) { throw 'vanilla cow texture not found in the client jar' }
        $fs = [IO.File]::Create($maskPath)
        $entry.Open().CopyTo($fs)
        $fs.Close()
    } finally { $zip.Dispose() }
}

$src = [System.Drawing.Bitmap]::FromFile($Source)
$mask = [System.Drawing.Bitmap]::FromFile($maskPath)
try {
    '  source     : {0} x {1}' -f $src.Width, $src.Height
    '  cell       : {0:N2} source pixels per texture pixel' -f ($src.Width / 64)

    # Does the source carry real transparency, or is it using black as a background?
    $hasAlpha = $false
    for ($y = 0; $y -lt $src.Height -and -not $hasAlpha; $y += 20) {
        for ($x = 0; $x -lt $src.Width; $x += 20) {
            if ($src.GetPixel($x, $y).A -lt 200) { $hasAlpha = $true; break }
        }
    }
    '  alpha      : {0}' -f $(if ($hasAlpha) { 'yes, using it' } else { 'none, keying near-black instead' })

    $out = New-Object System.Drawing.Bitmap 64, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $kept = 0; $dropped = 0; $filled = 0

    # Average colour of the art, used to fill legal pixels the source left empty.
    $rs = 0; $gs = 0; $bs = 0; $n = 0

    $sampled = New-Object 'System.Drawing.Color[,]' 64, 32
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 64; $x++) {
            $sx = [math]::Min([int][math]::Floor(($x + 0.5) * $src.Width / 64), $src.Width - 1)
            $sy = [math]::Min([int][math]::Floor(($y + 0.5) * $src.Height / 32), $src.Height - 1)
            $c = $src.GetPixel($sx, $sy)
            $blank = if ($hasAlpha) { $c.A -lt 128 } else { ($c.R + $c.G + $c.B) -le ($BlackThreshold * 3) }
            if ($blank) {
                $sampled[$x, $y] = [System.Drawing.Color]::Transparent
            } else {
                $sampled[$x, $y] = $c
                $rs += $c.R; $gs += $c.G; $bs += $c.B; $n++
            }
        }
    }
    if ($n -eq 0) { throw 'the source appears to be entirely blank' }
    $avg = [System.Drawing.Color]::FromArgb(255, [int]($rs / $n), [int]($gs / $n), [int]($bs / $n))

    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 64; $x++) {
            $legal = $mask.GetPixel($x, $y).A -ge 8
            $mine = $sampled[$x, $y]
            if (-not $legal) {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                if ($mine.A -gt 8) { $dropped++ }
            } elseif ($mine.A -gt 8) {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $mine.R, $mine.G, $mine.B))
                $kept++
            } else {
                # A legal pixel the art left blank. Filling with the global average makes large flat
                # patches that look painted on, so take the NEAREST opaque pixel from the art
                # instead. The regions are only slightly misaligned, so the right colour is usually
                # a pixel or two away and the mottling carries across.
                $fill = $avg
                :search for ($rad = 1; $rad -le 6; $rad++) {
                    for ($dy = -$rad; $dy -le $rad; $dy++) {
                        for ($dx = -$rad; $dx -le $rad; $dx++) {
                            $nx = $x + $dx; $ny = $y + $dy
                            if ($nx -lt 0 -or $ny -lt 0 -or $nx -gt 63 -or $ny -gt 31) { continue }
                            $near = $sampled[$nx, $ny]
                            if ($near.A -gt 8) { $fill = $near; break search }
                        }
                    }
                }
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $fill.R, $fill.G, $fill.B))
                $filled++
            }
        }
    }

    $out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()

    '  kept       : {0} px from the art' -f $kept
    '  dropped    : {0} px outside the real UV regions' -f $dropped
    '  filled     : {0} px the art left blank inside a real region' -f $filled
    '  wrote      : {0}' -f $dest
}
finally {
    $src.Dispose()
    $mask.Dispose()
}
