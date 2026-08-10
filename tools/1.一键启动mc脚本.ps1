param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$task = "runClient"
$launchBoth = $false
$neoforgeRequested = $false
$javaPath = $null

for ($index = 0; $index -lt $Arguments.Count; $index++) {
    switch ($Arguments[$index]) {
        "--build" { $task = "build" }
        "--neoforge" {
            $task = "runClient"
            $neoforgeRequested = $true
        }
        "--both" { $launchBoth = $true }
        "--path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --path 缺少 Java 路径。"
            }

            $index++
            $javaPath = $Arguments[$index]
        }
        default {
            throw "不支持的参数 '$($Arguments[$index])'。请使用 --build、--neoforge、--both 或 --path。"
        }
    }
}

if ($launchBoth -and -not $neoforgeRequested) {
    throw "参数 --both 必须与 --neoforge 一起使用。示例：--neoforge --both"
}

Set-Location "$PSScriptRoot\.."

function Set-DevelopmentServerProperties {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PropertiesPath
    )

    # 开发客户端使用离线身份，LAN 服务端不能强制校验 Mojang 会话或安全档案。
    $propertyLines = if (Test-Path -LiteralPath $PropertiesPath -PathType Leaf) {
        @(Get-Content -LiteralPath $PropertiesPath)
    } else {
        @()
    }

    $updatedLines = [System.Collections.Generic.List[string]]::new()
    $seenOnlineMode = $false
    $seenSecureProfile = $false
    $seenAllowCheats = $false
    foreach ($line in $propertyLines) {
        if ($line -match '^online-mode=') {
            $updatedLines.Add('online-mode=false')
            $seenOnlineMode = $true
        } elseif ($line -match '^enforce-secure-profile=') {
            $updatedLines.Add('enforce-secure-profile=false')
            $seenSecureProfile = $true
        } elseif ($line -match '^allow-cheats=') {
            $updatedLines.Add('allow-cheats=true')
            $seenAllowCheats = $true
        } else {
            $updatedLines.Add($line)
        }
    }

    if (-not $seenOnlineMode) {
        $updatedLines.Add('online-mode=false')
    }
    if (-not $seenSecureProfile) {
        $updatedLines.Add('enforce-secure-profile=false')
    }
    if (-not $seenAllowCheats) {
        $updatedLines.Add('allow-cheats=true')
    }

    Set-Content -LiteralPath $PropertiesPath -Value $updatedLines -Encoding UTF8
}

if ($null -ne $javaPath) {
    $javaHome = [System.IO.Path]::GetFullPath($javaPath)
    if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
        throw "指定的 Java 路径无效：'$javaPath'。未找到 bin\java.exe。"
    }
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

# 启用仓库内的 Git hooks。
git config core.hooksPath .githooks

# 设置环境变量。
Write-Host "Gradle 将通过 Toolchain 自动检测或下载所需 Java。"

if ($task -eq "build") {
    # 清理旧输出后执行测试和构建。
    .\gradlew.bat clean build
} else {
    if ($launchBoth) {
        # 启动离线专用服务端，再启动两个自动连接的客户端。
        $rootPath = (Get-Location).Path
        Set-DevelopmentServerProperties -PropertiesPath (Join-Path $rootPath 'run\server.properties')
        $serverArguments = "/c `"$rootPath\gradlew.bat`" runServer"
        Start-Process -FilePath $env:ComSpec -ArgumentList $serverArguments -WorkingDirectory $rootPath
        # 首次启动需要解析 NeoForge 依赖，给服务端足够时间监听 25565。
        Start-Sleep -Seconds 20
        $clientArguments = "/c `"$rootPath\gradlew.bat`" runClientServer1"
        $client2Arguments = "/c `"$rootPath\gradlew.bat`" runClientServer2"
        Start-Process -FilePath $env:ComSpec -ArgumentList $clientArguments -WorkingDirectory $rootPath
        Start-Process -FilePath $env:ComSpec -ArgumentList $client2Arguments -WorkingDirectory $rootPath
        Write-Host "已启动离线服务端和两个自动连接的 NeoForge 开发客户端。"
    } else {
        # 启动单个 NeoForge 开发客户端。
        .\gradlew.bat $task
    }
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
