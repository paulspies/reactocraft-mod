# Rebuilds a cow texture by moving each block of an AI-generated sheet into the rectangle the model
# actually reads it from.
#
# 🚨 WHY A PLAIN DOWNSCALE IS NOT ENOUGH. Measuring Paul's best ChatGPT attempt against the real UV
# showed the error is NOT a uniform shift, so no single offset or scale corrects it:
#
#     head top     his y0-6    real y0-5      one row too tall
#     face row     his y7-14   real y6-13     one row too low
#     body sides   his y16-31  real y14-31    two rows too low
#     legs         his y20-31  real y16-31    FOUR rows too low
#     horns        his x34-39  real x22-25    twelve columns too far right
#
# So each block is sampled from where the art put it and written to where the game wants it, scaling
# to fit. Distortion within a block is invisible; a block in the wrong place is not.
#
# ⚠️ The SOURCE rectangles below were measured from one specific image by dumping its alpha as an
# occupancy map. A new generation will land differently. Re-measure before trusting them.

param(
    [Parameter(Mandatory = $true)][string] $Source
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'src\main\resources\assets\reactocraft\textures\entity\irradiated_cow.png'

# from = where the art put it, in 64x32 space. to = where the cow model reads it.
$moves = @(
    @{ n = 'head top+under'; fx1 = 8;  fy1 = 0;  fx2 = 23; fy2 = 6;  tx1 = 6;  ty1 = 0;  tx2 = 21; ty2 = 5 }
    @{ n = 'head faces';     fx1 = 0;  fy1 = 7;  fx2 = 27; fy2 = 14; tx1 = 0;  ty1 = 6;  tx2 = 27; ty2 = 13 }
    @{ n = 'horns';          fx1 = 34; fy1 = 1;  fx2 = 39; fy2 = 3;  tx1 = 22; ty1 = 0;  tx2 = 25; ty2 = 3 }
    @{ n = 'udder';          fx1 = 53; fy1 = 0;  fx2 = 62; fy2 = 5;  tx1 = 52; ty1 = 0;  tx2 = 61; ty2 = 6 }
    @{ n = 'body top+under'; fx1 = 28; fy1 = 7;  fx2 = 51; fy2 = 14; tx1 = 28; ty1 = 4;  tx2 = 51; ty2 = 13 }
    @{ n = 'body sides';     fx1 = 18; fy1 = 16; fx2 = 61; fy2 = 31; tx1 = 18; ty1 = 14; tx2 = 61; ty2 = 31 }
    @{ n = 'leg caps';       fx1 = 4;  fy1 = 16; fx2 = 14; fy2 = 18; tx1 = 4;  ty1 = 16; tx2 = 11; ty2 = 19 }
    @{ n = 'legs';           fx1 = 0;  fy1 = 20; fx2 = 15; fy2 = 31; tx1 = 0;  ty1 = 20; tx2 = 15; ty2 = 31 }
)

$src = [System.Drawing.Bitmap]::FromFile($Source)
try {
    '  source : {0} x {1}' -f $src.Width, $src.Height

    # Sample the source down to 64x32 first, so every rectangle below is in the same coordinates.
    $flat = New-Object 'System.Drawing.Color[,]' 64, 32
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 64; $x++) {
            $sx = [math]::Min([int][math]::Floor(($x + 0.5) * $src.Width / 64), $src.Width - 1)
            $sy = [math]::Min([int][math]::Floor(($y + 0.5) * $src.Height / 32), $src.Height - 1)
            $flat[$x, $y] = $src.GetPixel($sx, $sy)
        }
    }

    $out = New-Object System.Drawing.Bitmap 64, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    foreach ($m in $moves) {
        $fw = $m.fx2 - $m.fx1 + 1
        $fh = $m.fy2 - $m.fy1 + 1
        $tw = $m.tx2 - $m.tx1 + 1
        $th = $m.ty2 - $m.ty1 + 1

        for ($ty = 0; $ty -lt $th; $ty++) {
            for ($tx = 0; $tx -lt $tw; $tx++) {
                # Nearest-neighbour scale from the source rectangle into the target rectangle.
                $sx = $m.fx1 + [int][math]::Floor(($tx + 0.5) * $fw / $tw)
                $sy = $m.fy1 + [int][math]::Floor(($ty + 0.5) * $fh / $th)
                $sx = [math]::Max(0, [math]::Min(63, $sx))
                $sy = [math]::Max(0, [math]::Min(31, $sy))
                $c = $flat[$sx, $sy]
                if ($c.A -lt 128) { continue }
                $out.SetPixel($m.tx1 + $tx, $m.ty1 + $ty,
                    [System.Drawing.Color]::FromArgb(255, $c.R, $c.G, $c.B))
            }
        }
        '  moved  : {0,-16} {1}x{2} -> {3}x{4}' -f $m.n, $fw, $fh, $tw, $th
    }

    $out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    '  wrote  : {0}' -f (Split-Path $dest -Leaf)
}
finally { $src.Dispose() }
