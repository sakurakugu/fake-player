param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Path,

    [Parameter(Position = 1)]
    [string]$Color = "#00A2E8"
)

$ErrorActionPreference = "Stop"

if ($Color -notmatch '^#?([0-9A-Fa-f]{6})$') {
    throw "颜色必须使用 RRGGBB 或 #RRGGBB 格式，例如 #00A2E8。"
}

$hexColor = $Matches[1]
$red = [Convert]::ToByte($hexColor.Substring(0, 2), 16)
$green = [Convert]::ToByte($hexColor.Substring(2, 2), 16)
$blue = [Convert]::ToByte($hexColor.Substring(4, 2), 16)
$inputPath = (Resolve-Path -LiteralPath $Path).Path
$directory = [System.IO.Path]::GetDirectoryName($inputPath)
$fileName = [System.IO.Path]::GetFileNameWithoutExtension($inputPath)
$outputPath = [System.IO.Path]::Combine($directory, "$fileName.transparent.png")

Add-Type -AssemblyName System.Drawing

$source = $null
$result = $null
try {
    $source = [System.Drawing.Bitmap]::FromFile($inputPath)
    $result = [System.Drawing.Bitmap]::new(
        $source.Width,
        $source.Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )

    $changedPixels = 0
    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            $pixel = $source.GetPixel($x, $y)
            if ($pixel.R -eq $red -and $pixel.G -eq $green -and $pixel.B -eq $blue) {
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, $red, $green, $blue))
                $changedPixels++
            } else {
                $result.SetPixel($x, $y, $pixel)
            }
        }
    }

    $result.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "已将 $changedPixels 个像素设为透明：$outputPath"
} finally {
    if ($null -ne $result) {
        $result.Dispose()
    }
    if ($null -ne $source) {
        $source.Dispose()
    }
}
