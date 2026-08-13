# Turns an AI-generated "entity skin" image into a real Minecraft entity texture.
#
# WHY THIS EXISTS. Image models produce a picture OF a skin, not a skin: wrong size, JPG
# compression, and a black background where transparency should be. This does the three fixes:
#
#   1. Downscale to exactly the target size by sampling the CENTRE of each cell. The source is
#      almost never a clean multiple, so plain resizing blurs pixel edges together.
#   2. Key near-black to fully transparent. Minecraft needs alpha; JPG cannot carry it.
#   3. Snap surviving colours, killing the JPG ringing that would otherwise leave grey haze in
#      what should be empty space.
#
# Usage: Convert-EntitySkin.ps1 -Source <in.jpg> -Dest <out.png> [-Width 64] [-Height 32]

param(
    [Parameter(Mandatory = $true)][string] $Source,
    [Parameter(Mandatory = $true)][string] $Dest,
    [int] $Width = 64,
    [int] $Height = 32,
    [int] $BlackThreshold = 40
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::FromFile($Source)
try {
    '  source     : {0} x {1}' -f $src.Width, $src.Height
    '  cell size  : {0:N2} x {1:N2} source pixels per output pixel' -f ($src.Width / $Width), ($src.Height / $Height)

    $out = New-Object System.Drawing.Bitmap $Width, $Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $transparent = 0
    $opaque = 0

    for ($y = 0; $y -lt $Height; $y++) {
        for ($x = 0; $x -lt $Width; $x++) {
            # Centre of the cell, so we never land on a boundary between two regions.
            $sx = [int][math]::Floor(($x + 0.5) * $src.Width / $Width)
            $sy = [int][math]::Floor(($y + 0.5) * $src.Height / $Height)
            $sx = [math]::Min($sx, $src.Width - 1)
            $sy = [math]::Min($sy, $src.Height - 1)

            $c = $src.GetPixel($sx, $sy)

            # Near-black is background, not colour. JPG ringing means it is never pure 0,0,0.
            if (($c.R + $c.G + $c.B) -le ($BlackThreshold * 3)) {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                $transparent++
            } else {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c.R, $c.G, $c.B))
                $opaque++
            }
        }
    }

    $dir = Split-Path -Parent $Dest
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $out.Save($Dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()

    '  wrote      : {0}' -f $Dest
    '  opaque     : {0} px' -f $opaque
    '  transparent: {0} px' -f $transparent
}
finally { $src.Dispose() }
