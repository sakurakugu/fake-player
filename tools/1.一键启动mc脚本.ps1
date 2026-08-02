param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$task = "runClient"
$javaPath = $null
$javaRoot = "C:\Software\Deps\Java"

for ($index = 0; $index -lt $Arguments.Count; $index++) {
    switch ($Arguments[$index]) {
        "--build" { $task = "build" }
        "--neoforge" { $task = "runClient" }
        "--path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --path 缺少 Java 路径。"
            }

            $index++
            $javaPath = $Arguments[$index]
        }
        default {
            throw "不支持的参数 '$($Arguments[$index])'。请使用 --build、--neoforge 或 --path。"
        }
    }
}

Set-Location "$PSScriptRoot\.."

function Get-JavaInstallation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaHome
    )

    $javaExecutable = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        return $null
    }

    $versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    if ($versionOutput -match 'version\s+"1\.(\d+)') {
        $majorVersion = [int]$Matches[1]
    } elseif ($versionOutput -match 'version\s+"(\d+)') {
        $majorVersion = [int]$Matches[1]
    } else {
        return $null
    }

    [PSCustomObject]@{
        Home = [System.IO.Path]::GetFullPath($JavaHome)
        MajorVersion = $majorVersion
    }
}

$javaInstallations = @()
if (Test-Path -LiteralPath $javaRoot -PathType Container) {
    $javaInstallations = @(Get-ChildItem -LiteralPath $javaRoot -Directory | ForEach-Object {
        Get-JavaInstallation -JavaHome $_.FullName
    } | Where-Object { $null -ne $_ })
}

if ($null -eq $javaPath) {
    $buildFile = Join-Path (Get-Location) "build.gradle"
    $buildContent = Get-Content -LiteralPath $buildFile -Raw
    if ($buildContent -notmatch 'JavaLanguageVersion\.of\(\s*(\d+)\s*\)') {
        throw "无法从 build.gradle 识别 Java toolchain 版本。"
    }

    $toolchainVersion = [int]$Matches[1]
    $selectedJava = $javaInstallations |
        Where-Object { $_.MajorVersion -eq $toolchainVersion } |
        Sort-Object Home -Descending |
        Select-Object -First 1

    if ($null -eq $selectedJava) {
        throw "当前版本需要 Java $toolchainVersion，但在 '$javaRoot' 中未找到对应 JDK。可使用 --path 指定 Java 路径。"
    }

    $javaPath = $selectedJava.Home
}

$javaHome = [System.IO.Path]::GetFullPath($javaPath)
if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
    throw "指定的 Java 路径无效：'$javaPath'。未找到 bin\java.exe。"
}

# 启用仓库内的 Git hooks。
git config core.hooksPath .githooks

# 设置 Gradle 使用的 Java 环境。
$env:JAVA_HOME = $javaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$javaInstallationPaths = (@($javaInstallations.Home) + $javaHome | Select-Object -Unique) -join ','

Write-Host "使用 Java：$javaHome"

if ($task -eq "build") {
    # 清理旧输出后执行测试和构建。
    .\gradlew.bat clean build "-Porg.gradle.java.installations.paths=$javaInstallationPaths"
} else {
    # 启动 NeoForge 开发客户端。
    .\gradlew.bat runClient "-Porg.gradle.java.installations.paths=$javaInstallationPaths"
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
