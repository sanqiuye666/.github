[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$NativeExecutable,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$JdkHome=$env:JAVA_HOME,
    [string]$Python='python'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=Split-Path -Parent $PSScriptRoot
$destination=[IO.Path]::GetFullPath($OutputDirectory)
$publicOutputRoot=[IO.Path]::GetFullPath((Join-Path $repo 'target/portable'))+[IO.Path]::DirectorySeparatorChar
if(-not $destination.StartsWith($publicOutputRoot,[StringComparison]::OrdinalIgnoreCase)){throw '输出必须位于项目target/portable的新子目录'}
if((Test-Path -LiteralPath $destination) -or (Test-Path -LiteralPath ($destination+'.zip'))){throw '输出目录或ZIP已存在，保留原件，请选择新的输出名称'}
$native=[IO.Path]::GetFullPath($NativeExecutable)
if(-not(Test-Path -LiteralPath $native -PathType Leaf)){throw '原生EXE缺失，请先构建Tauri正式产物'}
$player=Join-Path $repo 'player-app/target/player-app-0.1.0-SNAPSHOT.jar'
$admin=Join-Path $repo 'management-cli/target/dfs-admin.jar'
$bootstrap=Join-Path $repo 'bootstrap-agent/target/bootstrap-agent-0.1.0-SNAPSHOT.jar'
$jlink=Join-Path $JdkHome 'bin/jlink.exe'
if(-not(Test-Path -LiteralPath $jlink -PathType Leaf)){throw '需要带jlink的JDK21'}
if((Get-Content -LiteralPath (Join-Path $JdkHome 'release') -Raw) -notmatch 'JAVA_VERSION="21\.'){throw '仅接受JDK21作为随包运行时来源'}
Push-Location $repo
try {
    $identity=(& node packaging/build-identity.mjs --print).Trim()
    if($LASTEXITCODE -ne 0 -or $identity -notmatch '^[a-f0-9]{64}$'){throw '无法取得源码构建身份'}
    & $Python -X utf8 packaging/check-native-player.py --exe $native --expected $identity
    if($LASTEXITCODE -ne 0){throw '原生EXE身份检查失败'}
    & $Python -X utf8 packaging/check-artifact-identity.py --expected $identity --admin-jar $admin --player-jar $player --bootstrap-jar $bootstrap
    if($LASTEXITCODE -ne 0){throw 'Java或嵌入前端身份检查失败'}
    New-Item -ItemType Directory -Path $destination | Out-Null
    Copy-Item -LiteralPath $native -Destination (Join-Path $destination '三秋叶启动器.exe')
    Copy-Item -LiteralPath $player -Destination (Join-Path $destination 'player-sidecar.jar')
    Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $destination 'LICENSE')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/NATIVE-PLAYER.md') -Destination (Join-Path $destination '使用说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CUSTOM-INTRO-ACCOUNTS.md') -Destination (Join-Path $destination '自定义开屏与账号.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CLOUD-ACCOUNTS.md') -Destination (Join-Path $destination 'CLOUD-ACCOUNTS.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CUSTOM-INTRO-ACCOUNTS.md') -Destination (Join-Path $destination 'CUSTOM-INTRO-ACCOUNTS.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/SERVER-INTERFACES.md') -Destination (Join-Path $destination 'SERVER-INTERFACES.md')
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/cloud/player.env.example') -Destination (Join-Path $destination '账号配置.env.example')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/RESOURCE-MANAGEMENT.md') -Destination (Join-Path $destination '资源管理说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/AUTO-DISCOVERY-COTTAGE.md') -Destination (Join-Path $destination '自动目录与小屋说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/CREATIVE-TOOLS.md') -Destination (Join-Path $destination '创作工具说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/DIAGNOSTICS.md') -Destination (Join-Path $destination '诊断与外观说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/FABRIC.md') -Destination (Join-Path $destination 'Fabric加载器说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/INSTANCE-MODS.md') -Destination (Join-Path $destination '实例模组说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/LOADERS.md') -Destination (Join-Path $destination '更多核心说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/SERVER-MOCK.md') -Destination (Join-Path $destination '离线演练说明.md')
    Copy-Item -LiteralPath (Join-Path $repo 'docs/third-party') -Destination (Join-Path $destination 'third-party') -Recurse
    & $jlink --add-modules 'java.compiler,java.instrument,java.scripting,java.rmi,jdk.dynalink,java.desktop,java.logging,java.management,jdk.management,java.net.http,jdk.httpserver,jdk.crypto.ec,jdk.unsupported,java.naming,jdk.naming.dns,java.sql,jdk.zipfs' --strip-debug --no-man-pages --no-header-files --output (Join-Path $destination 'runtime')
    if($LASTEXITCODE -ne 0){throw '随包Java运行时生成失败，保留失败目录供检查'}
    & (Join-Path $destination 'runtime/bin/java.exe') --version
    if($LASTEXITCODE -ne 0){throw '随包Java实际启动失败'}
    foreach($module in @('java.compiler','java.instrument','java.scripting','java.rmi','jdk.dynalink','jdk.naming.dns')){
        & (Join-Path $destination 'runtime/bin/java.exe') --describe-module $module
        if($LASTEXITCODE -ne 0){throw ('随包Java缺少核心需要的模块，拒绝交付：'+$module)}
    }
    $hashes=[ordered]@{}
    foreach($file in Get-ChildItem -LiteralPath $destination -Recurse -File | Sort-Object FullName){
        $relative=$file.FullName.Substring($destination.Length+1).Replace('\','/')
        $hashes[$relative]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $metadata=[ordered]@{schemaVersion=1;product='三秋叶启动器';buildId=$identity;framework='Tauri';platform='windows-x64';kind='便携原生测试版';signed=$false;javaBundled=$true;updaterExternalized=$false;files=$hashes}
    [IO.File]::WriteAllText((Join-Path $destination 'native-build.json'),($metadata|ConvertTo-Json -Depth 5)+"`n",[Text.UTF8Encoding]::new($false))
    Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $destination -Force | Select-Object -ExpandProperty FullName) -DestinationPath ($destination+'.zip') -CompressionLevel Optimal
    Write-Output ('原生便携包：'+$destination)
    Write-Output ('构建身份：'+$identity)
    Write-Output ('ZIP SHA-256：'+(Get-FileHash -LiteralPath ($destination+'.zip')).Hash.ToLowerInvariant())
} finally {Pop-Location}
