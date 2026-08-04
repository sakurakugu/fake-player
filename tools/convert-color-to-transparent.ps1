<#
.SYNOPSIS
将图片中指定的 RGB 颜色转换为透明色。

.EXAMPLE
.\convert-color-to-transparent.ps1 .\input.png 00A2E8

.EXAMPLE
.\convert-color-to-transparent.ps1 .\input.png -Color "#FF00FF"

.NOTES
在 PowerShell 中，未加引号的 # 会开始一段注释。建议直接使用 RRGGBB，
或者给 #RRGGBB 加引号。
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Path,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Color
)

$ErrorActionPreference = "Stop"

if ($Color -notmatch '^(?:#|0[xX])?([0-9A-Fa-f]{6})$') {
    throw '颜色必须使用 RRGGBB、0xRRGGBB 或带引号的 "#RRGGBB" 格式，例如 00A2E8。'
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
    if ($changedPixels -eq 0) {
        Write-Warning "图片中没有找到颜色 #$hexColor 的精确匹配像素。"
    }
    Write-Output "已将 $changedPixels 个 #$hexColor 像素设为透明：$outputPath"
} finally {
    if ($null -ne $result) {
        $result.Dispose()
    }
    if ($null -ne $source) {
        $source.Dispose()
    }
}
