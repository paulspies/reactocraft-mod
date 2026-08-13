# Generates the two chocolate milk item textures, 16x16 PNG with transparency.
#
# WHY A SCRIPT AND NOT AN IMAGE EDITOR. At 16x16 every pixel is a deliberate decision, and a
# generated image can be regenerated and tweaked by changing one character in the maps below.
# It is also the honest route for a PUBLIC repo: this is original art in the vanilla idiom, not
# Mojang's PNG recoloured, which would not be ours to redistribute.
#
# ⚠️ ChatGPT and friends cannot do this job. They produce a beautiful large image that turns to
# mush at 16 pixels and has no transparency. Same lesson as the mob effect icons.
#
# Run from anywhere. Writes into src/main/resources/assets/reactocraft/textures/item/.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$dir  = Join-Path $root 'src\main\resources\assets\reactocraft\textures\item'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# ⚠️ PowerShell hash keys are CASE-INSENSITIVE, so 'L' and 'l' are the same key and the literal
# will not even parse. Every symbol below has to be distinct ignoring case.
#
# . transparent   o outline   m metal   # metal highlight
# L liquid        + liquid highlight    - liquid shadow
# g glass         c cork
$palette = @{
    '.' = $null
    'o' = @(58, 58, 58)
    'm' = @(154, 154, 154)
    '#' = @(198, 198, 198)
    'L' = @(107, 62, 31)
    '+' = @(139, 90, 43)
    '-' = @(74, 42, 20)
    'g' = @(190, 205, 200)
    'c' = @(154, 92, 48)
}

# Bucket: vanilla's shape is a tapered tub with a flat liquid surface near the rim.
$bucket = @(
    '................'
    '................'
    '..o..........o..'
    '..o..........o..'
    '..oooooooooooo..'
    '..o++++++++Lo...'
    '..oLLLLLLLLLo...'
    '..o#mmmmmmmmo...'
    '..o#mmmmmmmmo...'
    '...o#mmmmmmo....'
    '...o#mmmmmmo....'
    '...o#mmmmmmo....'
    '....o#mmmmo.....'
    '....oooooooo....'
    '................'
    '................'
)

# Bottle: glass neck and shoulders, liquid filling the belly.
$bottle = @(
    '................'
    '.......oo.......'
    '......occo......'
    '......occo......'
    '......oggo......'
    '......oggo......'
    '.....oLLLLo.....'
    '....oLLLLLLo....'
    '...o+LLLLLLLo...'
    '...oLLLLLLLLo...'
    '...oLLLLLLLLo...'
    '...oLLLLLLLLo...'
    '...o-LLLLLL-o...'
    '...o--------o...'
    '....oooooooo....'
    '................'
)

function Write-Sprite($rows, $path) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        $line = $rows[$y]
        for ($x = 0; $x -lt 16; $x++) {
            $ch = if ($x -lt $line.Length) { $line[$x].ToString() } else { '.' }
            if (-not $palette.ContainsKey($ch)) { throw "unknown palette char '$ch' at $x,$y in $path" }
            $rgb = $palette[$ch]
            if ($null -eq $rgb) { continue }
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
        }
    }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    '  {0}  {1} bytes' -f (Split-Path $path -Leaf), (Get-Item $path).Length
}

Write-Sprite $bucket (Join-Path $dir 'chocolate_milk_bucket.png')
Write-Sprite $bottle (Join-Path $dir 'chocolate_milk_bottle.png')
