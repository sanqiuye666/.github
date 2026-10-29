[CmdletBinding()]
param([switch]$NoOpen)
$ErrorActionPreference = 'Stop'
$demoRoot = $PSScriptRoot
$jar = Join-Path $demoRoot 'sanqiuye-demo.jar'
$dataRoot = Join-Path $demoRoot 'demo-data'
$stateFile = Join-Path $dataRoot 'demo-process.json'
$port = 15173
$url = "http://127.0.0.1:$port/"
New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
    $existing = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
    if ($existing -and $existing.StartTime.ToUniversalTime().ToString('o') -eq $state.startedAt) {
        $command = (Get-CimInstance Win32_Process -Filter "ProcessId=$($state.pid)").CommandLine
        if ($command -and $command.Contains($jar) -and $command.Contains($dataRoot)) {
            if (-not $NoOpen) { Start-Process $url }
            Write-Output $url
            exit 0
        }
        throw 'Demo 进程记录与实际程序不匹配，请勿手动覆盖记录。'
    }
}
$candidates = @()
if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin/java.exe' }
$installed = Get-Command java.exe -ErrorAction SilentlyContinue
if ($installed) { $candidates += $installed.Source }
$candidates += 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe'
$java = $null
foreach ($candidate in $candidates | Select-Object -Unique) {
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
    try {
        $version = (& $candidate --version 2>$null | Out-String)
        if ($LASTEXITCODE -eq 0 -and $version -match '(openjdk|java) 21\.') { $java = $candidate; break }
    } catch { continue }
}
if (-not $java) { throw '此本机 Demo 需要 Java 21，请安装后设置 JAVA_HOME。' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Demo JAR 缺失，请重新解压完整文件夹。' }
$stdout = Join-Path $dataRoot 'startup.out'
$stderr = Join-Path $dataRoot 'startup.err'
$arguments = @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-cp', ('"' + $jar + '"'), 'cn.dreamingfish.updater.player.launcher.LauncherWebMain', '--data-root', ('"' + $dataRoot + '"'), '--port', "$port")
$child = Start-Process -FilePath $java -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    $child.Refresh()
    if ($child.HasExited) { throw ('Demo 未启动，请查看 ' + $stderr + '。若端口被占用，请先关闭之前的 Demo。') }
    if ((Test-Path -LiteralPath $stdout) -and (Get-Content -Raw -LiteralPath $stdout)) {
        $info = (Get-Content -Raw -LiteralPath $stdout).Trim() | ConvertFrom-Json
        if ($info.url -eq $url -and $info.mode -eq 'LOCAL') {
            @{pid=$child.Id;startedAt=$child.StartTime.ToUniversalTime().ToString('o');url=$url} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $stateFile
            if (-not $NoOpen) { Start-Process $url }
            Write-Output $url
            exit 0
        }
    }
    Start-Sleep -Milliseconds 150
}
throw ('Demo 启动超时，请查看 ' + $stderr)
