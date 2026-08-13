# Builds the irradiated cow entity texture, 64x32, by painting each body part into its CORRECT
# rectangle on the UV sheet.
#
# 🚨 WHY THE FIRST ATTEMPT FAILED. Paul generated a lovely image in ChatGPT and it was the right
# size with the right palette, but the regions were in the wrong places, so in game the face landed
# on the side of the body. An entity texture is not a picture of a cow, it is the cow's boxes
# unwrapped flat, and every face has an exact home.
#
# THE REGIONS, derived from CowModel's own box offsets, not guessed:
#
#   head   texOffs(0,0)   8w 8h 6d   -> x0..27  y0..13
#            top    x6..13  y0..5      bottom x14..21 y0..5
#            right  x0..5   y6..13     FACE   x6..13  y6..13
#            left   x14..19 y6..13     back   x20..27 y6..13
#   horns  texOffs(22,0)  1w 3h 1d   -> x22..25 y0..3
#   udder  texOffs(52,0)  4w 6h 1d   -> x52..61 y0..6
#   body   texOffs(18,4)  12w 18h 10d-> x18..61 y4..31
#            top    x28..39 y4..13     bottom x40..51 y4..13
#            right  x18..27 y14..31    front  x28..39 y14..31
#            left   x40..49 y14..31    back   x50..61 y14..31
#   legs   texOffs(0,16)  4w 12h 4d  -> x0..15  y16..31   (all four share this)
#
# ⚠️ Anything outside those rectangles is never drawn by the game. Painting there is wasted, and
# leaving a rectangle empty shows as a missing-texture hole on the model.
#
# Palette is sampled from Paul's own generated art, so the look stays his.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'src\main\resources\assets\reactocraft\textures\entity\irradiated_cow.png'

# --- palette, taken from Paul's image -------------------------------------------------------------
$HIDE_DARK  = @( 74,  70,  40)
$HIDE       = @(102,  99,  52)
$HIDE_LIGHT = @(128, 124,  74)
$GREEN      = @(140, 220,  40)
$GREEN_DIM  = @( 96, 150,  30)
$SORE       = @(104,  36,  36)
$PINK       = @(238, 158, 158)
$HORN       = @( 60,  56,  44)
$EYE        = @( 24,  24,  20)
$HOOF       = @( 44,  42,  32)

$bmp = New-Object System.Drawing.Bitmap 64, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rng = New-Object System.Random 20260813   # fixed seed, so regenerating gives the same cow

function Px($x, $y, $c) {
    if ($x -lt 0 -or $y -lt 0 -or $x -gt 63 -or $y -gt 31) { return }
    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
}

function Fill($x1, $y1, $x2, $y2, $c) {
    for ($y = $y1; $y -le $y2; $y++) { for ($x = $x1; $x -le $x2; $x++) { Px $x $y $c } }
}

# Mottled hide: a base with scattered lighter and darker pixels, so it does not read as flat paint.
function Hide($x1, $y1, $x2, $y2) {
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) {
            $r = $rng.NextDouble()
            $c = if ($r -lt 0.18) { $HIDE_DARK } elseif ($r -lt 0.34) { $HIDE_LIGHT } else { $HIDE }
            Px $x $y $c
        }
    }
}

# Scatter glowing patches and sores over an area already covered in hide.
function Blight($x1, $y1, $x2, $y2, $greenChance, $soreChance) {
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) {
            $r = $rng.NextDouble()
            if ($r -lt $greenChance) {
                Px $x $y $(if ($rng.NextDouble() -lt 0.4) { $GREEN } else { $GREEN_DIM })
            } elseif ($r -lt $greenChance + $soreChance) {
                Px $x $y $SORE
            }
        }
    }
}

# --- HEAD, x0..27 y0..13 --------------------------------------------------------------------------
Hide 6 0 13 5        # top of the head
Hide 14 0 21 5       # underside of the head
Hide 0 6 5 13        # right cheek
Hide 6 6 13 13       # THE FACE
Hide 14 6 19 13      # left cheek
Hide 20 6 27 13      # back of the head
Blight 0 0 27 13 0.10 0.05

# The face, drawn deliberately rather than scattered.
Fill 8 10 11 13 $PINK      # muzzle, low and central
Px 8 12 $EYE; Px 11 12 $EYE   # nostrils sit in the muzzle
Fill 6 7 7 8 $EYE          # right eye
Fill 12 7 13 8 $EYE        # left eye
Px 7 7 $GREEN; Px 12 7 $GREEN  # a sick glow in each eye

# --- HORNS, x22..25 y0..3 -------------------------------------------------------------------------
Fill 22 0 25 3 $HORN

# --- UDDER, x52..61 y0..6 -------------------------------------------------------------------------
Fill 52 0 61 6 $PINK
Blight 52 0 61 6 0.10 0.06

# --- BODY, x18..61 y4..31 -------------------------------------------------------------------------
Hide 28 4 39 13      # top, the part you see from above
Hide 40 4 51 13      # underside
Hide 18 14 27 31     # right flank
Hide 28 14 39 31     # front
Hide 40 14 49 31     # left flank
Hide 50 14 61 31     # back
Blight 18 4 61 31 0.13 0.06

# A biohazard-ish mark across the top of the body, the bit visible from above.
$mark = @(
    '....######....'
    '...##....##...'
    '..##..##..##..'
    '..##.####.##..'
    '..##..##..##..'
    '...##....##...'
    '....######....'
)
for ($i = 0; $i -lt $mark.Count; $i++) {
    $line = $mark[$i]
    for ($j = 0; $j -lt $line.Length; $j++) {
        if ($line[$j] -eq '#') { Px (28 + $j - 1) (5 + $i) $GREEN }
    }
}

# --- LEGS, x0..15 y16..31 -------------------------------------------------------------------------
Hide 4 16 11 19      # top and bottom caps
Hide 0 20 15 31      # the four sides
Blight 0 16 15 31 0.10 0.05
Fill 0 29 15 31 $HOOF   # darker toward the hoof

$bmp.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
'  wrote {0}  {1} bytes' -f (Split-Path $dest -Leaf), (Get-Item $dest).Length
