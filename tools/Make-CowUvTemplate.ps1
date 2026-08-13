# Draws a labelled guide showing which rectangle of a 64x32 cow texture the game reads each body
# part from. Rendered at 16x so the labels are readable.
#
# WHY THIS EXISTS. Paul's AI-generated cow textures came back the right size, the right palette and
# with real transparency, but with every region in the wrong place, so the cow wore its udder on its
# back. An entity texture is the model's boxes unwrapped flat. Colouring is the easy half; landing
# each face in its exact rectangle is the half that keeps going wrong.
#
# Regions are derived from CowModel's own box offsets, not guessed:
#   head  texOffs(0,0)  8w 8h 6d · horns texOffs(22,0) 1w 3h 1d
#   body  texOffs(18,4) 12w 18h 10d · legs texOffs(0,16) 4w 12h 4d
#   udder texOffs(52,0) 4w 6h 1d
#
# Standard box UV layout, for any part: across the top row sit TOP then BOTTOM, each width x depth.
# The row below holds RIGHT, FRONT, LEFT, BACK, each height tall.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$S = 16   # zoom
$out = "D:\Paul's PC2\Desktop\Temp Screenshots\COW-UV-TEMPLATE.png"

$regions = @(
    @{ n = 'HEAD top';   x1 = 6;  y1 = 0;  x2 = 13; y2 = 5;  r = 70;  g = 110; b = 170 }
    @{ n = 'HEAD under'; x1 = 14; y1 = 0;  x2 = 21; y2 = 5;  r = 55;  g = 88;  b = 140 }
    @{ n = 'cheek R';    x1 = 0;  y1 = 6;  x2 = 5;  y2 = 13; r = 60;  g = 95;  b = 150 }
    @{ n = '** FACE **'; x1 = 6;  y1 = 6;  x2 = 13; y2 = 13; r = 200; g = 90;  b = 90 }
    @{ n = 'cheek L';    x1 = 14; y1 = 6;  x2 = 19; y2 = 13; r = 60;  g = 95;  b = 150 }
    @{ n = 'HEAD back';  x1 = 20; y1 = 6;  x2 = 27; y2 = 13; r = 55;  g = 88;  b = 140 }
    @{ n = 'HORNS';      x1 = 22; y1 = 0;  x2 = 25; y2 = 3;  r = 150; g = 120; b = 60 }
    @{ n = 'UDDER';      x1 = 52; y1 = 0;  x2 = 61; y2 = 6;  r = 190; g = 120; b = 170 }
    @{ n = 'BODY top';   x1 = 28; y1 = 4;  x2 = 39; y2 = 13; r = 70;  g = 150; b = 90 }
    @{ n = 'BODY under'; x1 = 40; y1 = 4;  x2 = 51; y2 = 13; r = 55;  g = 120; b = 72 }
    @{ n = 'flank R';    x1 = 18; y1 = 14; x2 = 27; y2 = 31; r = 60;  g = 135; b = 80 }
    @{ n = 'BODY front'; x1 = 28; y1 = 14; x2 = 39; y2 = 31; r = 70;  g = 150; b = 90 }
    @{ n = 'flank L';    x1 = 40; y1 = 14; x2 = 49; y2 = 31; r = 60;  g = 135; b = 80 }
    @{ n = 'BODY back';  x1 = 50; y1 = 14; x2 = 61; y2 = 31; r = 55;  g = 120; b = 72 }
    @{ n = 'leg caps';   x1 = 4;  y1 = 16; x2 = 11; y2 = 19; r = 150; g = 110; b = 50 }
    @{ n = 'LEGS x4';    x1 = 0;  y1 = 20; x2 = 15; y2 = 31; r = 170; g = 125; b = 55 }
)

$bmp = New-Object System.Drawing.Bitmap (64 * $S), (32 * $S)
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.Clear([System.Drawing.Color]::FromArgb(255, 28, 28, 32))
$gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

$label = New-Object System.Drawing.Font 'Consolas', ($S * 0.55)
$white = [System.Drawing.Brushes]::White
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), 2

foreach ($reg in $regions) {
    $colour = [System.Drawing.Color]::FromArgb(215, [int]$reg.r, [int]$reg.g, [int]$reg.b)
    $brush = New-Object System.Drawing.SolidBrush $colour
    $w = ($reg.x2 - $reg.x1 + 1) * $S
    $h = ($reg.y2 - $reg.y1 + 1) * $S
    $gfx.FillRectangle($brush, ($reg.x1 * $S), ($reg.y1 * $S), $w, $h)
    $gfx.DrawRectangle($pen, ($reg.x1 * $S), ($reg.y1 * $S), $w, $h)
    $gfx.DrawString($reg.n, $label, $white, ($reg.x1 * $S + 4), ($reg.y1 * $S + 3))
    $coords = 'x{0}-{1} y{2}-{3}' -f $reg.x1, $reg.x2, $reg.y1, $reg.y2
    $gfx.DrawString($coords, $label, $white, ($reg.x1 * $S + 4), ($reg.y1 * $S + 3 + $S * 0.62))
    $brush.Dispose()
}

$pen.Dispose()
$gfx.Dispose()

$dir = Split-Path $out
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
'  wrote {0}' -f $out
